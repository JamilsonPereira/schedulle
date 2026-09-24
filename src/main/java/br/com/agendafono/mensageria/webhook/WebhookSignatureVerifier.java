package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.WhatsAppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida o header X-Hub-Signature-256 enviado pela Meta em cada POST do webhook.
 * A assinatura é "sha256=" + HMAC-SHA256(corpo bruto, App Secret) em hexadecimal.
 */
@Component
public class WebhookSignatureVerifier {

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] appSecret;

    public WebhookSignatureVerifier(WhatsAppProperties properties) {
        this.appSecret = properties.appSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (rawBody == null || signatureHeader == null || !signatureHeader.startsWith(PREFIX)) {
            return false;
        }
        byte[] received;
        try {
            received = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return false;
        }
        // Comparação em tempo constante para não vazar informação por timing
        return MessageDigest.isEqual(hmac(rawBody), received);
    }

    public String sign(byte[] rawBody) {
        return PREFIX + HexFormat.of().formatHex(hmac(rawBody));
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(appSecret, ALGORITHM));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível", e);
        }
    }
}
