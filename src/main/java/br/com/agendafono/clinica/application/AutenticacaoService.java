package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.CredenciaisInvalidasException;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.SenhaFracaException;
import br.com.agendafono.clinica.SessaoExpiradaException;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.UsuarioView;
import br.com.agendafono.clinica.application.port.ClinicaRepository;
import br.com.agendafono.clinica.application.port.EmissorDeTokens;
import br.com.agendafono.clinica.application.port.EmissorDeTokens.TokenDeAcesso;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.application.port.RefreshTokenRepository;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.clinica.domain.RefreshToken;
import br.com.agendafono.clinica.domain.Senhas;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.SegurancaProperties;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Login, renovação e encerramento de sessão (SDD, seção 10).
 *
 * <p>Falhas de login e reuso de refresh token são gravados em transações próprias, antes de lançar o erro:
 * se fossem na mesma transação do erro, o rollback apagaria o contador de tentativas e a revogação.
 */
@Service
public class AutenticacaoService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final ProfissionalRepository profissionais;
    private final ClinicaRepository clinicas;
    private final RefreshTokenRepository refreshTokens;
    private final EmissorDeTokens emissor;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final TransactionTemplate transacao;
    private final Relogio relogio;
    private final SegurancaProperties propriedades;
    /** Hash usado quando o e-mail não existe, para o tempo de resposta não revelar quem tem conta. */
    private final String hashFicticio;

    public AutenticacaoService(UsuarioRepository usuarios, ProfissionalRepository profissionais,
                               ClinicaRepository clinicas, RefreshTokenRepository refreshTokens,
                               EmissorDeTokens emissor, PasswordEncoder senhas, Auditoria auditoria,
                               TransactionTemplate transacao, Relogio relogio, SegurancaProperties propriedades) {
        this.usuarios = usuarios;
        this.profissionais = profissionais;
        this.clinicas = clinicas;
        this.refreshTokens = refreshTokens;
        this.emissor = emissor;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.transacao = transacao;
        this.relogio = relogio;
        this.propriedades = propriedades;
        this.hashFicticio = senhas.encode("senha-ficticia-" + UUID.randomUUID());
    }

    /** Tokens de uma sessão. O refresh token só aparece aqui, uma vez; o banco guarda apenas o hash. */
    public record SessaoDeLogin(String tokenAcesso, Instant acessoExpiraEm, String refreshToken,
                                Instant refreshExpiraEm, UsuarioView usuario) {
    }

    public record Perfil(UsuarioView usuario, ClinicaView clinica) {
    }

    public SessaoDeLogin login(String email, String senha) {
        Instant agora = relogio.agora();
        Optional<Usuario> encontrado;
        try {
            encontrado = usuarios.porEmail(Usuario.normalizarEmail(email));
        } catch (ValidacaoException e) {
            encontrado = Optional.empty();
        }
        String senhaInformada = senha == null ? "" : senha;
        if (encontrado.isEmpty()) {
            senhas.matches(senhaInformada, hashFicticio);
            throw new CredenciaisInvalidasException();
        }
        Usuario usuario = encontrado.get();
        if (!usuario.podeEntrar(agora)) {
            senhas.matches(senhaInformada, hashFicticio);
            transacao.executeWithoutResult(s -> auditoria.registrar(usuario.clinicaId(), usuario.id(),
                    "LOGIN_RECUSADO_BLOQUEIO", "usuario", usuario.id()));
            throw new CredenciaisInvalidasException();
        }
        if (!senhas.matches(senhaInformada, usuario.senhaHash())) {
            transacao.executeWithoutResult(s -> {
                usuario.registrarFalhaDeLogin(agora, propriedades.tentativasAntesDoBloqueio(),
                        propriedades.tempoBloqueio());
                usuarios.atualizarEstadoDeLogin(usuario);
                auditoria.registrar(usuario.clinicaId(), usuario.id(), "LOGIN_FALHOU", "usuario", usuario.id());
            });
            throw new CredenciaisInvalidasException();
        }
        return transacao.execute(s -> {
            usuario.registrarLogin(agora);
            usuarios.atualizarEstadoDeLogin(usuario);
            auditoria.registrar(usuario.clinicaId(), usuario.id(), "LOGIN", "usuario", usuario.id());
            return abrirSessao(usuario, UUID.randomUUID(), agora);
        });
    }

    public SessaoDeLogin renovar(String refreshToken) {
        Instant agora = relogio.agora();
        String hash = hash(refreshToken);
        RefreshToken atual = transacao.execute(s -> refreshTokens.porHash(hash).orElse(null));
        if (atual == null) {
            throw new SessaoExpiradaException();
        }
        if (atual.reutilizado()) {
            // Token já trocado sendo usado de novo: provável roubo. Derruba todas as sessões dessa família.
            transacao.executeWithoutResult(s -> {
                refreshTokens.revogarFamilia(atual.familia(), agora);
                auditoria.registrar(atual.clinicaId(), atual.usuarioId(), "REFRESH_REUTILIZADO", "usuario",
                        atual.usuarioId());
            });
            throw new SessaoExpiradaException();
        }
        if (!atual.valido(agora)) {
            throw new SessaoExpiradaException();
        }
        return transacao.execute(s -> {
            if (!refreshTokens.marcarUsado(atual.id(), agora)) {
                throw new SessaoExpiradaException();
            }
            Usuario usuario = usuarios.buscar(atual.clinicaId(), atual.usuarioId())
                    .filter(u -> u.podeEntrar(agora))
                    .orElseThrow(SessaoExpiradaException::new);
            return abrirSessao(usuario, atual.familia(), agora);
        });
    }

    /** Encerra a sessão do refresh token (ou todas do usuário). Token desconhecido não é erro. */
    @Transactional
    public void encerrar(String refreshToken, boolean todasAsSessoes) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant agora = relogio.agora();
        refreshTokens.porHash(hash(refreshToken)).ifPresent(rt -> {
            if (todasAsSessoes) {
                refreshTokens.revogarDoUsuario(rt.usuarioId(), agora);
            } else {
                refreshTokens.revogarFamilia(rt.familia(), agora);
            }
            auditoria.registrar(rt.clinicaId(), rt.usuarioId(), "LOGOUT", "usuario", rt.usuarioId());
        });
    }

    @Transactional(readOnly = true)
    public Perfil perfil(UsuarioAutenticado atual) {
        Usuario usuario = usuarios.buscar(atual.clinicaId(), atual.usuarioId())
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", atual.usuarioId()));
        ClinicaView clinica = clinicas.buscar(atual.clinicaId()).map(ClinicaMapper::view)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Clínica", atual.clinicaId()));
        return new Perfil(ClinicaMapper.view(usuario, atual.profissionalId(), relogio.agora()), clinica);
    }

    /** Troca a própria senha. Derruba as outras sessões e devolve uma sessão nova. */
    public SessaoDeLogin trocarSenha(UsuarioAutenticado atual, String senhaAtual, String novaSenha) {
        Instant agora = relogio.agora();
        return transacao.execute(s -> {
            Usuario usuario = usuarios.buscar(atual.clinicaId(), atual.usuarioId())
                    .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", atual.usuarioId()));
            if (senhaAtual == null || !senhas.matches(senhaAtual, usuario.senhaHash())) {
                throw new CredenciaisInvalidasException();
            }
            if (senhaAtual.equals(novaSenha)) {
                throw new SenhaFracaException("A nova senha deve ser diferente da atual");
            }
            Senhas.validar(novaSenha, usuario.email());
            usuario.trocarSenha(senhas.encode(novaSenha));
            usuarios.atualizar(usuario);
            refreshTokens.revogarDoUsuario(usuario.id(), agora);
            auditoria.registrar(usuario.clinicaId(), usuario.id(), "SENHA_TROCADA", "usuario", usuario.id());
            return abrirSessao(usuario, UUID.randomUUID(), agora);
        });
    }

    // ------------------------------------------------------------------ auxiliares

    private SessaoDeLogin abrirSessao(Usuario usuario, UUID familia, Instant agora) {
        UUID profissionalId = profissionais.doUsuario(usuario.clinicaId(), usuario.id())
                .map(Profissional::id).orElse(null);
        TokenDeAcesso acesso = emissor.emitir(usuario, profissionalId, agora);

        byte[] aleatorio = new byte[32];
        ALEATORIO.nextBytes(aleatorio);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        Instant expira = agora.plus(propriedades.validadeRefresh());
        refreshTokens.inserir(new RefreshToken(UUID.randomUUID(), usuario.clinicaId(), usuario.id(), familia,
                hash(refresh), expira, null, null));

        return new SessaoDeLogin(acesso.valor(), acesso.expiraEm(), refresh, expira,
                ClinicaMapper.view(usuario, profissionalId, agora));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
