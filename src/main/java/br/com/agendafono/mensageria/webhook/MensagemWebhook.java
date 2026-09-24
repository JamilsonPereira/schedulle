package br.com.agendafono.mensageria.webhook;

public record MensagemWebhook(
        String wamid,
        String phoneNumberId,
        String telefone,
        String tipo,
        String texto) {
}
