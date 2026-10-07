package br.com.agendafono.bot.domain;

import java.time.Instant;
import java.util.UUID;

/** Horário livre oferecido no WhatsApp. {@code id} é curto ("h1") porque vai no item da lista. */
public record HorarioOferta(String id, UUID profissionalId, String profissionalNome, Instant inicio, Instant fim) {
}
