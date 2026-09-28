package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Canal;

import java.time.Instant;

/** Linha do histórico de consentimento: prova do aceite ou da revogação. */
public record RegistroConsentimento(Acao acao, String versaoTexto, Canal canal, String evidencia,
                                    Instant registradoEm) {

    public enum Acao { CONCEDIDO, REVOGADO }
}
