package br.com.agendafono.agenda;

/** A pré-reserva de 5 minutos venceu antes da confirmação (RN-04). */
public class ReservaExpiradaException extends AgendaException {

    public ReservaExpiradaException() {
        super("reserva-expirada", "A reserva do horário expirou; escolha o horário novamente");
    }
}
