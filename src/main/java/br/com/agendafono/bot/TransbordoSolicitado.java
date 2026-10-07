package br.com.agendafono.bot;

import java.util.UUID;

/** A conversa passou para atendimento humano (a recepção responde pelo painel). Sem dados pessoais. */
public record TransbordoSolicitado(UUID clinicaId, UUID conversaId, Motivo motivo) {

    public enum Motivo { PEDIU_ATENDENTE, NAO_ENTENDEU, SEM_HORARIOS, SEM_CONSENTIMENTO }
}
