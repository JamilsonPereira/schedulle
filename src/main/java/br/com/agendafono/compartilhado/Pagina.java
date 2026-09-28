package br.com.agendafono.compartilhado;

import java.util.List;
import java.util.function.Function;

/** Página de resultados. {@code pagina} começa em 0. */
public record Pagina<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos) {

    public Pagina {
        conteudo = List.copyOf(conteudo);
    }

    public int totalPaginas() {
        return tamanho == 0 ? 0 : (int) ((totalElementos + tamanho - 1) / tamanho);
    }

    public <R> Pagina<R> map(Function<T, R> f) {
        return new Pagina<>(conteudo.stream().map(f).toList(), pagina, tamanho, totalElementos);
    }
}
