package br.com.agendafono.bot.domain;

/** Estados da conversa (Especificação do MVP, seção 6). */
public enum EstadoConversa {
    INICIO,
    CONSENTIMENTO,
    MENU,
    PARA_QUEM,
    NOVO_NOME,
    NOVO_NASCIMENTO,
    NOVA_DEMANDA,
    ESCOLHER_HORARIO,
    CONFIRMAR,
    HUMANO
}
