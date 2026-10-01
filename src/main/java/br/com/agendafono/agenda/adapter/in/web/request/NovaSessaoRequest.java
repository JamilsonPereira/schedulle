package br.com.agendafono.agenda.adapter.in.web.request;

import br.com.agendafono.agenda.TipoSessao;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record NovaSessaoRequest(@NotNull UUID pacienteId,
                                @NotNull UUID profissionalId,
                                @NotNull TipoSessao tipo,
                                @NotNull Instant inicio,
                                @Min(10) @Max(240) Integer duracaoMin,
                                UUID recursoId,
                                boolean permitirForaDaGrade) {
}
