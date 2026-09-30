package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.OperacaoNaoPermitidaException;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.Views.UsuarioView;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.application.port.RefreshTokenRepository;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.clinica.domain.Senhas;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.Papel;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gestão de usuários da clínica pelo ADMIN. Senhas temporárias aparecem uma única vez, na resposta. */
@Service
public class UsuariosService {

    private final UsuarioRepository usuarios;
    private final ProfissionalRepository profissionais;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final Relogio relogio;

    public UsuariosService(UsuarioRepository usuarios, ProfissionalRepository profissionais,
                           RefreshTokenRepository refreshTokens, PasswordEncoder senhas, Auditoria auditoria,
                           Relogio relogio) {
        this.usuarios = usuarios;
        this.profissionais = profissionais;
        this.refreshTokens = refreshTokens;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record UsuarioComSenhaTemporaria(UsuarioView usuario, String senhaTemporaria) {
    }

    @Transactional(readOnly = true)
    public List<UsuarioView> listar(UUID clinicaId) {
        Instant agora = relogio.agora();
        return usuarios.listar(clinicaId).stream().map(u -> view(u, agora)).toList();
    }

    @Transactional
    public UsuarioComSenhaTemporaria criar(UsuarioAutenticado ator, String nome, String email, Set<Papel> papeis) {
        String temporaria = Senhas.temporaria();
        Usuario usuario = Usuario.novo(UUID.randomUUID(), ator.clinicaId(), nome, email, papeis,
                senhas.encode(temporaria));
        usuarios.inserir(usuario);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "USUARIO_CRIADO", "usuario", usuario.id());
        return new UsuarioComSenhaTemporaria(view(usuario, relogio.agora()), temporaria);
    }

    @Transactional
    public UsuarioView atualizar(UsuarioAutenticado ator, UUID usuarioId, String nome, Set<Papel> papeis,
                                 boolean ativo, Integer versaoEsperada) {
        Usuario usuario = carregar(ator.clinicaId(), usuarioId);
        usuario.exigirVersao(versaoEsperada);
        if (usuario.id().equals(ator.usuarioId()) && (!ativo || papeis == null || !papeis.contains(Papel.ADMIN))) {
            throw new OperacaoNaoPermitidaException("Você não pode remover o próprio acesso de administrador");
        }
        usuario.atualizar(nome, papeis, ativo);
        usuarios.atualizar(usuario);
        if (usuarios.listar(ator.clinicaId()).stream().noneMatch(Usuario::administradorAtivo)) {
            throw new OperacaoNaoPermitidaException("A clínica precisa de ao menos um administrador ativo");
        }
        if (!ativo) {
            refreshTokens.revogarDoUsuario(usuario.id(), relogio.agora());
        }
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "USUARIO_ALTERADO", "usuario", usuario.id());
        return view(usuario, relogio.agora());
    }

    /** Gera nova senha temporária (ex.: usuário esqueceu) e derruba as sessões abertas dele. */
    @Transactional
    public UsuarioComSenhaTemporaria redefinirSenha(UsuarioAutenticado ator, UUID usuarioId) {
        Usuario usuario = carregar(ator.clinicaId(), usuarioId);
        String temporaria = Senhas.temporaria();
        usuario.definirSenhaTemporaria(senhas.encode(temporaria));
        usuarios.atualizar(usuario);
        refreshTokens.revogarDoUsuario(usuario.id(), relogio.agora());
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "SENHA_REDEFINIDA", "usuario", usuario.id());
        return new UsuarioComSenhaTemporaria(view(usuario, relogio.agora()), temporaria);
    }

    private Usuario carregar(UUID clinicaId, UUID usuarioId) {
        return usuarios.buscar(clinicaId, usuarioId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Usuário", usuarioId));
    }

    private UsuarioView view(Usuario u, Instant agora) {
        UUID profissionalId = profissionais.doUsuario(u.clinicaId(), u.id()).map(Profissional::id).orElse(null);
        return ClinicaMapper.view(u, profissionalId, agora);
    }
}
