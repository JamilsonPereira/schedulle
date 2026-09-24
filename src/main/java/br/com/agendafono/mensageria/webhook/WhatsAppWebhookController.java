package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.WhatsAppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppProperties properties;
    private final WebhookSignatureVerifier signatureVerifier;
    private final WebhookEntranteService webhookEntranteService;

    public WhatsAppWebhookController(
            WhatsAppProperties properties,
            WebhookSignatureVerifier signatureVerifier,
            WebhookEntranteService webhookEntranteService) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.webhookEntranteService = webhookEntranteService;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String token,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        boolean tokenOk = token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                properties.verifyToken().getBytes(StandardCharsets.UTF_8));

        if ("subscribe".equals(mode) && tokenOk && challenge != null) {
            log.info("Webhook do WhatsApp verificado pela Meta");
            return ResponseEntity.ok(challenge);
        }
        log.warn("Tentativa de verificacao do webhook recusada (mode={})", mode);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        if (!signatureVerifier.isValid(rawBody, signature)) {
            log.warn("Evento do webhook com assinatura invalida descartado");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            WebhookEntranteService.ResultadoEnfileiramento resultado = webhookEntranteService.enfileirar(rawBody);
            log.info(
                    "Evento do webhook recebido ({} bytes, {} inseridas, {} duplicadas, {} desconhecidas)",
                    rawBody.length,
                    resultado.inseridas(),
                    resultado.duplicadas(),
                    resultado.desconhecidas());
        } catch (WebhookPayloadInvalidoException e) {
            log.warn("Evento do webhook com JSON invalido descartado");
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }
}
