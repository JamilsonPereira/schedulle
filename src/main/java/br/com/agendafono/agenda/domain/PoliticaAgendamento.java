package br.com.agendafono.agenda.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * Parâmetros de agendamento configuráveis por clínica (coluna {@code clinica.politica}).
 *
 * @param antecedenciaMinima     o bot só oferece horários a partir de agora + este valor (RN-02)
 * @param janelaMaxima           o bot só oferece horários até agora + este valor (RN-02)
 * @param passo                  espaçamento entre inícios de slots; nulo = a própria duração da sessão
 * @param ttlReserva             validade da pré-reserva do bot (RN-04)
 * @param antecedenciaAvisoFalta aviso de falta com pelo menos essa antecedência dá direito a reposição (RN-20)
 */
public record PoliticaAgendamento(
        Duration antecedenciaMinima,
        Duration janelaMaxima,
        Duration passo,
        Duration ttlReserva,
        Duration antecedenciaAvisoFalta) {

    public static final PoliticaAgendamento PADRAO = new PoliticaAgendamento(
            Duration.ofHours(12), Duration.ofDays(30), null, Duration.ofMinutes(5), Duration.ofHours(24));

    public PoliticaAgendamento {
        Objects.requireNonNull(antecedenciaMinima, "antecedenciaMinima");
        Objects.requireNonNull(janelaMaxima, "janelaMaxima");
        Objects.requireNonNull(ttlReserva, "ttlReserva");
        Objects.requireNonNull(antecedenciaAvisoFalta, "antecedenciaAvisoFalta");
        if (passo != null && (passo.isNegative() || passo.isZero())) {
            throw new IllegalArgumentException("O passo deve ser positivo");
        }
    }

    public Duration passoPara(Duration duracao) {
        return passo != null ? passo : duracao;
    }
}
