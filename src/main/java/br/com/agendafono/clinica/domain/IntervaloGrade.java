package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ValidacaoException;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/** Um intervalo de atendimento na semana, em hora local da clínica, opcionalmente numa sala. */
public record IntervaloGrade(DayOfWeek dia, LocalTime inicio, LocalTime fim, UUID recursoId) {

    public IntervaloGrade {
        Objects.requireNonNull(dia, "dia");
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new ValidacaoException("Na grade, o fim deve ser depois do início (" + dia + " " + inicio + ")");
        }
    }

    boolean sobrepoe(IntervaloGrade outro) {
        return dia == outro.dia && inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }
}
