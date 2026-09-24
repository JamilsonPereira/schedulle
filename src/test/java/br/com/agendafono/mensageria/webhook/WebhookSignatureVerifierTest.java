package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.WhatsAppProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private final WebhookSignatureVerifier verifier =
            new WebhookSignatureVerifier(new WhatsAppProperties("token", "segredo", null, "v23.0", "https://graph.facebook.com"));

    private final byte[] body = "{\"object\":\"whatsapp_business_account\"}".getBytes(StandardCharsets.UTF_8);

    @Test
    void aceitaAssinaturaCorreta() {
        assertThat(verifier.isValid(body, verifier.sign(body))).isTrue();
    }

    @Test
    void aceitaValorConhecidoDeHmacSha256() {
        // HMAC-SHA256("{\"object\":\"whatsapp_business_account\"}", "segredo") calculado de forma independente
        String esperado = "sha256=" + hmacReferencia();
        assertThat(verifier.isValid(body, esperado)).isTrue();
    }

    @Test
    void rejeitaCorpoAlterado() {
        String assinatura = verifier.sign(body);
        byte[] adulterado = "{\"object\":\"outro\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(adulterado, assinatura)).isFalse();
    }

    @Test
    void rejeitaAssinaturaComOutroSegredo() {
        var outro = new WebhookSignatureVerifier(new WhatsAppProperties("token", "outro-segredo", null, "v23.0", "https://graph.facebook.com"));
        assertThat(verifier.isValid(body, outro.sign(body))).isFalse();
    }

    @Test
    void rejeitaHeaderAusenteOuMalformado() {
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "md5=abc")).isFalse();
        assertThat(verifier.isValid(body, "sha256=nao-e-hex")).isFalse();
        assertThat(verifier.isValid(body, "sha256=")).isFalse();
    }

    private static String hmacReferencia() {
        return "f3332ff5c60ad4472fec5895fcab6be6b0b666af00ff72ffccc32c140e972870";
    }
}
