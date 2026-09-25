package br.com.agendafono.agenda;

/** O horário está fora da grade do profissional, cai num bloqueio ou fora da janela permitida. */
public class HorarioForaDaAgendaException extends AgendaException {

    public enum Motivo { FORA_DA_GRADE, BLOQUEADO, ANTECEDENCIA_MINIMA, ALEM_DA_JANELA, NO_PASSADO }

    private final Motivo motivo;

    public HorarioForaDaAgendaException(Motivo motivo) {
        super("horario-fora-da-agenda", switch (motivo) {
            case FORA_DA_GRADE -> "O horário está fora da grade de atendimento do profissional";
            case BLOQUEADO -> "O horário está bloqueado na agenda";
            case ANTECEDENCIA_MINIMA -> "O horário não respeita a antecedência mínima da clínica";
            case ALEM_DA_JANELA -> "O horário está além da janela de agendamento da clínica";
            case NO_PASSADO -> "O horário já passou";
        });
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
