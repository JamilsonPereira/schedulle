package br.com.agendafono.pacientes.application;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param versaoConsentimentoAtual versão vigente do texto de consentimento (a mesma exibida pelo bot)
 * @param diretorioAnexos          pasta do armazenamento local de anexos (desenvolvimento)
 * @param fuso                     fuso usado para calcular idades
 */
@Validated
@ConfigurationProperties(prefix = "pacientes")
public record PacientesProperties(
        @NotBlank String versaoConsentimentoAtual,
        @NotBlank String diretorioAnexos,
        @NotBlank String fuso) {
}
