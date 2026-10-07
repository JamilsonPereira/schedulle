package br.com.agendafono.bot;

import java.util.List;
import java.util.UUID;

/** Fila de conversas que passaram para a recepção (modo HUMANO). Usada pelo painel. */
public interface AtendimentoHumano {

    /** Conversas em atendimento humano, da mensagem mais antiga para a mais recente. */
    List<ConversaEmAtendimento> emAtendimento(UUID clinicaId);

    /**
     * A recepção encerrou o atendimento: o bot volta a responder, a partir do menu.
     *
     * @return false se a conversa não existe nesta clínica
     */
    boolean devolverAoBot(UUID clinicaId, UUID conversaId);
}
