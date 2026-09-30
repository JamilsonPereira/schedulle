package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ValidacaoException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Período sem atendimento. {@code profissionalId} nulo = clínica inteira (feriado).
 * O motivo é texto curto e operacional ("Congresso", "Férias"); nunca informação clínica.
 */
public record Bloqueio(UUID id, UUID clinicaId, UUID profissionalId, Instant inicio, Instant fim, String motivo) {

    public Bloqueio {
        Objects.requireNonNull(id);
        Objects.requireNonNull(clinicaId);
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new ValidacaoException("O fim do bloqueio deve ser depois do início");
        }
        if (Duration.between(inicio, fim).toDays() > 366) {
            throw new ValidacaoException("Um bloqueio pode durar no máximo um ano");
        }
        motivo = Textos.opcional(motivo, "O motivo", 2, 200);
    }
}
