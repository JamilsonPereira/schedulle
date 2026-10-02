package br.com.agendafono.agenda.adapter.in.web.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record RemarcarRequest(@NotNull Instant novoInicio,
                              UUID recursoId,
                              boolean permitirForaDaGrade,
                              Integer versao) {
}
