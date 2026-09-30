package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.GradeSobrepostaException;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.ValidacaoException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Agregado Profissional: dados, subáreas e grade semanal sem sobreposição (SDD, seção 4). */
public final class Profissional {

    private final UUID id;
    private final UUID clinicaId;
    private UUID usuarioId;
    private String nome;
    private String registroCrfa;
    private Set<Subarea> subareas;
    private int duracaoPadraoMin;
    private boolean ativo;
    private List<IntervaloGrade> grade;
    private int versao;

    private Profissional(UUID id, UUID clinicaId, UUID usuarioId, String nome, String registroCrfa,
                         Set<Subarea> subareas, int duracaoPadraoMin, boolean ativo, List<IntervaloGrade> grade,
                         int versao) {
        this.id = Objects.requireNonNull(id);
        this.clinicaId = Objects.requireNonNull(clinicaId);
        this.usuarioId = usuarioId;
        this.nome = nome;
        this.registroCrfa = registroCrfa;
        this.subareas = subareas;
        this.duracaoPadraoMin = duracaoPadraoMin;
        this.ativo = ativo;
        this.grade = grade;
        this.versao = versao;
    }

    public static Profissional novo(UUID id, UUID clinicaId, String nome, String registroCrfa,
                                    Set<Subarea> subareas, Integer duracaoPadraoMin, UUID usuarioId) {
        Profissional p = new Profissional(id, clinicaId, null, null, null, EnumSet.noneOf(Subarea.class), 40, true,
                List.of(), 0);
        p.atualizar(nome, registroCrfa, subareas, duracaoPadraoMin, usuarioId);
        return p;
    }

    public static Profissional reconstituir(UUID id, UUID clinicaId, UUID usuarioId, String nome,
                                            String registroCrfa, Set<Subarea> subareas, int duracaoPadraoMin,
                                            boolean ativo, List<IntervaloGrade> grade, int versao) {
        return new Profissional(id, clinicaId, usuarioId, nome, registroCrfa,
                subareas.isEmpty() ? EnumSet.noneOf(Subarea.class) : EnumSet.copyOf(subareas), duracaoPadraoMin,
                ativo, List.copyOf(grade), versao);
    }

    public void exigirVersao(Integer esperada) {
        if (esperada != null && esperada != versao) {
            throw new ConflitoDeVersaoException();
        }
    }

    public void atualizar(String novoNome, String novoRegistro, Set<Subarea> novasSubareas,
                          Integer novaDuracaoMin, UUID novoUsuarioId) {
        int duracao = novaDuracaoMin == null ? 40 : novaDuracaoMin;
        if (duracao < 10 || duracao > 240) {
            throw new ValidacaoException("A duração padrão deve estar entre 10 e 240 minutos");
        }
        nome = Textos.obrigatorio(novoNome, "o nome do profissional", 2, 120);
        registroCrfa = Textos.opcional(novoRegistro, "O registro no conselho", 3, 30);
        subareas = novasSubareas == null || novasSubareas.isEmpty()
                ? EnumSet.noneOf(Subarea.class) : EnumSet.copyOf(novasSubareas);
        duracaoPadraoMin = duracao;
        usuarioId = novoUsuarioId;
    }

    /** Substitui a grade inteira. Intervalos do mesmo dia não podem se sobrepor. */
    public void definirGrade(List<IntervaloGrade> novaGrade) {
        List<IntervaloGrade> ordenada = new ArrayList<>(novaGrade);
        ordenada.sort(Comparator.comparing(IntervaloGrade::dia).thenComparing(IntervaloGrade::inicio));
        for (int i = 0; i < ordenada.size(); i++) {
            for (int j = i + 1; j < ordenada.size(); j++) {
                if (ordenada.get(i).sobrepoe(ordenada.get(j))) {
                    throw new GradeSobrepostaException();
                }
            }
        }
        grade = List.copyOf(ordenada);
    }

    public void inativar() {
        ativo = false;
    }

    public void reativar() {
        ativo = true;
    }

    public void incrementarVersao() {
        versao++;
    }

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public UUID usuarioId() { return usuarioId; }
    public String nome() { return nome; }
    public String registroCrfa() { return registroCrfa; }
    public Set<Subarea> subareas() { return subareas.isEmpty() ? EnumSet.noneOf(Subarea.class) : EnumSet.copyOf(subareas); }
    public int duracaoPadraoMin() { return duracaoPadraoMin; }
    public boolean ativo() { return ativo; }
    public List<IntervaloGrade> grade() { return grade; }
    public int versao() { return versao; }
}
