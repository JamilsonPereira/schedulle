package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.TipoRecurso;
import br.com.agendafono.compartilhado.seguranca.Papel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contratos HTTP do módulo clínica. */
final class ClinicaDtos {

    private ClinicaDtos() {
    }

    // ------------------------------------------------------------------ autenticação

    record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 128) String senha) {
    }

    record RefreshRequest(@NotBlank @Size(max = 100) String refreshToken) {
    }

    /** {@code todas = true} encerra as sessões do usuário em todos os aparelhos. */
    record LogoutRequest(@Size(max = 100) String refreshToken, boolean todas) {
    }

    record TrocarSenhaRequest(@NotBlank @Size(max = 128) String senhaAtual,
                              @NotBlank @Size(max = 128) String novaSenha) {
    }

    // ------------------------------------------------------------------ usuários

    record NovoUsuarioRequest(@NotBlank @Size(max = 120) String nome,
                              @NotBlank @Email @Size(max = 254) String email,
                              @NotEmpty Set<Papel> papeis) {
    }

    record AtualizarUsuarioRequest(@NotBlank @Size(max = 120) String nome,
                                   @NotEmpty Set<Papel> papeis,
                                   boolean ativo,
                                   Integer versao) {
    }

    // ------------------------------------------------------------------ clínica

    record AtualizarClinicaRequest(@NotBlank @Size(max = 120) String nome,
                                   @NotBlank @Size(max = 64) String fuso,
                                   Integer versao) {
    }

    /** Campos nulos voltam ao padrão do sistema. */
    record PoliticaRequest(Integer antecedenciaMinimaMin, Integer janelaMaximaDias, Integer passoMin,
                           Integer ttlReservaMin, Integer antecedenciaAvisoFaltaHoras, Integer versao) {
    }

    record RecursoRequest(@NotBlank @Size(max = 60) String nome, @NotNull TipoRecurso tipo) {
    }

    record AtualizarRecursoRequest(@NotBlank @Size(max = 60) String nome, @NotNull TipoRecurso tipo, boolean ativo,
                                   Integer versao) {
    }

    // ------------------------------------------------------------------ profissionais

    record ProfissionalRequest(@NotBlank @Size(max = 120) String nome,
                               @Size(max = 30) String registroCrfa,
                               @NotEmpty Set<Subarea> subareas,
                               Integer duracaoPadraoMin,
                               UUID usuarioId,
                               Integer versao) {
    }

    record IntervaloRequest(@NotNull DayOfWeek dia, @NotNull LocalTime inicio, @NotNull LocalTime fim,
                            UUID recursoId) {
    }

    record GradeRequest(@NotNull @Size(max = 50) List<@Valid @NotNull IntervaloRequest> intervalos,
                        Integer versao) {
    }

    record VersaoRequest(Integer versao) {
    }

    // ------------------------------------------------------------------ bloqueios

    /** {@code profissionalId} nulo = bloqueio da clínica inteira (só ADMIN e RECEPCAO). */
    record BloqueioRequest(UUID profissionalId, @NotNull Instant inicio, @NotNull Instant fim,
                           @Size(max = 200) String motivo) {
    }

    // ------------------------------------------------------------------ plataforma

    record NovaClinicaRequest(@NotBlank @Size(max = 120) String nomeClinica,
                              @Size(max = 64) String fuso,
                              @NotBlank @Size(max = 120) String nomeAdministrador,
                              @NotBlank @Email @Size(max = 254) String emailAdministrador) {
    }
}
