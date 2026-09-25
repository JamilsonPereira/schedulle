package br.com.agendafono.agenda.domain;

import br.com.agendafono.agenda.Eventos.FaltaAvisada;
import br.com.agendafono.agenda.Eventos.SessaoAgendada;
import br.com.agendafono.agenda.Eventos.SessaoCancelada;
import br.com.agendafono.agenda.Eventos.SessaoRemarcada;
import br.com.agendafono.agenda.Eventos.VagaLiberada;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.ReservaExpiradaException;
import br.com.agendafono.agenda.StatusSessao;
import br.com.agendafono.agenda.TipoSessao;
import br.com.agendafono.agenda.TransicaoInvalidaException;
import br.com.agendafono.agenda.VersaoDesatualizadaException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessaoTest {

    private static final Instant AGORA = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-10-05T13:00:00Z");
    private static final Periodo PERIODO = Periodo.de(INICIO, Duration.ofMinutes(40));
    private static final Duration AVISO_24H = Duration.ofHours(24);

    private static Sessao agendada() {
        Sessao s = Sessao.agendar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, TipoSessao.TERAPIA, PERIODO, AGORA);
        s.extrairEventos();
        return s;
    }

    private static Sessao reservada() {
        return Sessao.reservar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, TipoSessao.AVALIACAO, PERIODO, AGORA, Duration.ofMinutes(5));
    }

    @Test
    void agendarGeraSessaoAgendada() {
        Sessao s = Sessao.agendar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, null, TipoSessao.TERAPIA, PERIODO, AGORA);
        assertThat(s.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(s.extrairEventos()).singleElement().isInstanceOf(SessaoAgendada.class);
    }

    @Test
    void naoAgendaNoPassado() {
        assertThatThrownBy(() -> Sessao.agendar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, null, TipoSessao.TERAPIA, PERIODO, INICIO.plusSeconds(1)))
                .isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void reservaConfirmadaATempoViraAgendada() {
        Sessao s = reservada();
        assertThat(s.expiraEm()).isEqualTo(AGORA.plus(Duration.ofMinutes(5)));
        s.confirmarReserva(AGORA.plus(Duration.ofMinutes(4)));
        assertThat(s.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(s.expiraEm()).isNull();
        assertThat(s.extrairEventos()).singleElement().isInstanceOf(SessaoAgendada.class);
    }

    @Test
    void reservaVencidaNaoPodeSerConfirmada() {
        Sessao s = reservada();
        assertThatThrownBy(() -> s.confirmarReserva(AGORA.plus(Duration.ofMinutes(5))))
                .isInstanceOf(ReservaExpiradaException.class);
    }

    @Test
    void confirmarReservaDuasVezesNaoFalha() {
        Sessao s = reservada();
        s.confirmarReserva(AGORA);
        s.confirmarReserva(AGORA);
        assertThat(s.status()).isEqualTo(StatusSessao.AGENDADA);
    }

    @Test
    void avisoComAntecedenciaDaDireitoAReposicaoELiberaVaga() {
        Sessao s = agendada();
        boolean direito = s.avisarFalta(INICIO.minus(Duration.ofHours(25)), AVISO_24H);
        assertThat(direito).isTrue();
        assertThat(s.status()).isEqualTo(StatusSessao.FALTA_AVISADA);
        assertThat(s.extrairEventos()).hasSize(2)
                .anySatisfy(e -> assertThat(e).isInstanceOfSatisfying(FaltaAvisada.class,
                        f -> assertThat(f.direitoReposicao()).isTrue()))
                .anySatisfy(e -> assertThat(e).isInstanceOf(VagaLiberada.class));
    }

    @Test
    void avisoEmCimaDaHoraNaoDaDireitoAReposicao() {
        Sessao s = agendada();
        assertThat(s.avisarFalta(INICIO.minus(Duration.ofHours(2)), AVISO_24H)).isFalse();
    }

    @Test
    void naoAvisaFaltaDepoisQueASessaoComecou() {
        Sessao s = agendada();
        assertThatThrownBy(() -> s.avisarFalta(INICIO, AVISO_24H)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void presencaSoAPartirDoInicio() {
        Sessao s = agendada();
        assertThatThrownBy(() -> s.registrarPresenca(StatusSessao.ATENDIDA, INICIO.minusSeconds(1)))
                .isInstanceOf(TransicaoInvalidaException.class);
        s.registrarPresenca(StatusSessao.ATENDIDA, INICIO.plusSeconds(60));
        assertThat(s.status()).isEqualTo(StatusSessao.ATENDIDA);
    }

    @Test
    void estadoFinalNaoMuda() {
        Sessao s = agendada();
        s.cancelar(AGORA);
        assertThatThrownBy(() -> s.confirmarPresenca()).isInstanceOf(TransicaoInvalidaException.class);
        assertThatThrownBy(() -> s.cancelar(AGORA)).isInstanceOf(TransicaoInvalidaException.class);
    }

    @Test
    void cancelarFuturaLiberaVaga() {
        Sessao s = agendada();
        s.cancelar(AGORA);
        assertThat(s.extrairEventos())
                .anySatisfy(e -> assertThat(e).isInstanceOf(SessaoCancelada.class))
                .anySatisfy(e -> assertThat(e).isInstanceOf(VagaLiberada.class));
    }

    @Test
    void cancelarReservaNaoLiberaVaga() {
        Sessao s = reservada();
        s.cancelar(AGORA);
        assertThat(s.extrairEventos()).noneMatch(VagaLiberada.class::isInstance);
    }

    @Test
    void remarcarMantemDuracaoEVoltaParaAgendada() {
        Sessao s = agendada();
        s.confirmarPresenca();
        s.extrairEventos();
        Instant novo = INICIO.plus(Duration.ofDays(1));
        s.remarcar(novo, null, AGORA);
        assertThat(s.periodo()).isEqualTo(Periodo.de(novo, Duration.ofMinutes(40)));
        assertThat(s.status()).isEqualTo(StatusSessao.AGENDADA);
        assertThat(s.extrairEventos())
                .anySatisfy(e -> assertThat(e).isInstanceOf(SessaoRemarcada.class))
                .anySatisfy(e -> assertThat(e).isInstanceOf(VagaLiberada.class));
    }

    @Test
    void versaoEsperadaDiferenteFalha() {
        Sessao s = agendada();
        s.exigirVersao(null);
        s.exigirVersao(0);
        assertThatThrownBy(() -> s.exigirVersao(1)).isInstanceOf(VersaoDesatualizadaException.class);
    }
}
