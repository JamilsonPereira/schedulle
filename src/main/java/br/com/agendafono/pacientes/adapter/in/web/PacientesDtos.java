package br.com.agendafono.pacientes.adapter.in.web;

import br.com.agendafono.pacientes.Demanda;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Contratos HTTP do módulo pacientes. */
final class PacientesDtos {

    private PacientesDtos() {
    }

    /**
     * Cadastro pelo painel. O responsável é encontrado pelo telefone ou criado. Se ele ainda não tiver
     * consentimento, {@code consentimentoColetado} precisa ser {@code true} (termo aceito presencialmente).
     */
    record NovoPacienteRequest(
            @NotBlank String telefoneResponsavel,
            @Size(max = 120) String nomeResponsavel,
            @NotBlank @Size(max = 120) String nome,
            LocalDate dataNascimento,
            Demanda demanda,
            boolean consentimentoColetado) {
    }

    record AtualizarPacienteRequest(
            @NotBlank @Size(max = 120) String nome,
            LocalDate dataNascimento,
            Demanda demanda,
            Integer versao) {
    }

    record VersaoRequest(Integer versao) {
    }

    record AtualizarResponsavelRequest(
            @Size(max = 120) String nome,
            @NotBlank String telefone,
            Integer versao) {
    }

    /** {@code versaoTexto} nulo = versão vigente configurada em {@code pacientes.versao-consentimento-atual}. */
    record ConsentimentoRequest(String versaoTexto) {
    }
}
