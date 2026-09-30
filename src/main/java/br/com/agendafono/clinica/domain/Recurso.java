package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.TipoRecurso;

import java.util.Objects;
import java.util.UUID;

/** Sala ou cabine de audiometria. */
public final class Recurso {

    private final UUID id;
    private final UUID clinicaId;
    private String nome;
    private TipoRecurso tipo;
    private boolean ativo;
    private int versao;

    private Recurso(UUID id, UUID clinicaId, String nome, TipoRecurso tipo, boolean ativo, int versao) {
        this.id = Objects.requireNonNull(id);
        this.clinicaId = Objects.requireNonNull(clinicaId);
        this.nome = nome;
        this.tipo = tipo;
        this.ativo = ativo;
        this.versao = versao;
    }

    public static Recurso novo(UUID id, UUID clinicaId, String nome, TipoRecurso tipo) {
        return new Recurso(id, clinicaId, Textos.obrigatorio(nome, "o nome da sala", 1, 60),
                Objects.requireNonNull(tipo, "tipo"), true, 0);
    }

    public static Recurso reconstituir(UUID id, UUID clinicaId, String nome, TipoRecurso tipo, boolean ativo,
                                       int versao) {
        return new Recurso(id, clinicaId, nome, tipo, ativo, versao);
    }

    public void exigirVersao(Integer esperada) {
        if (esperada != null && esperada != versao) {
            throw new ConflitoDeVersaoException();
        }
    }

    public void atualizar(String novoNome, TipoRecurso novoTipo, boolean novoAtivo) {
        nome = Textos.obrigatorio(novoNome, "o nome da sala", 1, 60);
        tipo = Objects.requireNonNull(novoTipo, "tipo");
        ativo = novoAtivo;
    }

    public void incrementarVersao() {
        versao++;
    }

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public String nome() { return nome; }
    public TipoRecurso tipo() { return tipo; }
    public boolean ativo() { return ativo; }
    public int versao() { return versao; }
}
