package br.com.agendafono.compartilhado.seguranca;

/** Perfis de acesso ao painel. Um usuário pode ter mais de um (ex.: dona da clínica que também atende). */
public enum Papel {
    ADMIN,
    RECEPCAO,
    FONO
}
