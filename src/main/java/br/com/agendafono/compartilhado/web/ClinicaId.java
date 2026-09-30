package br.com.agendafono.compartilhado.web;

import java.util.Objects;
import java.util.UUID;

/** Clínica (tenant) da requisição atual, sempre extraída do JWT. Use como parâmetro de controller. */
public record ClinicaId(UUID valor) {

    public ClinicaId {
        Objects.requireNonNull(valor, "valor");
    }
}
