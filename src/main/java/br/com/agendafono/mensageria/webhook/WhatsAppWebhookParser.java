package br.com.agendafono.mensageria.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class WhatsAppWebhookParser {

    private final ObjectMapper objectMapper;

    public WhatsAppWebhookParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<MensagemWebhook> extrairMensagens(byte[] rawBody) throws IOException {
        JsonNode root = objectMapper.readTree(rawBody);
        List<MensagemWebhook> mensagens = new ArrayList<>();

        for (JsonNode entry : iterable(root.path("entry"))) {
            for (JsonNode change : iterable(entry.path("changes"))) {
                JsonNode value = change.path("value");
                String phoneNumberId = textOrNull(value.path("metadata").path("phone_number_id"));

                for (JsonNode message : iterable(value.path("messages"))) {
                    String wamid = textOrNull(message.path("id"));
                    String telefone = textOrNull(message.path("from"));
                    if (wamid == null || phoneNumberId == null || telefone == null) {
                        continue;
                    }
                    mensagens.add(new MensagemWebhook(
                            wamid,
                            phoneNumberId,
                            telefone,
                            tipo(message),
                            texto(message)));
                }
            }
        }
        return mensagens;
    }

    private static String tipo(JsonNode message) {
        return switch (message.path("type").asText("unknown")) {
            case "text" -> "TEXT";
            case "interactive" -> "INTERACTIVE";
            case "button" -> "BUTTON";
            default -> "UNKNOWN";
        };
    }

    private static String texto(JsonNode message) {
        String type = message.path("type").asText();
        if ("text".equals(type)) {
            return textOrNull(message.path("text").path("body"));
        }
        if ("interactive".equals(type)) {
            JsonNode interactive = message.path("interactive");
            String interactiveType = interactive.path("type").asText();
            if ("button_reply".equals(interactiveType)) {
                return textOrNull(interactive.path("button_reply").path("title"));
            }
            if ("list_reply".equals(interactiveType)) {
                return textOrNull(interactive.path("list_reply").path("title"));
            }
        }
        if ("button".equals(type)) {
            return textOrNull(message.path("button").path("text"));
        }
        return null;
    }

    private static Iterable<JsonNode> iterable(JsonNode node) {
        return node::elements;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }
}
