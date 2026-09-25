package br.com.agendafono.agenda;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida da sessão (SDD, seção 4).
 * ATENDIDA, CANCELADA, FALTA_AVISADA e FALTA_SEM_AVISO são estados finais.
 */
public enum StatusSessao {

    RESERVADA,
    AGENDADA,
    CONFIRMADA,
    ATENDIDA,
    CANCELADA,
    FALTA_AVISADA,
    FALTA_SEM_AVISO;

    private static final Set<StatusSessao> ATIVOS = EnumSet.of(RESERVADA, AGENDADA, CONFIRMADA);

    /** Estados que ocupam horário na agenda (os mesmos da constraint EXCLUDE no banco). */
    public boolean ocupaHorario() {
        return ATIVOS.contains(this);
    }

    public boolean isFinal() {
        return !ocupaHorario();
    }

    public boolean podeIrPara(StatusSessao destino) {
        return switch (this) {
            case RESERVADA -> destino == AGENDADA || destino == CANCELADA;
            case AGENDADA -> destino == CONFIRMADA || destino == CANCELADA || destino == FALTA_AVISADA
                    || destino == ATENDIDA || destino == FALTA_SEM_AVISO;
            case CONFIRMADA -> destino == CANCELADA || destino == FALTA_AVISADA
                    || destino == ATENDIDA || destino == FALTA_SEM_AVISO;
            case ATENDIDA, CANCELADA, FALTA_AVISADA, FALTA_SEM_AVISO -> false;
        };
    }
}
