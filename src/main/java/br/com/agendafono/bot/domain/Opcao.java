package br.com.agendafono.bot.domain;

import java.util.Objects;

/** Um botão de resposta ou item de lista. {@code descricao} só vale para lista. */
public record Opcao(String id, String titulo, String descricao) {

    public Opcao {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(titulo, "titulo");
        if (id.isBlank() || id.length() > 200) {
            throw new IllegalArgumentException("id de opção inválido");
        }
    }

    public Opcao(String id, String titulo) {
        this(id, titulo, null);
    }
}
