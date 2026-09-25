package br.com.agendafono.agenda.domain;

import br.com.agendafono.agenda.Eventos.FaltaAvisada;
import br.com.agendafono.agenda.Eventos.PresencaRegistrada;
import br.com.agendafono.agenda.Eventos.SessaoAgendada;
import br.com.agendafono.agenda.Eventos.SessaoCancelada;
import br.com.agendafono.agenda.Eventos.SessaoConfirmada;
import br.com.agendafono.agenda.Eventos.SessaoRemarcada;
import br.com.agendafono.agenda.Eventos.VagaLiberada;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.ReservaExpiradaException;
import br.com.agendafono.agenda.StatusSessao;
import br.com.agendafono.agenda.TipoSessao;
import br.com.agendafono.agenda.TransicaoInvalidaException;
import br.com.agendafono.agenda.VersaoDesatualizadaException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado Sessão (SDD, seção 4). Garante as transições de status válidas; a ausência de
 * sobreposição com outras sessões é garantida pela constraint {@code EXCLUDE} no banco.
 *
 * <p>Toda mudança registra eventos de domínio, recolhidos por {@link #extrairEventos()} e
 * publicados pela camada de aplicação na mesma transação.
 */
public final class Sessao {

    private final UUID id;
    private final UUID clinicaId;
    private final UUID pacienteId;
    private final UUID profissionalId;
    private final UUID serieId;
    private final TipoSessao tipo;
    private UUID recursoId;
    private Periodo periodo;
    private StatusSessao status;
    private Instant expiraEm;
    private int versao;

    private final List<Object> eventos = new ArrayList<>();

    private Sessao(UUID id, UUID clinicaId, UUID pacienteId, UUID profissionalId, UUID recursoId, UUID serieId,
                   TipoSessao tipo, Periodo periodo, StatusSessao status, Instant expiraEm, int versao) {
        this.id = Objects.requireNonNull(id, "id");
        this.clinicaId = Objects.requireNonNull(clinicaId, "clinicaId");
        this.pacienteId = Objects.requireNonNull(pacienteId, "pacienteId");
        this.profissionalId = Objects.requireNonNull(profissionalId, "profissionalId");
        this.recursoId = recursoId;
        this.serieId = serieId;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.periodo = Objects.requireNonNull(periodo, "periodo");
        this.status = Objects.requireNonNull(status, "status");
        this.expiraEm = expiraEm;
        this.versao = versao;
    }

    // ------------------------------------------------------------------ criação

    /** Pré-reserva do bot, válida até {@code agora + ttl} (RN-04). */
    public static Sessao reservar(UUID id, UUID clinicaId, UUID pacienteId, UUID profissionalId, UUID recursoId,
                                  TipoSessao tipo, Periodo periodo, Instant agora, Duration ttl) {
        exigirFuturo(periodo, agora);
        return new Sessao(id, clinicaId, pacienteId, profissionalId, recursoId, null, tipo, periodo,
                StatusSessao.RESERVADA, agora.plus(ttl), 0);
    }

    /** Agendamento direto (painel ou série). */
    public static Sessao agendar(UUID id, UUID clinicaId, UUID pacienteId, UUID profissionalId, UUID recursoId,
                                 UUID serieId, TipoSessao tipo, Periodo periodo, Instant agora) {
        exigirFuturo(periodo, agora);
        Sessao s = new Sessao(id, clinicaId, pacienteId, profissionalId, recursoId, serieId, tipo, periodo,
                StatusSessao.AGENDADA, null, 0);
        s.registrarAgendada();
        return s;
    }

    /** Recria a sessão a partir do banco, sem validar nem gerar eventos. */
    public static Sessao reconstituir(UUID id, UUID clinicaId, UUID pacienteId, UUID profissionalId, UUID recursoId,
                                      UUID serieId, TipoSessao tipo, Periodo periodo, StatusSessao status,
                                      Instant expiraEm, int versao) {
        return new Sessao(id, clinicaId, pacienteId, profissionalId, recursoId, serieId, tipo, periodo, status,
                expiraEm, versao);
    }

    // ------------------------------------------------------------------ comportamento

    public void exigirVersao(Integer versaoEsperada) {
        if (versaoEsperada != null && versaoEsperada != versao) {
            throw new VersaoDesatualizadaException();
        }
    }

    /** RESERVADA → AGENDADA, se a reserva ainda vale. Repetir numa sessão já AGENDADA não faz nada. */
    public void confirmarReserva(Instant agora) {
        if (status == StatusSessao.AGENDADA && expiraEm == null) {
            return; // confirmação repetida pelo bot
        }
        exigirTransicao(StatusSessao.AGENDADA, "confirmar a reserva de");
        if (!expiraEm.isAfter(agora)) {
            throw new ReservaExpiradaException();
        }
        status = StatusSessao.AGENDADA;
        expiraEm = null;
        registrarAgendada();
    }

    /** "Confirmo" no lembrete: AGENDADA → CONFIRMADA. Repetir em CONFIRMADA não faz nada. */
    public void confirmarPresenca() {
        if (status == StatusSessao.CONFIRMADA) {
            return;
        }
        exigirTransicao(StatusSessao.CONFIRMADA, "confirmar a presença em");
        status = StatusSessao.CONFIRMADA;
        eventos.add(new SessaoConfirmada(id, clinicaId));
    }

    public void cancelar(Instant agora) {
        StatusSessao anterior = status;
        exigirTransicao(StatusSessao.CANCELADA, "cancelar");
        status = StatusSessao.CANCELADA;
        eventos.add(new SessaoCancelada(id, clinicaId, profissionalId, periodo));
        if (anterior != StatusSessao.RESERVADA && periodo.inicio().isAfter(agora)) {
            eventos.add(new VagaLiberada(clinicaId, profissionalId, recursoId, periodo, id));
        }
    }

    /**
     * "Vou faltar" (RN-20). Só antes do início da sessão. Com antecedência maior ou igual à da política,
     * o paciente ganha direito a reposição; o horário liberado vira oferta de vaga.
     *
     * @return se o aviso deu direito a reposição
     */
    public boolean avisarFalta(Instant agora, Duration antecedenciaMinimaParaReposicao) {
        exigirTransicao(StatusSessao.FALTA_AVISADA, "avisar falta em");
        if (!periodo.inicio().isAfter(agora)) {
            throw new TransicaoInvalidaException("A sessão já começou; registre a presença ou a falta");
        }
        boolean direito = Duration.between(agora, periodo.inicio()).compareTo(antecedenciaMinimaParaReposicao) >= 0;
        status = StatusSessao.FALTA_AVISADA;
        eventos.add(new FaltaAvisada(id, clinicaId, pacienteId, profissionalId, periodo, direito));
        eventos.add(new VagaLiberada(clinicaId, profissionalId, recursoId, periodo, id));
        return direito;
    }

    /** ATENDIDA ou FALTA_SEM_AVISO, permitido só a partir do início da sessão. */
    public void registrarPresenca(StatusSessao resultado, Instant agora) {
        if (resultado != StatusSessao.ATENDIDA && resultado != StatusSessao.FALTA_SEM_AVISO) {
            throw new IllegalArgumentException("Resultado de presença inválido: " + resultado);
        }
        exigirTransicao(resultado, "registrar presença em");
        if (agora.isBefore(periodo.inicio())) {
            throw new TransicaoInvalidaException("A presença só pode ser registrada a partir do início da sessão");
        }
        status = resultado;
        eventos.add(new PresencaRegistrada(id, clinicaId, resultado));
    }

    /** Move a sessão para outro horário (mesma duração). Volta a AGENDADA e libera o horário antigo. */
    public void remarcar(Instant novoInicio, UUID novoRecursoId, Instant agora) {
        if (status != StatusSessao.AGENDADA && status != StatusSessao.CONFIRMADA) {
            throw new TransicaoInvalidaException(status, "remarcar");
        }
        Periodo novo = Periodo.de(novoInicio, periodo.duracao());
        exigirFuturo(novo, agora);
        Periodo anterior = periodo;
        UUID recursoAnterior = recursoId;
        periodo = novo;
        recursoId = novoRecursoId;
        status = StatusSessao.AGENDADA;
        eventos.add(new SessaoRemarcada(id, clinicaId, profissionalId, anterior, novo));
        if (anterior.inicio().isAfter(agora)) {
            eventos.add(new VagaLiberada(clinicaId, profissionalId, recursoAnterior, anterior, id));
        }
    }

    /** Chamado pela persistência após um UPDATE bem-sucedido. */
    public void incrementarVersao() {
        versao++;
    }

    public List<Object> extrairEventos() {
        List<Object> copia = List.copyOf(eventos);
        eventos.clear();
        return copia;
    }

    // ------------------------------------------------------------------ auxiliares

    private void registrarAgendada() {
        eventos.add(new SessaoAgendada(id, clinicaId, pacienteId, profissionalId, periodo, tipo));
    }

    private void exigirTransicao(StatusSessao destino, String acao) {
        if (!status.podeIrPara(destino)) {
            throw new TransicaoInvalidaException(status, acao);
        }
    }

    private static void exigirFuturo(Periodo periodo, Instant agora) {
        if (!periodo.inicio().isAfter(agora)) {
            throw new TransicaoInvalidaException("Não é possível agendar num horário que já passou");
        }
    }

    // ------------------------------------------------------------------ leitura

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public UUID pacienteId() { return pacienteId; }
    public UUID profissionalId() { return profissionalId; }
    public UUID recursoId() { return recursoId; }
    public UUID serieId() { return serieId; }
    public TipoSessao tipo() { return tipo; }
    public Periodo periodo() { return periodo; }
    public StatusSessao status() { return status; }
    public Instant expiraEm() { return expiraEm; }
    public int versao() { return versao; }
}
