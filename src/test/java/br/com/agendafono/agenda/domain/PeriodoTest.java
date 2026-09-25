package br.com.agendafono.agenda.domain;

import br.com.agendafono.agenda.Periodo;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodoTest {

    private static Periodo p(String inicio, String fim) {
        return new Periodo(Instant.parse("2026-10-05T" + inicio + ":00Z"), Instant.parse("2026-10-05T" + fim + ":00Z"));
    }

    @Test
    void recusaFimAntesOuIgualAoInicio() {
        assertThatThrownBy(() -> p("10:00", "10:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> p("10:00", "09:00")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void periodosAdjacentesNaoSeSobrepoem() {
        assertThat(p("10:00", "10:40").sobrepoe(p("10:40", "11:20"))).isFalse();
        assertThat(p("10:00", "10:41").sobrepoe(p("10:40", "11:20"))).isTrue();
    }

    @Test
    void subtraiNoMeioGerandoDuasPartes() {
        assertThat(p("08:00", "12:00").menos(p("09:00", "10:00")))
                .containsExactly(p("08:00", "09:00"), p("10:00", "12:00"));
    }

    @Test
    void subtraiVariosOcupados() {
        List<Periodo> livres = Periodo.subtrair(List.of(p("08:00", "12:00")),
                List.of(p("11:00", "13:00"), p("07:00", "08:30"), p("09:00", "09:40")));
        assertThat(livres).containsExactly(p("08:30", "09:00"), p("09:40", "11:00"));
    }

    @Test
    void contem() {
        assertThat(p("08:00", "12:00").contem(p("08:00", "08:40"))).isTrue();
        assertThat(p("08:00", "12:00").contem(p("11:40", "12:20"))).isFalse();
    }
}
