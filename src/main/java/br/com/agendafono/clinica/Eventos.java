package br.com.agendafono.clinica;

import java.time.Instant;
import java.util.UUID;

/** Eventos publicados pelo módulo clínica. */
public final class Eventos {

    private Eventos() {
    }

    /** Dispara o aviso em massa aos pacientes afetados (RF-23). {@code profissionalId} nulo = clínica toda. */
    public record BloqueioCriado(UUID clinicaId, UUID bloqueioId, UUID profissionalId, Instant inicio, Instant fim) {
    }

    public record BloqueioRemovido(UUID clinicaId, UUID bloqueioId) {
    }
}
