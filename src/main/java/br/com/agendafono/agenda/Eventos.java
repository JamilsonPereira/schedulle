package br.com.agendafono.agenda;

import java.util.UUID;

/**
 * Eventos de domínio publicados pelo módulo agenda (API pública do módulo).
 * Carregam só IDs e dados mínimos; quem consome busca o resto se precisar.
 */
public final class Eventos {

    private Eventos() {
    }

    /** Sessão passou a ocupar a agenda de forma definitiva (AGENDADA). */
    public record SessaoAgendada(UUID sessaoId, UUID clinicaId, UUID pacienteId,
                                 UUID profissionalId, Periodo periodo, TipoSessao tipo) {
    }

    public record SessaoConfirmada(UUID sessaoId, UUID clinicaId) {
    }

    public record SessaoRemarcada(UUID sessaoId, UUID clinicaId, UUID profissionalId,
                                  Periodo anterior, Periodo novo) {
    }

    public record SessaoCancelada(UUID sessaoId, UUID clinicaId, UUID profissionalId,
                                  Periodo periodo) {
    }

    /** Falta avisada pelo responsável; {@code direitoReposicao} segue a política da clínica (RN-20). */
    public record FaltaAvisada(UUID sessaoId, UUID clinicaId, UUID pacienteId,
                               UUID profissionalId, Periodo periodo, boolean direitoReposicao) {
    }

    public record PresencaRegistrada(UUID sessaoId, UUID clinicaId, StatusSessao resultado) {
    }

    /** Um horário futuro que estava ocupado ficou livre (dispara a oferta de vaga, RN-40). */
    public record VagaLiberada(UUID clinicaId, UUID profissionalId, UUID recursoId,
                               Periodo periodo, UUID sessaoOrigemId) {
    }
}
