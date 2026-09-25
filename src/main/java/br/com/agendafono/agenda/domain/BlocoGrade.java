package br.com.agendafono.agenda.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** Um intervalo da grade semanal do profissional, em hora local da clínica. */
public record BlocoGrade(DayOfWeek dia, LocalTime inicio, LocalTime fim, UUID recursoId) {

    public BlocoGrade {
        Objects.requireNonNull(dia, "dia");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim do bloco da grade deve ser depois do início");
        }
    }
}
