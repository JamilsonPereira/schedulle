package br.com.agendafono.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class MetaCloudApiWhatsAppProvider implements ProvedorWhatsApp {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudApiWhatsAppProvider.class);

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    public MetaCloudApiWhatsAppProvider(WhatsAppProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.graphBaseUrl())
                .build();
    }

    @Override
    public void enviarTexto(String phoneNumberId, String telefone, String texto) {
        if (properties.accessToken() == null || properties.accessToken().isBlank()) {
            log.info("Envio WhatsApp ignorado em dev: access token ausente");
            return;
        }

        restClient.post()
                .uri("/{version}/{phoneNumberId}/messages", properties.apiVersion(), phoneNumberId)
                .header("Authorization", "Bearer " + properties.accessToken())
                .body(Map.of(
                        "messaging_product", "whatsapp",
                        "recipient_type", "individual",
                        "to", telefone,
                        "type", "text",
                        "text", Map.of("preview_url", false, "body", texto)))
                .retrieve()
                .toBodilessEntity();
    }
}
