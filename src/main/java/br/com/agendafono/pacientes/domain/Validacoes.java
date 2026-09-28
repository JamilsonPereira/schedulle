package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.DadosInvalidosException;

import java.time.LocalDate;

final class Validacoes {

    private Validacoes() {
    }

    static String nomeObrigatorio(String nome, String campo) {
        String n = nomeOpcional(nome, campo);
        if (n == null) {
            throw new DadosInvalidosException("Informe o " + campo);
        }
        return n;
    }

    static String nomeOpcional(String nome, String campo) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String n = nome.trim().replaceAll("\\s+", " ");
        if (n.length() < 2 || n.length() > 120) {
            throw new DadosInvalidosException("O " + campo + " deve ter entre 2 e 120 caracteres");
        }
        return n;
    }

    static void nascimento(LocalDate dataNascimento, LocalDate hoje) {
        if (dataNascimento == null) {
            return;
        }
        if (dataNascimento.isAfter(hoje)) {
            throw new DadosInvalidosException("A data de nascimento não pode ser no futuro");
        }
        if (dataNascimento.isBefore(hoje.minusYears(120))) {
            throw new DadosInvalidosException("Data de nascimento inválida");
        }
    }
}
