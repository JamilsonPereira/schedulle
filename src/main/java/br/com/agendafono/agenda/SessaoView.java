package br.com.agendafono.agenda;

import java.time.Instant;
import java.util.UUID;

/** Visão de leitura de uma sessão, exposta a outros módulos e à API. Horários em UTC. */
public record SessaoView(
        UUID id,
        UUID clinicaId,
        UUID pacienteId,
        UUID profissionalId,
        UUID recursoId,
        UUID serieId,
        TipoSessao tipo,
        Instant inicio,
        Instant fim,
        StatusSessao status,
        Instant expiraEm,
        int versao) {
}
