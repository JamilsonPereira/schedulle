package br.com.agendafono.mensageria.webhook;

public class WebhookPayloadInvalidoException extends RuntimeException {

    public WebhookPayloadInvalidoException(Throwable cause) {
        super("Payload do webhook invalido", cause);
    }
}
