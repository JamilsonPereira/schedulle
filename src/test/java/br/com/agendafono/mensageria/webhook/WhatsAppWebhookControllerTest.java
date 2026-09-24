package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.WhatsAppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(WhatsAppWebhookController.class)
@TestPropertySource(properties = {
        "whatsapp.verify-token=token-de-teste",
        "whatsapp.app-secret=segredo-de-teste"
})
class WhatsAppWebhookControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(WhatsAppProperties.class)
    @Import(WebhookSignatureVerifier.class)
    static class Config {
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    WebhookSignatureVerifier verifier;

    @MockitoBean
    WebhookEntranteService webhookEntranteService;

    @Test
    void devolveChallengeQuandoTokenConfere() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-de-teste")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void recusaVerificacaoComTokenErrado() throws Exception {
        mvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "outro")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aceitaEventoComAssinaturaValida() throws Exception {
        byte[] body = "{\"object\":\"whatsapp_business_account\",\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        when(webhookEntranteService.enfileirar(any()))
                .thenReturn(new WebhookEntranteService.ResultadoEnfileiramento(0, 0, 0));

        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", verifier.sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void recusaEventoSemAssinatura() throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"object\":\"whatsapp_business_account\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recusaEventoComAssinaturaInvalida() throws Exception {
        mvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .content("{\"object\":\"whatsapp_business_account\"}"))
                .andExpect(status().isUnauthorized());
    }
}
