package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Canal;

import java.time.Instant;
import java.util.Objects;

/** Consentimento vigente do responsável. */
public record Consentimento(String versaoTexto, Canal canal, String evidencia, Instant registradoEm) {

    public Consentimento {
        Objects.requireNonNull(versaoTexto, "versaoTexto");
        Objects.requireNonNull(canal, "canal");
        Objects.requireNonNull(evidencia, "evidencia");
        Objects.requireNonNull(registradoEm, "registradoEm");
    }
}
