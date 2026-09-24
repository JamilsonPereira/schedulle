package br.com.agendafono.mensageria;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configurações da WhatsApp Cloud API.
 *
 * @param verifyToken token combinado com a Meta para a verificação do webhook (GET)
 * @param appSecret   App Secret do app na Meta, usado para validar a assinatura X-Hub-Signature-256
 * @param accessToken token de acesso para enviar mensagens (vazio em dev)
 * @param apiVersion  versão da Graph API, ex.: v23.0
 */
@Validated
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        @NotBlank String verifyToken,
        @NotBlank String appSecret,
        String accessToken,
        String apiVersion,
        String graphBaseUrl) {
}
