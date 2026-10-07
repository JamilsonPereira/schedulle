package br.com.agendafono.bot;

import java.time.Instant;
import java.util.UUID;

/** Conversa em modo HUMANO, para a fila da recepção. Telefone sempre mascarado. */
public record ConversaEmAtendimento(UUID id, UUID responsavelId, String responsavelNome, String telefoneMascarado,
                                    Instant ultimaMensagemEm) {
}
