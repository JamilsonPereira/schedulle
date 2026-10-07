package br.com.agendafono.bot.application.etapas;

import br.com.agendafono.bot.application.port.ServicosDaClinica.DadosDaClinica;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Quem está falando, com qual clínica e quando. */
public record Situacao(UUID clinicaId, UUID responsavelId, boolean consentido, DadosDaClinica clinica,
                       Instant agora) {

    public LocalDate hoje() {
        return agora.atZone(clinica.fuso()).toLocalDate();
    }
}
