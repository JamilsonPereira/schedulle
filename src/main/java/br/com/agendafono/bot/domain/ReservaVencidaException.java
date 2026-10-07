package br.com.agendafono.bot.domain;

/** A pré-reserva venceu antes da confirmação. */
public class ReservaVencidaException extends RuntimeException {
    public ReservaVencidaException() {
        super("Reserva vencida");
    }
}
