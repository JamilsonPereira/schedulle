package br.com.agendafono.agenda.adapter.in.web;

import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.TipoSessao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Contratos HTTP do módulo agenda. Horários sempre em ISO 8601 UTC (ex.: 2026-10-05T17:00:00Z). */
final class AgendaDtos {

    private AgendaDtos() {
    }

    record HorarioLivreResponse(Instant inicio, Instant fim, UUID recursoId) {
        static HorarioLivreResponse de(HorarioLivre h) {
            return new HorarioLivreResponse(h.periodo().inicio(), h.periodo().fim(), h.recursoId());
        }
    }

    enum AcaoStatus {
        CONFIRMAR_PRESENCA,
        CANCELAR,
        AVISAR_FALTA,
        REGISTRAR_ATENDIMENTO,
        REGISTRAR_FALTA_SEM_AVISO
    }

    record AlterarStatusRequest(@NotNull AcaoStatus acao, Integer versao) {
    }
}
