package br.com.agendafono.agenda;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Intervalo de tempo semiaberto {@code [inicio, fim)}, sempre em UTC.
 * Dois períodos adjacentes (um termina quando o outro começa) não se sobrepõem.
 */
public record Periodo(Instant inicio, Instant fim) {

    public Periodo {
        Objects.requireNonNull(inicio, "inicio");
        Objects.requireNonNull(fim, "fim");
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim do período deve ser depois do início");
        }
    }

    public static Periodo de(Instant inicio, Duration duracao) {
        return new Periodo(inicio, inicio.plus(duracao));
    }

    public Duration duracao() {
        return Duration.between(inicio, fim);
    }

    public boolean sobrepoe(Periodo outro) {
        return inicio.isBefore(outro.fim) && outro.inicio.isBefore(fim);
    }

    public boolean contem(Periodo outro) {
        return !outro.inicio.isBefore(inicio) && !outro.fim.isAfter(fim);
    }

    /** Partes deste período que sobram depois de remover {@code outro} (0, 1 ou 2 partes). */
    public List<Periodo> menos(Periodo outro) {
        if (!sobrepoe(outro)) {
            return List.of(this);
        }
        List<Periodo> partes = new ArrayList<>(2);
        if (inicio.isBefore(outro.inicio)) {
            partes.add(new Periodo(inicio, outro.inicio));
        }
        if (outro.fim.isBefore(fim)) {
            partes.add(new Periodo(outro.fim, fim));
        }
        return partes;
    }

    /** Remove todos os {@code ocupados} de cada período livre, devolvendo os trechos restantes em ordem. */
    public static List<Periodo> subtrair(Collection<Periodo> livres, Collection<Periodo> ocupados) {
        List<Periodo> resultado = new ArrayList<>(livres);
        for (Periodo ocupado : ocupados) {
            List<Periodo> proximo = new ArrayList<>(resultado.size() + 1);
            for (Periodo livre : resultado) {
                proximo.addAll(livre.menos(ocupado));
            }
            resultado = proximo;
        }
        resultado.sort(Comparator.comparing(Periodo::inicio));
        return resultado;
    }
}
