package br.com.agendafono.agenda;

import java.util.List;

/** O horário pedido já está ocupado (RN-03). Traz até 3 alternativas próximas quando disponíveis. */
public class HorarioIndisponivelException extends AgendaException {

    private final List<HorarioLivre> alternativas;

    public HorarioIndisponivelException(List<HorarioLivre> alternativas) {
        super("horario-indisponivel", "Esse horário acabou de ser ocupado");
        this.alternativas = List.copyOf(alternativas);
    }

    public HorarioIndisponivelException() {
        this(List.of());
    }

    public List<HorarioLivre> alternativas() {
        return alternativas;
    }
}
