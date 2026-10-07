package br.com.agendafono.bot.adapter.out.modulos;

import br.com.agendafono.agenda.Disponibilidade;
import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.bot.application.port.ServicosDaClinica.BuscaDeHorarios;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.clinica.ProfissionalConsulta;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.PoliticaView;
import br.com.agendafono.clinica.Views.ProfissionalView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Escolha dos horários oferecidos pelo bot, com a agenda e a clínica falsas. */
class BuscaDeHorariosTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final UUID CLINICA = UUID.randomUUID();
    static final LocalDate HOJE = LocalDate.of(2026, 10, 7);

    final ProfissionalView ana = fono("Dra. Ana", Subarea.LINGUAGEM);
    final ProfissionalView bia = fono("Dra. Bia", Subarea.VOZ);
    final List<Disponibilidade.Consulta> consultas = new ArrayList<>();
    /** Livres por profissional: todo dia às 09:00, 09:40 e 10:20. */
    Map<UUID, List<LocalTime>> livres = Map.of(ana.id(), horas("09:00", "09:40", "10:20"),
            bia.id(), horas("09:00", "09:40", "10:20"));
    Integer janela = 30;

    @Test
    void doisPorDiaEmOrdemComIdsCurtos() {
        BuscaDeHorarios b = adapter().horarios(CLINICA, "LINGUAGEM", HOJE.plusDays(1), 5);
        assertThat(b.horarios().stream().map(HorarioOferta::id).toList()).containsExactly("h1", "h2", "h3", "h4", "h5");
        assertThat(b.horarios().stream().map(h -> h.inicio().atZone(SP).toLocalDateTime().toString()).toList())
                .containsExactly("2026-10-08T09:00", "2026-10-08T09:40", "2026-10-09T09:00", "2026-10-09T09:40",
                        "2026-10-10T09:00");
        assertThat(b.horarios().get(0).profissionalNome()).isEqualTo("Dra. Ana");
        assertThat(b.proximaBusca()).isEqualTo(LocalDate.of(2026, 10, 11));
        assertThat(consultas.stream().map(Disponibilidade.Consulta::profissionalId).distinct().toList())
                .containsExactly(ana.id()); // só quem atende a demanda
        assertThat(consultas.get(0).origem()).isEqualTo(Disponibilidade.Origem.BOT);
    }

    @Test
    void semFonoDaSubareaProcuraEntreTodos() {
        adapter().horarios(CLINICA, "DEGLUTICAO", HOJE, 5);
        assertThat(consultas.stream().map(Disponibilidade.Consulta::profissionalId).distinct().toList()).hasSize(2);
    }

    @Test
    void paraNaJanelaDaClinicaESemProximaBuscaQuandoFaltam() {
        janela = 2;
        BuscaDeHorarios b = adapter().horarios(CLINICA, "LINGUAGEM", HOJE, 5);
        assertThat(b.horarios()).hasSize(5); // hoje, amanhã e depois: 2 + 2 + 1
        assertThat(b.proximaBusca()).isNull(); // o dia seguinte ao último já passa da janela

        janela = 1;
        b = adapter().horarios(CLINICA, "LINGUAGEM", HOJE, 5);
        assertThat(b.horarios()).hasSize(4);
        assertThat(b.proximaBusca()).isNull();
        assertThat(consultas.get(consultas.size() - 1).ate()).isEqualTo(HOJE.plusDays(1));
    }

    @Test
    void consultaEmBlocosDeNoMaximo31Dias() {
        livres = Map.of(ana.id(), List.of(), bia.id(), List.of());
        janela = 70;
        BuscaDeHorarios b = adapter().horarios(CLINICA, "LINGUAGEM", HOJE, 5);
        assertThat(b.horarios()).isEmpty();
        assertThat(consultas).hasSize(3);
        for (Disponibilidade.Consulta c : consultas) {
            assertThat(c.de().plusDays(Disponibilidade.Consulta.MAX_DIAS).isBefore(c.ate())).isFalse();
        }
        assertThat(consultas.get(2).ate()).isEqualTo(HOJE.plusDays(70));
    }

    // ------------------------------------------------------------------

    ServicosDaClinicaAdapter adapter() {
        consultas.clear();
        Disponibilidade disponibilidade = c -> {
            consultas.add(c);
            List<HorarioLivre> r = new ArrayList<>();
            for (LocalDate d = c.de(); !d.isAfter(c.ate()); d = d.plusDays(1)) {
                for (LocalTime t : livres.get(c.profissionalId())) {
                    Instant i = d.atTime(t).atZone(SP).toInstant();
                    r.add(new HorarioLivre(new Periodo(i, i.plus(Duration.ofMinutes(40))), null));
                }
            }
            return r;
        };
        ProfissionalConsulta profissionais = new ProfissionalConsulta() {
            public Optional<ProfissionalView> profissional(UUID clinicaId, UUID id) {
                return Optional.empty();
            }

            public List<ProfissionalView> ativos(UUID clinicaId, Subarea subarea) {
                return List.of(ana, bia).stream().filter(p -> subarea == null || p.subareas().contains(subarea)).toList();
            }
        };
        Clock relogio = Clock.fixed(HOJE.atTime(10, 0).atZone(SP).toInstant(), ZoneOffset.UTC);
        return new ServicosDaClinicaAdapter(
                id -> Optional.of(new ClinicaView(CLINICA, "Clínica", "America/Sao_Paulo",
                        new PoliticaView(null, janela, null, null, null), 0)),
                profissionais, null, null, null, disponibilidade, null, "v1", relogio);
    }

    static ProfissionalView fono(String nome, Subarea subarea) {
        return new ProfissionalView(UUID.randomUUID(), nome, null, Set.of(subarea), 40, null, true, List.of(), 0);
    }

    static List<LocalTime> horas(String... hs) {
        return java.util.Arrays.stream(hs).map(LocalTime::parse).toList();
    }
}
