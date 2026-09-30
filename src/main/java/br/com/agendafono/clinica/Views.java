package br.com.agendafono.clinica;

import br.com.agendafono.compartilhado.seguranca.Papel;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Modelos de leitura do módulo clínica (API pública). */
public final class Views {

    private Views() {
    }

    /** Valores nulos = padrão do sistema (ver {@code PoliticaAgendamento.PADRAO} na agenda). */
    public record PoliticaView(Integer antecedenciaMinimaMin, Integer janelaMaximaDias, Integer passoMin,
                               Integer ttlReservaMin, Integer antecedenciaAvisoFaltaHoras) {
    }

    public record ClinicaView(UUID id, String nome, String fuso, PoliticaView politica, int versao) {
    }

    public record IntervaloGradeView(DayOfWeek dia, LocalTime inicio, LocalTime fim, UUID recursoId) {
    }

    public record ProfissionalView(UUID id, String nome, String registroCrfa, Set<Subarea> subareas,
                                   int duracaoPadraoMin, UUID usuarioId, boolean ativo,
                                   List<IntervaloGradeView> grade, int versao) {
    }

    public record RecursoView(UUID id, String nome, TipoRecurso tipo, boolean ativo, int versao) {
    }

    /** {@code profissionalId} nulo = bloqueio da clínica inteira (ex.: feriado). */
    public record BloqueioView(UUID id, UUID profissionalId, Instant inicio, Instant fim, String motivo) {
    }

    public record UsuarioView(UUID id, String nome, String email, Set<Papel> papeis, boolean ativo,
                              boolean precisaTrocarSenha, boolean bloqueado, Instant ultimoLoginEm,
                              UUID profissionalId, int versao) {
    }
}
