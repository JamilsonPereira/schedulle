package br.com.agendafono.bot;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma mensagem recebida no WhatsApp, como a mensageria a guarda em {@code evento_entrada}.
 *
 * @param telefone      número do contato só com dígitos (wa_id da Meta), ex.: 5511999990000
 * @param tipo          TEXT, INTERACTIVE, BUTTON ou UNKNOWN
 * @param texto         texto digitado (tipo TEXT), se houver
 * @param payload       JSON da mensagem da Meta (objeto de {@code messages[]}); de onde sai o id do botão ou do
 *                      item de lista clicado
 */
public record MensagemDoContato(UUID clinicaId, String phoneNumberId, String telefone, String wamid, String tipo,
                                String texto, String payload, Instant recebidaEm) {

    public MensagemDoContato {
        Objects.requireNonNull(clinicaId, "clinicaId");
        Objects.requireNonNull(phoneNumberId, "phoneNumberId");
        Objects.requireNonNull(telefone, "telefone");
        Objects.requireNonNull(wamid, "wamid");
        Objects.requireNonNull(recebidaEm, "recebidaEm");
    }
}
