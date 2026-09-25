package br.com.agendafono.compartilhado.web;

import java.util.Objects;
import java.util.UUID;

/** Clínica (tenant) da requisição atual. Parâmetro de controller resolvido por {@link ClinicaIdArgumentResolver}. */
public record ClinicaId(UUID valor) {

    public ClinicaId {
        Objects.requireNonNull(valor, "valor");
    }
}
