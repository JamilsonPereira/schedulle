package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.ConsentimentoAusenteException;
import br.com.agendafono.pacientes.DadosInvalidosException;
import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.Eventos.ConsentimentoRegistrado;
import br.com.agendafono.pacientes.Eventos.ConsentimentoRevogado;
import br.com.agendafono.pacientes.Eventos.PacienteCadastrado;
import br.com.agendafono.pacientes.Eventos.TitularAnonimizado;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TitularAnonimizadoException;
import br.com.agendafono.pacientes.domain.RegistroConsentimento.Acao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Responsável: quem conversa no WhatsApp e consente pelo paciente (ou o próprio paciente adulto).
 * Guarda a regra de que nenhum paciente é cadastrado sem consentimento vigente (LGPD arts. 11 e 14).
 */
public final class Responsavel {

    private final UUID id;
    private final UUID clinicaId;
    private String nome;
    private Telefone telefone;
    private Consentimento consentimento;
    private Instant anonimizadoEm;
    private int versao;

    private final List<RegistroConsentimento> registrosNovos = new ArrayList<>();
    private final List<Object> eventos = new ArrayList<>();

    private Responsavel(UUID id, UUID clinicaId, String nome, Telefone telefone, Consentimento consentimento,
                        Instant anonimizadoEm, int versao) {
        this.id = Objects.requireNonNull(id, "id");
        this.clinicaId = Objects.requireNonNull(clinicaId, "clinicaId");
        this.nome = nome;
        this.telefone = telefone;
        this.consentimento = consentimento;
        this.anonimizadoEm = anonimizadoEm;
        this.versao = versao;
    }

    public static Responsavel novo(UUID id, UUID clinicaId, Telefone telefone, String nome) {
        Objects.requireNonNull(telefone, "telefone");
        return new Responsavel(id, clinicaId, Validacoes.nomeOpcional(nome, "nome do responsável"), telefone,
                null, null, 0);
    }

    public static Responsavel reconstituir(UUID id, UUID clinicaId, String nome, Telefone telefone,
                                           Consentimento consentimento, Instant anonimizadoEm, int versao) {
        return new Responsavel(id, clinicaId, nome, telefone, consentimento, anonimizadoEm, versao);
    }

    // ------------------------------------------------------------------ comportamento

    public void exigirVersao(Integer versaoEsperada) {
        if (versaoEsperada != null && versaoEsperada != versao) {
            throw new RegistroDesatualizadoException();
        }
    }

    public void registrarConsentimento(String versaoTexto, Canal canal, String evidencia, Instant agora) {
        exigirAtivo();
        if (versaoTexto == null || versaoTexto.isBlank()) {
            throw new DadosInvalidosException("Informe a versão do texto de consentimento");
        }
        if (evidencia == null || evidencia.isBlank()) {
            throw new DadosInvalidosException("Informe a evidência do consentimento");
        }
        consentimento = new Consentimento(versaoTexto.trim(), canal, evidencia.trim(), agora);
        registrosNovos.add(new RegistroConsentimento(Acao.CONCEDIDO, consentimento.versaoTexto(), canal,
                consentimento.evidencia(), agora));
        eventos.add(new ConsentimentoRegistrado(clinicaId, id, consentimento.versaoTexto(), canal));
    }

    /** Revogação (LGPD art. 8º, §5º). Pacientes já cadastrados continuam, mas nenhum novo entra. */
    public void revogarConsentimento(Canal canal, String evidencia, Instant agora) {
        exigirAtivo();
        if (consentimento == null) {
            throw new ConsentimentoAusenteException();
        }
        registrosNovos.add(new RegistroConsentimento(Acao.REVOGADO, consentimento.versaoTexto(), canal,
                evidencia == null || evidencia.isBlank() ? canal.name().toLowerCase() : evidencia.trim(), agora));
        consentimento = null;
        eventos.add(new ConsentimentoRevogado(clinicaId, id, canal));
    }

    public void atualizar(String novoNome, Telefone novoTelefone) {
        exigirAtivo();
        this.nome = Validacoes.nomeOpcional(novoNome, "nome do responsável");
        this.telefone = Objects.requireNonNull(novoTelefone, "telefone");
    }

    /** Preenche o nome só se ainda não houver um (usado quando o bot ou o painel descobre o nome depois). */
    public void completarNome(String possivelNome) {
        if (nome == null && possivelNome != null && !possivelNome.isBlank()) {
            nome = Validacoes.nomeOpcional(possivelNome, "nome do responsável");
        }
    }

    public Paciente cadastrarPaciente(UUID pacienteId, String nomePaciente, LocalDate dataNascimento,
                                      Demanda demanda, LocalDate hoje) {
        exigirAtivo();
        if (consentimento == null) {
            throw new ConsentimentoAusenteException();
        }
        Paciente paciente = Paciente.novo(pacienteId, clinicaId, id, nomePaciente, dataNascimento, demanda, hoje);
        eventos.add(new PacienteCadastrado(clinicaId, pacienteId, id));
        return paciente;
    }

    /** Apaga os dados pessoais do responsável. Os pacientes são anonimizados à parte pelo caso de uso. */
    public void anonimizar(Instant agora) {
        exigirAtivo();
        String telefoneAnterior = telefone != null ? telefone.somenteDigitos() : null;
        nome = null;
        telefone = null;
        consentimento = null;
        anonimizadoEm = agora;
        eventos.add(new TitularAnonimizado(clinicaId, id, telefoneAnterior));
    }

    public void incrementarVersao() {
        versao++;
    }

    public List<RegistroConsentimento> extrairRegistrosNovos() {
        List<RegistroConsentimento> copia = List.copyOf(registrosNovos);
        registrosNovos.clear();
        return copia;
    }

    public List<Object> extrairEventos() {
        List<Object> copia = List.copyOf(eventos);
        eventos.clear();
        return copia;
    }

    private void exigirAtivo() {
        if (anonimizadoEm != null) {
            throw new TitularAnonimizadoException();
        }
    }

    // ------------------------------------------------------------------ leitura

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public String nome() { return nome; }
    public Telefone telefone() { return telefone; }
    public Consentimento consentimento() { return consentimento; }
    public boolean possuiConsentimento() { return consentimento != null; }
    public Instant anonimizadoEm() { return anonimizadoEm; }
    public boolean anonimizado() { return anonimizadoEm != null; }
    public int versao() { return versao; }
}
