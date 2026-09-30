package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.ValidacaoException;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

public final class Clinica {

    private final UUID id;
    private String nome;
    private ZoneId fuso;
    private PoliticaClinica politica;
    private int versao;

    private Clinica(UUID id, String nome, ZoneId fuso, PoliticaClinica politica, int versao) {
        this.id = Objects.requireNonNull(id);
        this.nome = nome;
        this.fuso = fuso;
        this.politica = politica;
        this.versao = versao;
    }

    public static Clinica nova(UUID id, String nome, String fuso) {
        return new Clinica(id, Textos.obrigatorio(nome, "o nome da clínica", 2, 120), fuso(fuso),
                PoliticaClinica.PADRAO, 0);
    }

    public static Clinica reconstituir(UUID id, String nome, ZoneId fuso, PoliticaClinica politica, int versao) {
        return new Clinica(id, nome, fuso, politica, versao);
    }

    public void exigirVersao(Integer esperada) {
        if (esperada != null && esperada != versao) {
            throw new ConflitoDeVersaoException();
        }
    }

    public void atualizar(String novoNome, String novoFuso) {
        nome = Textos.obrigatorio(novoNome, "o nome da clínica", 2, 120);
        fuso = fuso(novoFuso);
    }

    public void definirPolitica(PoliticaClinica nova) {
        politica = Objects.requireNonNull(nova);
    }

    public void incrementarVersao() {
        versao++;
    }

    /** Aceita só fusos do Brasil: evita erro de digitação que deslocaria toda a agenda. */
    static ZoneId fuso(String valor) {
        String v = valor == null || valor.isBlank() ? "America/Sao_Paulo" : valor.trim();
        try {
            ZoneId zona = ZoneId.of(v);
            if (!zona.getId().startsWith("America/")) {
                throw new ValidacaoException("Fuso inválido; use um fuso do Brasil, ex.: America/Sao_Paulo");
            }
            return zona;
        } catch (DateTimeException e) {
            throw new ValidacaoException("Fuso inválido: " + v);
        }
    }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public ZoneId fuso() { return fuso; }
    public PoliticaClinica politica() { return politica; }
    public int versao() { return versao; }
}
