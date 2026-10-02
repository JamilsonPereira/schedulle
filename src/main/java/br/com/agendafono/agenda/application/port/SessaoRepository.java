package br.com.agendafono.agenda.application.port;

import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.VersaoDesatualizadaException;
import br.com.agendafono.agenda.domain.Sessao;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída para persistência de sessões. Toda operação é filtrada pela clínica. */
public interface SessaoRepository {

    Optional<Sessao> buscar(UUID clinicaId, UUID sessaoId);

    /** @throws HorarioIndisponivelException se a constraint de conflito do banco recusar */
    void inserir(Sessao sessao);

    /**
     * Grava a sessão com controle otimista pela versão lida e incrementa a versão do objeto.
     *
     * @throws VersaoDesatualizadaException se outra transação alterou a sessão
     * @throws HorarioIndisponivelException se o novo horário conflitar
     */
    void atualizar(Sessao sessao);

    /** Sessões cujo início está em {@code [de, ate)}; {@code profissionalId} nulo = todos. */
    List<Sessao> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate);

    /** Sessões do paciente com início em {@code [de, ate)}, mais recentes primeiro. */
    List<Sessao> doPaciente(UUID clinicaId, UUID pacienteId, Instant de, Instant ate);

    /** Períodos que ocupam a agenda do profissional no intervalo (reservas vencidas não contam). */
    List<Periodo> ocupadosDoProfissional(UUID clinicaId, UUID profissionalId, Periodo intervalo, Instant agora,
                                         UUID ignorarSessaoId);

    /** Idem, por sala/cabine. */
    Map<UUID, List<Periodo>> ocupadosPorRecurso(UUID clinicaId, Collection<UUID> recursos, Periodo intervalo,
                                                Instant agora, UUID ignorarSessaoId);

    /** Cancela reservas vencidas que ainda seguram horário do profissional ou do recurso. */
    int cancelarReservasExpiradas(UUID clinicaId, UUID profissionalId, UUID recursoId, Instant agora);

    /** Job: cancela todas as reservas vencidas de todas as clínicas. */
    int cancelarTodasReservasExpiradas(Instant agora);
}
