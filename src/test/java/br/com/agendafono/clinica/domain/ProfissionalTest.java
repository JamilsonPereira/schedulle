package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.GradeSobrepostaException;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.ValidacaoException;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfissionalTest {

    private static IntervaloGrade intervalo(DayOfWeek dia, String inicio, String fim) {
        return new IntervaloGrade(dia, LocalTime.parse(inicio), LocalTime.parse(fim), null);
    }

    private Profissional novo() {
        return Profissional.novo(UUID.randomUUID(), UUID.randomUUID(), "Dra. Paula", "CRFa 2-12345",
                Set.of(Subarea.values()[0]), null, null);
    }

    @Test
    void duracaoPadraoE40EValidaFaixa() {
        assertThat(novo().duracaoPadraoMin()).isEqualTo(40);
        assertThatThrownBy(() -> Profissional.novo(UUID.randomUUID(), UUID.randomUUID(), "Paula", null, Set.of(), 5,
                null)).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void gradeEOrdenadaEIntervalosEncostadosSaoPermitidos() {
        Profissional p = novo();
        p.definirGrade(List.of(intervalo(TUESDAY, "08:00", "12:00"), intervalo(MONDAY, "13:00", "18:00"),
                intervalo(MONDAY, "08:00", "13:00")));
        assertThat(p.grade()).extracting(IntervaloGrade::dia, IntervaloGrade::inicio).containsExactly(
                org.assertj.core.groups.Tuple.tuple(MONDAY, LocalTime.of(8, 0)),
                org.assertj.core.groups.Tuple.tuple(MONDAY, LocalTime.of(13, 0)),
                org.assertj.core.groups.Tuple.tuple(TUESDAY, LocalTime.of(8, 0)));
    }

    @Test
    void recusaSobreposicaoNoMesmoDia() {
        Profissional p = novo();
        assertThatThrownBy(() -> p.definirGrade(List.of(intervalo(MONDAY, "08:00", "12:00"),
                intervalo(MONDAY, "11:00", "14:00")))).isInstanceOf(GradeSobrepostaException.class);
    }

    @Test
    void intervaloComFimAntesDoInicioEInvalido() {
        assertThatThrownBy(() -> intervalo(MONDAY, "12:00", "08:00")).isInstanceOf(ValidacaoException.class);
    }
}
