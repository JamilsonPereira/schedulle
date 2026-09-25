package br.com.agendafono.agenda;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.agenda.Agendamento.Agendar;
import br.com.agendafono.agenda.Agendamento.Remarcar;
import br.com.agendafono.agenda.Agendamento.Reservar;
import br.com.agendafono.agenda.Disponibilidade.Consulta;
import br.com.agendafono.agenda.Disponibilidade.Origem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Casos de uso da agenda contra um Postgres real (SDD, seções 6.1 a 6.4). */
class AgendamentoIntegrationTest extends IntegracaoBase {

    @Autowired
    Agendamento agendamento;

    @Autowired
    Disponibilidade disponibilidade;

    @Autowired
    Presenca presenca;

    @Autowired
    JdbcTemplate jdbc;

    AgendaCenario c;

    @BeforeEach
    void setUp() {
        c = new AgendaCenario(jdbc).criar();
    }

    private List<Instant> livresNaSegunda() {
        return disponibilidade.consultar(new Consulta(c.clinica, c.fono, c.segunda, c.segunda, null, Origem.PAINEL))
                .stream().map(h -> h.periodo().inicio()).toList();
    }

    private SessaoView agendar(String hhmm) {
        return agendamento.agendar(new Agendar(c.clinica, c.paciente, c.fono, TipoSessao.TERAPIA, c.as(hhmm),
                null, null, false));
    }

    // ------------------------------------------------------------------ disponibilidade

    @Test
    void disponibilidadeFatiaAGradePelaDuracaoDoProfissional() {
        assertThat(livresNaSegunda()).containsExactly(
                c.as("08:00"), c.as("08:40"), c.as("09:20"), c.as("10:00"), c.as("10:40"), c.as("11:20"));
    }

    @Test
    void disponibilidadeDescontaSessoesEBloqueios() {
        agendar("09:20");
        c.bloqueio("11:00", "12:00");
        assertThat(livresNaSegunda()).containsExactly(c.as("08:00"), c.as("08:40"), c.as("10:00"));
    }

    @Test
    void profissionalDeOutraClinicaNaoEEncontrado() {
        UUID outra = c.novaClinica();
        assertThatThrownBy(() -> disponibilidade.consultar(
                new Consulta(outra, c.fono, c.segunda, c.segunda, null, Origem.PAINEL)))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    // ------------------------------------------------------------------ reserva do bot

    @Test
    void reservaDoBotEConfirmacao() {
        SessaoView reserva = agendamento.reservar(
                new Reservar(c.clinica, c.paciente, c.fono, TipoSessao.AVALIACAO, c.as("08:40"), null));

        assertThat(reserva.status()).isEqualTo(StatusSessao.RESERVADA);
        assertThat(reserva.expiraEm()).isBetween(Instant.now().plus(Duration.ofMinutes(4)),
                Instant.now().plus(Duration.ofMinutes(6)));
        assertThat(livresNaSegunda()).doesNotContain(c.as("08:40"));

        SessaoView confirmada = agendamento.confirmarReserva(c.clinica, reserva.id());
        assertThat(confirmada.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(confirmada.expiraEm()).isNull();
        assertThat(confirmada.versao()).isEqualTo(1);
    }

    @Test
    void horarioOcupadoTrazAteTresAlternativas() {
        agendar("08:40");

        assertThatThrownBy(() -> agendamento.reservar(
                new Reservar(c.clinica, c.paciente, c.fono, TipoSessao.AVALIACAO, c.as("08:40"), null)))
                .isInstanceOfSatisfying(HorarioIndisponivelException.class, e -> {
                    assertThat(e.alternativas()).hasSize(3);
                    assertThat(e.alternativas()).extracting(h -> h.periodo().inicio())
                            .contains(c.as("08:00"), c.as("09:20"))
                            .doesNotContain(c.as("08:40"));
                });
    }

    @Test
    void reservaVencidaNaoSeguraOHorario() {
        UUID vencida = c.reservaVencida("10:00", "10:40");

        SessaoView nova = agendar("10:00");

        assertThat(nova.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(c.statusNoBanco(vencida)).isEqualTo("CANCELADA");
    }

    @Test
    void vinteReservasSimultaneasNoMesmoHorarioSoUmaVence() throws Exception {
        int tentativas = 20;
        List<UUID> pacientes = new ArrayList<>();
        for (int i = 0; i < tentativas; i++) {
            pacientes.add(c.novoPaciente("+55119888800" + String.format("%02d", i)));
        }
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(tentativas)) {
            for (UUID paciente : pacientes) {
                Callable<Boolean> tentativa = () -> {
                    largada.await();
                    try {
                        agendamento.reservar(new Reservar(c.clinica, paciente, c.fono, TipoSessao.AVALIACAO,
                                c.as("10:00"), null));
                        return true;
                    } catch (HorarioIndisponivelException e) {
                        return false;
                    }
                };
                resultados.add(pool.submit(tentativa));
            }
            largada.countDown();

            long sucessos = 0;
            for (Future<Boolean> r : resultados) {
                if (r.get()) {
                    sucessos++;
                }
            }
            assertThat(sucessos).isEqualTo(1);
        }

        Integer ativas = jdbc.queryForObject("""
                SELECT count(*) FROM sessao
                 WHERE profissional_id = ? AND status IN ('RESERVADA', 'AGENDADA', 'CONFIRMADA')""",
                Integer.class, c.fono);
        assertThat(ativas).isEqualTo(1);
    }

    // ------------------------------------------------------------------ painel

    @Test
    void painelNaoAgendaForaDaGradeSemEncaixe() {
        assertThatThrownBy(() -> agendar("13:00"))
                .isInstanceOfSatisfying(HorarioForaDaAgendaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(HorarioForaDaAgendaException.Motivo.FORA_DA_GRADE));

        SessaoView encaixe = agendamento.agendar(new Agendar(c.clinica, c.paciente, c.fono, TipoSessao.TERAPIA,
                c.as("13:00"), null, null, true));
        assertThat(encaixe.status()).isEqualTo(StatusSessao.AGENDADA);
    }

    @Test
    void painelNaoAgendaEmHorarioBloqueado() {
        c.bloqueio("08:00", "12:00");
        assertThatThrownBy(() -> agendar("08:00"))
                .isInstanceOfSatisfying(HorarioForaDaAgendaException.class,
                        e -> assertThat(e.motivo()).isEqualTo(HorarioForaDaAgendaException.Motivo.BLOQUEADO));
    }

    @Test
    void remarcacaoComVersaoDesatualizadaFalha() {
        SessaoView s = agendar("08:00");

        SessaoView remarcada = agendamento.remarcar(
                new Remarcar(c.clinica, s.id(), c.as("10:00"), null, false, s.versao()));
        assertThat(remarcada.inicio()).isEqualTo(c.as("10:00"));
        assertThat(remarcada.fim()).isEqualTo(c.as("10:40"));
        assertThat(remarcada.versao()).isEqualTo(s.versao() + 1);

        assertThatThrownBy(() -> agendamento.remarcar(
                new Remarcar(c.clinica, s.id(), c.as("11:20"), null, false, s.versao())))
                .isInstanceOf(VersaoDesatualizadaException.class);
    }

    @Test
    void remarcarParaHorarioQueSobrepoeOProprioHorarioAntigoFunciona() {
        SessaoView s = agendar("08:00");
        SessaoView remarcada = agendamento.remarcar(
                new Remarcar(c.clinica, s.id(), c.as("08:20"), null, false, null));
        assertThat(remarcada.inicio()).isEqualTo(c.as("08:20"));
    }

    @Test
    void avisoDeFaltaLiberaOHorario() {
        SessaoView s = agendar("08:00");
        assertThat(livresNaSegunda()).doesNotContain(c.as("08:00"));

        SessaoView faltou = agendamento.avisarFalta(c.clinica, s.id(), null);

        assertThat(faltou.status()).isEqualTo(StatusSessao.FALTA_AVISADA);
        assertThat(livresNaSegunda()).contains(c.as("08:00"));
    }

    @Test
    void presencaAntesDoInicioNaoEPermitida() {
        SessaoView s = agendar("08:00");
        assertThatThrownBy(() -> presenca.registrar(c.clinica, s.id(), Presenca.Resultado.ATENDIDA, null))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void outraClinicaNaoEnxergaASessao() {
        SessaoView s = agendar("08:00");
        UUID outra = c.novaClinica();
        assertThat(agendamento.buscar(outra, s.id())).isEmpty();
        assertThatThrownBy(() -> agendamento.cancelar(outra, s.id(), null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
