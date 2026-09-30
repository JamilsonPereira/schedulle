package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.compartilhado.seguranca.Papel;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Usuário do painel. A senha nunca passa por aqui em texto: só o hash. */
public final class Usuario {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private final UUID clinicaId;
    private String nome;
    private final String email;
    private String senhaHash;
    private Set<Papel> papeis;
    private boolean ativo;
    private int tentativasFalhas;
    private Instant bloqueadoAte;
    private boolean precisaTrocarSenha;
    private Instant ultimoLoginEm;
    private int versao;

    private Usuario(UUID id, UUID clinicaId, String nome, String email, String senhaHash, Set<Papel> papeis,
                    boolean ativo, int tentativasFalhas, Instant bloqueadoAte, boolean precisaTrocarSenha,
                    Instant ultimoLoginEm, int versao) {
        this.id = Objects.requireNonNull(id);
        this.clinicaId = Objects.requireNonNull(clinicaId);
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.papeis = papeis;
        this.ativo = ativo;
        this.tentativasFalhas = tentativasFalhas;
        this.bloqueadoAte = bloqueadoAte;
        this.precisaTrocarSenha = precisaTrocarSenha;
        this.ultimoLoginEm = ultimoLoginEm;
        this.versao = versao;
    }

    /** Novo usuário com senha temporária: deve trocá-la no primeiro acesso. */
    public static Usuario novo(UUID id, UUID clinicaId, String nome, String email, Set<Papel> papeis,
                               String hashSenhaTemporaria) {
        return new Usuario(id, clinicaId, Textos.obrigatorio(nome, "o nome", 2, 120), normalizarEmail(email),
                Objects.requireNonNull(hashSenhaTemporaria), exigirPapeis(papeis), true, 0, null, true, null, 0);
    }

    public static Usuario reconstituir(UUID id, UUID clinicaId, String nome, String email, String senhaHash,
                                       Set<Papel> papeis, boolean ativo, int tentativasFalhas, Instant bloqueadoAte,
                                       boolean precisaTrocarSenha, Instant ultimoLoginEm, int versao) {
        return new Usuario(id, clinicaId, nome, email, senhaHash, papeis.isEmpty() ? EnumSet.noneOf(Papel.class)
                : EnumSet.copyOf(papeis), ativo, tentativasFalhas, bloqueadoAte, precisaTrocarSenha, ultimoLoginEm,
                versao);
    }

    public static String normalizarEmail(String email) {
        if (email == null || !EMAIL.matcher(email.trim()).matches() || email.trim().length() > 254) {
            throw new ValidacaoException("E-mail inválido");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Set<Papel> exigirPapeis(Set<Papel> papeis) {
        if (papeis == null || papeis.isEmpty()) {
            throw new ValidacaoException("Informe ao menos um perfil");
        }
        return EnumSet.copyOf(papeis);
    }

    public void exigirVersao(Integer esperada) {
        if (esperada != null && esperada != versao) {
            throw new ConflitoDeVersaoException();
        }
    }

    public boolean podeEntrar(Instant agora) {
        return ativo && (bloqueadoAte == null || !bloqueadoAte.isAfter(agora));
    }

    /** Após {@code limite} falhas seguidas, bloqueia por {@code tempo}. */
    public void registrarFalhaDeLogin(Instant agora, int limite, Duration tempo) {
        tentativasFalhas++;
        if (tentativasFalhas >= limite) {
            bloqueadoAte = agora.plus(tempo);
            tentativasFalhas = 0;
        }
    }

    public void registrarLogin(Instant agora) {
        tentativasFalhas = 0;
        bloqueadoAte = null;
        ultimoLoginEm = agora;
    }

    public void trocarSenha(String novoHash) {
        senhaHash = Objects.requireNonNull(novoHash);
        precisaTrocarSenha = false;
    }

    public void definirSenhaTemporaria(String hashTemporario) {
        senhaHash = Objects.requireNonNull(hashTemporario);
        precisaTrocarSenha = true;
        tentativasFalhas = 0;
        bloqueadoAte = null;
    }

    public void atualizar(String novoNome, Set<Papel> novosPapeis, boolean novoAtivo) {
        nome = Textos.obrigatorio(novoNome, "o nome", 2, 120);
        papeis = exigirPapeis(novosPapeis);
        ativo = novoAtivo;
    }

    public boolean administradorAtivo() {
        return ativo && papeis.contains(Papel.ADMIN);
    }

    public void incrementarVersao() {
        versao++;
    }

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public String nome() { return nome; }
    public String email() { return email; }
    public String senhaHash() { return senhaHash; }
    public Set<Papel> papeis() { return EnumSet.copyOf(papeis.isEmpty() ? EnumSet.noneOf(Papel.class) : papeis); }
    public boolean ativo() { return ativo; }
    public int tentativasFalhas() { return tentativasFalhas; }
    public Instant bloqueadoAte() { return bloqueadoAte; }
    public boolean precisaTrocarSenha() { return precisaTrocarSenha; }
    public Instant ultimoLoginEm() { return ultimoLoginEm; }
    public int versao() { return versao; }
}
