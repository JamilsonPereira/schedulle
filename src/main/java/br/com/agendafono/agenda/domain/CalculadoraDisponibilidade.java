package br.com.agendafono.agenda.domain;

import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.Periodo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Calcula horários livres (SDD, seção 6.1): grade − bloqueios − sessões ativas, fatiado pela duração.
 * Função pura: não acessa banco nem relógio; tudo chega pela {@link Entrada}.
 */
public final class CalculadoraDisponibilidade {

    /**
     * @param ocupadosProfissional sessões ativas do profissional no intervalo
     * @param ocupadosPorRecurso   sessões ativas por sala/cabine usada na grade
     * @param naoAntesDe           nenhum horário pode começar antes deste instante
     * @param naoDepoisDe          nenhum horário pode começar depois deste instante
     */
    public record Entrada(
            ZoneId zona,
            LocalDate de,
            LocalDate ate,
            List<BlocoGrade> grade,
            List<Periodo> bloqueios,
            List<Periodo> ocupadosProfissional,
            Map<UUID, List<Periodo>> ocupadosPorRecurso,
            Duration duracao,
            Duration passo,
            Instant naoAntesDe,
            Instant naoDepoisDe) {

        public Entrada {
            Objects.requireNonNull(zona);
            Objects.requireNonNull(de);
            Objects.requireNonNull(ate);
            grade = List.copyOf(grade);
            bloqueios = List.copyOf(bloqueios);
            ocupadosProfissional = List.copyOf(ocupadosProfissional);
            ocupadosPorRecurso = Map.copyOf(ocupadosPorRecurso);
            Objects.requireNonNull(duracao);
            Objects.requireNonNull(passo);
            Objects.requireNonNull(naoAntesDe);
            Objects.requireNonNull(naoDepoisDe);
            if (duracao.isNegative() || duracao.isZero() || passo.isNegative() || passo.isZero()) {
                throw new IllegalArgumentException("Duração e passo devem ser positivos");
            }
        }
    }

    /** Resultado da verificação de um horário específico. */
    public enum Veredito { DISPONIVEL, FORA_DA_GRADE, BLOQUEADO, OCUPADO, ANTES_DO_PERMITIDO, DEPOIS_DO_PERMITIDO }

    public record Verificacao(Veredito veredito, UUID recursoDaGrade) {
        public boolean disponivel() {
            return veredito == Veredito.DISPONIVEL;
        }
    }

    public List<HorarioLivre> calcular(Entrada e) {
        List<HorarioLivre> slots = new ArrayList<>();
        for (LocalDate dia = e.de(); !dia.isAfter(e.ate()); dia = dia.plusDays(1)) {
            for (BlocoGrade bloco : e.grade()) {
                if (bloco.dia() != dia.getDayOfWeek()) {
                    continue;
                }
                Periodo janela = periodoDoBloco(dia, bloco, e.zona());
                for (Periodo livre : livresNoBloco(janela, bloco, e)) {
                    fatiar(livre, bloco.recursoId(), e, slots);
                }
            }
        }
        slots.sort(Comparator.comparing((HorarioLivre h) -> h.periodo().inicio()));
        return deduplicar(slots);
    }

    /**
     * Verifica um período exato, sem exigir alinhamento ao passo (usado pelo painel e pela remarcação).
     * A checagem de ocupação aqui é só uma prévia amigável: quem decide de fato é a constraint do banco.
     */
    public Verificacao verificar(Entrada e, Periodo pedido) {
        if (pedido.inicio().isBefore(e.naoAntesDe())) {
            return new Verificacao(Veredito.ANTES_DO_PERMITIDO, null);
        }
        if (pedido.inicio().isAfter(e.naoDepoisDe())) {
            return new Verificacao(Veredito.DEPOIS_DO_PERMITIDO, null);
        }
        Optional<BlocoGrade> bloco = blocoQueContem(e, pedido);
        if (bloco.isEmpty()) {
            return new Verificacao(Veredito.FORA_DA_GRADE, null);
        }
        UUID recurso = bloco.get().recursoId();
        return new Verificacao(vereditoDeOcupacao(e, pedido, recurso), recurso);
    }

    /** Como {@link #verificar}, mas ignora a grade (encaixe da recepção). */
    public Verificacao verificarSemGrade(Entrada e, Periodo pedido, UUID recursoId) {
        if (pedido.inicio().isBefore(e.naoAntesDe())) {
            return new Verificacao(Veredito.ANTES_DO_PERMITIDO, null);
        }
        if (pedido.inicio().isAfter(e.naoDepoisDe())) {
            return new Verificacao(Veredito.DEPOIS_DO_PERMITIDO, null);
        }
        return new Verificacao(vereditoDeOcupacao(e, pedido, recursoId), recursoId);
    }

    // ------------------------------------------------------------------

    private Veredito vereditoDeOcupacao(Entrada e, Periodo pedido, UUID recurso) {
        if (e.bloqueios().stream().anyMatch(pedido::sobrepoe)) {
            return Veredito.BLOQUEADO;
        }
        if (e.ocupadosProfissional().stream().anyMatch(pedido::sobrepoe)) {
            return Veredito.OCUPADO;
        }
        if (recurso != null && e.ocupadosPorRecurso().getOrDefault(recurso, List.of()).stream()
                .anyMatch(pedido::sobrepoe)) {
            return Veredito.OCUPADO;
        }
        return Veredito.DISPONIVEL;
    }

    private Optional<BlocoGrade> blocoQueContem(Entrada e, Periodo pedido) {
        LocalDate dia = pedido.inicio().atZone(e.zona()).toLocalDate();
        return e.grade().stream()
                .filter(b -> b.dia() == dia.getDayOfWeek())
                .filter(b -> periodoDoBloco(dia, b, e.zona()).contem(pedido))
                .findFirst();
    }

    private List<Periodo> livresNoBloco(Periodo janela, BlocoGrade bloco, Entrada e) {
        List<Periodo> livres = Periodo.subtrair(List.of(janela), e.bloqueios());
        livres = Periodo.subtrair(livres, e.ocupadosProfissional());
        if (bloco.recursoId() != null) {
            livres = Periodo.subtrair(livres, e.ocupadosPorRecurso().getOrDefault(bloco.recursoId(), List.of()));
        }
        return livres;
    }

    private void fatiar(Periodo livre, UUID recurso, Entrada e, List<HorarioLivre> destino) {
        Instant inicio = livre.inicio();
        while (!inicio.plus(e.duracao()).isAfter(livre.fim())) {
            if (!inicio.isBefore(e.naoAntesDe()) && !inicio.isAfter(e.naoDepoisDe())) {
                destino.add(new HorarioLivre(Periodo.de(inicio, e.duracao()), recurso));
            }
            inicio = inicio.plus(e.passo());
        }
    }

    private static Periodo periodoDoBloco(LocalDate dia, BlocoGrade bloco, ZoneId zona) {
        Instant inicio = ZonedDateTime.of(dia, bloco.inicio(), zona).toInstant();
        Instant fim = ZonedDateTime.of(dia, bloco.fim(), zona).toInstant();
        return new Periodo(inicio, fim);
    }

    /** Blocos de grade sobrepostos gerariam slots repetidos; mantém o primeiro de cada início. */
    private static List<HorarioLivre> deduplicar(List<HorarioLivre> ordenados) {
        List<HorarioLivre> unicos = new ArrayList<>(ordenados.size());
        Instant ultimo = null;
        for (HorarioLivre h : ordenados) {
            if (!h.periodo().inicio().equals(ultimo)) {
                unicos.add(h);
                ultimo = h.periodo().inicio();
            }
        }
        return unicos;
    }
}
