package br.com.agendafono.agenda;

import java.util.UUID;

/** Um horário livre para agendamento; {@code recursoId} é a sala/cabine da grade, quando houver. */
public record HorarioLivre(Periodo periodo, UUID recursoId) {
}
