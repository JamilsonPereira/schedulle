package br.com.agendafono.agenda.domain;

import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade.Entrada;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade.Veredito;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraDisponibilidadeTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 10, 5);
    private static final Duration QUARENTA = Duration.ofMinutes(40);
    private static final UUID CABINE = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    private final CalculadoraDisponibilidade calculadora = new CalculadoraDisponibilidade();

    private static Instant hora(String hhmm) {
        return ZonedDateTime.of(SEGUNDA, LocalTime.parse(hhmm), SP).toInstant();
    }

    private static Periodo p(String inicio, String fim) {
        return new Periodo(hora(inicio), hora(fim));
    }

    private static Entrada entrada(List<BlocoGrade> grade, List<Periodo> bloqueios, List<Periodo> ocupados,
                                   Map<UUID, List<Periodo>> porRecurso, Instant naoAntesDe) {
        return new Entrada(SP, SEGUNDA, SEGUNDA, grade, bloqueios, ocupados, porRecurso, QUARENTA, QUARENTA,
                naoAntesDe, naoAntesDe.plus(Duration.ofDays(30)));
    }

    private static List<BlocoGrade> manha() {
        return List.of(new BlocoGrade(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), null));
    }

    private static List<String> inicios(List<HorarioLivre> slots) {
        return slots.stream()
                .map(h -> h.periodo().inicio().atZone(SP).toLocalTime().toString())
                .toList();
    }

    @Test
    void fatiaAGradePelaDuracao() {
        var slots = calculadora.calcular(entrada(manha(), List.of(), List.of(), Map.of(), hora("00:00")));
        assertThat(inicios(slots)).containsExactly("08:00", "08:40", "09:20", "10:00", "10:40", "11:20");
    }

    @Test
    void sessaoOcupadaDeslocaOsSlotsSeguintes() {
        var slots = calculadora.calcular(entrada(manha(), List.of(), List.of(p("09:00", "09:40")), Map.of(),
                hora("00:00")));
        // 08:00 cabe; 08:40 invadiria 09:00; a partir de 09:40 recomeça o fatiamento
        assertThat(inicios(slots)).containsExactly("08:00", "09:40", "10:20", "11:00");
    }

    @Test
    void bloqueioRemoveHorarios() {
        var slots = calculadora.calcular(entrada(manha(), List.of(p("10:00", "12:00")), List.of(), Map.of(),
                hora("00:00")));
        assertThat(inicios(slots)).containsExactly("08:00", "08:40", "09:20");
    }

    @Test
    void respeitaAntecedenciaMinima() {
        var slots = calculadora.calcular(entrada(manha(), List.of(), List.of(), Map.of(), hora("10:00")));
        assertThat(inicios(slots)).containsExactly("10:00", "10:40", "11:20");
    }

    @Test
    void salaOcupadaPorOutroProfissionalTiraOHorario() {
        var grade = List.of(new BlocoGrade(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(10, 0), CABINE));
        var slots = calculadora.calcular(entrada(grade, List.of(), List.of(),
                Map.of(CABINE, List.of(p("08:00", "08:40"))), hora("00:00")));
        assertThat(inicios(slots)).containsExactly("08:40", "09:20");
        assertThat(slots).allMatch(h -> CABINE.equals(h.recursoId()));
    }

    @Test
    void outroDiaDaSemanaNaoTemHorario() {
        var terca = List.of(new BlocoGrade(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0), null));
        assertThat(calculadora.calcular(entrada(terca, List.of(), List.of(), Map.of(), hora("00:00")))).isEmpty();
    }

    @Test
    void nenhumSlotSobrepoeOcupadosOuBloqueios() {
        var ocupados = List.of(p("08:10", "08:50"), p("10:05", "10:30"));
        var bloqueios = List.of(p("11:15", "11:45"));
        var slots = calculadora.calcular(entrada(manha(), bloqueios, ocupados, Map.of(), hora("00:00")));
        assertThat(slots).isNotEmpty().allSatisfy(h -> {
            assertThat(ocupados).noneMatch(o -> o.sobrepoe(h.periodo()));
            assertThat(bloqueios).noneMatch(b -> b.sobrepoe(h.periodo()));
            assertThat(p("08:00", "12:00").contem(h.periodo())).isTrue();
        });
    }

    @Test
    void verificaHorarioExato() {
        var e = entrada(manha(), List.of(p("11:00", "12:00")), List.of(p("09:00", "09:40")), Map.of(),
                hora("08:30"));
        assertThat(calculadora.verificar(e, p("10:05", "10:45")).veredito()).isEqualTo(Veredito.DISPONIVEL);
        assertThat(calculadora.verificar(e, p("09:20", "10:00")).veredito()).isEqualTo(Veredito.OCUPADO);
        assertThat(calculadora.verificar(e, p("11:00", "11:40")).veredito()).isEqualTo(Veredito.BLOQUEADO);
        assertThat(calculadora.verificar(e, p("11:40", "12:20")).veredito()).isEqualTo(Veredito.FORA_DA_GRADE);
        assertThat(calculadora.verificar(e, p("08:00", "08:40")).veredito()).isEqualTo(Veredito.ANTES_DO_PERMITIDO);
    }

    @Test
    void encaixeForaDaGradeAindaRespeitaOcupacao() {
        var e = entrada(manha(), List.of(), List.of(p("13:00", "13:40")), Map.of(), hora("00:00"));
        assertThat(calculadora.verificarSemGrade(e, p("14:00", "14:40"), null).veredito())
                .isEqualTo(Veredito.DISPONIVEL);
        assertThat(calculadora.verificarSemGrade(e, p("13:20", "14:00"), null).veredito())
                .isEqualTo(Veredito.OCUPADO);
    }
}
