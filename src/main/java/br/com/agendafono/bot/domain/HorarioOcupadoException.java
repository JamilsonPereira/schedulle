package br.com.agendafono.bot.domain;

/** O horário escolhido foi ocupado entre a oferta e a reserva. */
public class HorarioOcupadoException extends RuntimeException {
    public HorarioOcupadoException() {
        super("Horário ocupado");
    }
}
