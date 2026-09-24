package br.com.agendafono.mensageria.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppWebhookParserTest {

    private final WhatsAppWebhookParser parser = new WhatsAppWebhookParser(new ObjectMapper());

    @Test
    void extraiMensagensDoPayloadDaCloudApi() throws Exception {
        byte[] body = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "metadata": {
                          "phone_number_id": "123456789"
                        },
                        "messages": [{
                          "from": "5511999999999",
                          "id": "wamid.HBgNNTUx",
                          "type": "text",
                          "text": { "body": "Oi" }
                        }, {
                          "from": "5511888888888",
                          "id": "wamid.HBgNNTUy",
                          "type": "interactive",
                          "interactive": {
                            "type": "button_reply",
                            "button_reply": { "id": "confirmo", "title": "Confirmo" }
                          }
                        }]
                      }
                    }]
                  }]
                }
                """.getBytes(StandardCharsets.UTF_8);

        assertThat(parser.extrairMensagens(body))
                .containsExactly(
                        new MensagemWebhook("wamid.HBgNNTUx", "123456789", "5511999999999", "TEXT", "Oi"),
                        new MensagemWebhook("wamid.HBgNNTUy", "123456789", "5511888888888", "INTERACTIVE", "Confirmo"));
    }
}
