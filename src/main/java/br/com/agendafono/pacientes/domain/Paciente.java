package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.TitularAnonimizadoException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.UUID;

/** Quem é atendido. Criado apenas por {@link Responsavel#cadastrarPaciente}, que exige consentimento. */
public final class Paciente {

    static final String NOME_ANONIMIZADO = "Paciente anonimizado";

    private final UUID id;
    private final UUID clinicaId;
    private final UUID responsavelId;
    private String nome;
    private LocalDate dataNascimento;
    private Demanda demanda;
    private boolean ativo;
    private Instant anonimizadoEm;
    private int versao;

    private Paciente(UUID id, UUID clinicaId, UUID responsavelId, String nome, LocalDate dataNascimento,
                     Demanda demanda, boolean ativo, Instant anonimizadoEm, int versao) {
        this.id = Objects.requireNonNull(id, "id");
        this.clinicaId = Objects.requireNonNull(clinicaId, "clinicaId");
        this.responsavelId = Objects.requireNonNull(responsavelId, "responsavelId");
        this.nome = nome;
        this.dataNascimento = dataNascimento;
        this.demanda = demanda;
        this.ativo = ativo;
        this.anonimizadoEm = anonimizadoEm;
        this.versao = versao;
    }

    static Paciente novo(UUID id, UUID clinicaId, UUID responsavelId, String nome, LocalDate dataNascimento,
                         Demanda demanda, LocalDate hoje) {
        Validacoes.nascimento(dataNascimento, hoje);
        return new Paciente(id, clinicaId, responsavelId, Validacoes.nomeObrigatorio(nome, "nome do paciente"),
                dataNascimento, demanda, true, null, 0);
    }

    public static Paciente reconstituir(UUID id, UUID clinicaId, UUID responsavelId, String nome,
                                        LocalDate dataNascimento, Demanda demanda, boolean ativo,
                                        Instant anonimizadoEm, int versao) {
        return new Paciente(id, clinicaId, responsavelId, nome, dataNascimento, demanda, ativo, anonimizadoEm,
                versao);
    }

    public void exigirVersao(Integer versaoEsperada) {
        if (versaoEsperada != null && versaoEsperada != versao) {
            throw new RegistroDesatualizadoException();
        }
    }

    public void atualizar(String novoNome, LocalDate novoNascimento, Demanda novaDemanda, LocalDate hoje) {
        exigirAtivo();
        Validacoes.nascimento(novoNascimento, hoje);
        nome = Validacoes.nomeObrigatorio(novoNome, "nome do paciente");
        dataNascimento = novoNascimento;
        demanda = novaDemanda;
    }

    public void inativar() {
        exigirAtivo();
        ativo = false;
    }

    public void reativar() {
        exigirAtivo();
        ativo = true;
    }

    public void anonimizar(Instant agora) {
        if (anonimizadoEm != null) {
            return;
        }
        nome = NOME_ANONIMIZADO;
        dataNascimento = null;
        demanda = null;
        ativo = false;
        anonimizadoEm = agora;
    }

    /** Idade em anos completos; nula sem data de nascimento. */
    public Integer idade(LocalDate hoje) {
        return dataNascimento == null ? null : Period.between(dataNascimento, hoje).getYears();
    }

    public boolean menorDeIdade(LocalDate hoje) {
        Integer idade = idade(hoje);
        return idade != null && idade < 18;
    }

    public void incrementarVersao() {
        versao++;
    }

    private void exigirAtivo() {
        if (anonimizadoEm != null) {
            throw new TitularAnonimizadoException();
        }
    }

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public UUID responsavelId() { return responsavelId; }
    public String nome() { return nome; }
    public LocalDate dataNascimento() { return dataNascimento; }
    public Demanda demanda() { return demanda; }
    public boolean ativo() { return ativo; }
    public Instant anonimizadoEm() { return anonimizadoEm; }
    public boolean anonimizado() { return anonimizadoEm != null; }
    public int versao() { return versao; }
}
