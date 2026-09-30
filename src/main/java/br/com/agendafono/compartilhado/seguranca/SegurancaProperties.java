package br.com.agendafono.compartilhado.seguranca;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param chavePrivadaPem   RSA PKCS#8 em PEM para assinar os JWT; vazia = par gerado na subida (só dev: tokens
 *                          deixam de valer a cada reinício)
 * @param chavePublicaPem   RSA X.509 em PEM correspondente
 * @param tokenPlataforma   segredo do endpoint de onboarding de clínicas; vazio = endpoint desligado
 */
@Validated
@ConfigurationProperties(prefix = "seguranca")
public record SegurancaProperties(
        @NotBlank String emissor,
        @NotNull Duration validadeAcesso,
        @NotNull Duration validadeRefresh,
        String chavePrivadaPem,
        String chavePublicaPem,
        String tokenPlataforma,
        @Min(1) int tentativasAntesDoBloqueio,
        @NotNull Duration tempoBloqueio) {
}
