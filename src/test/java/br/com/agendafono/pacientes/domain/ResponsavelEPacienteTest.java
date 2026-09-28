package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.ConsentimentoAusenteException;
import br.com.agendafono.pacientes.DadosInvalidosException;
import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.Eventos.ConsentimentoRegistrado;
import br.com.agendafono.pacientes.Eventos.PacienteCadastrado;
import br.com.agendafono.pacientes.Eventos.TitularAnonimizado;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TitularAnonimizadoException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponsavelEPacienteTest {

    private static final Instant AGORA = Instant.parse("2026-09-28T12:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 9, 28);

    private static Responsavel responsavel() {
        return Responsavel.novo(UUID.randomUUID(), UUID.randomUUID(), Telefone.digitado("(11) 99999-0000"),
                "  Maria   Silva ");
    }

    private static Responsavel comConsentimento() {
        Responsavel r = responsavel();
        r.registrarConsentimento("2026-09-v1", Canal.WHATSAPP, "wamid.ABC", AGORA);
        r.extrairEventos();
        r.extrairRegistrosNovos();
        return r;
    }

    @Test
    void normalizaEspacosDoNome() {
        assertThat(responsavel().nome()).isEqualTo("Maria Silva");
    }

    @Test
    void semConsentimentoNaoCadastraPaciente() {
        assertThatThrownBy(() -> responsavel().cadastrarPaciente(UUID.randomUUID(), "Pedro", null, null, HOJE))
                .isInstanceOf(ConsentimentoAusenteException.class);
    }

    @Test
    void consentimentoGeraHistoricoEEvento() {
        Responsavel r = responsavel();
        r.registrarConsentimento("2026-09-v1", Canal.WHATSAPP, "wamid.ABC", AGORA);
        assertThat(r.possuiConsentimento()).isTrue();
        assertThat(r.extrairRegistrosNovos()).singleElement()
                .satisfies(reg -> assertThat(reg.acao()).isEqualTo(RegistroConsentimento.Acao.CONCEDIDO));
        assertThat(r.extrairEventos()).singleElement().isInstanceOf(ConsentimentoRegistrado.class);
    }

    @Test
    void cadastraPacienteComIdade() {
        Responsavel r = comConsentimento();
        Paciente p = r.cadastrarPaciente(UUID.randomUUID(), "Pedro", LocalDate.of(2019, 3, 10), Demanda.LINGUAGEM,
                HOJE);
        assertThat(p.idade(HOJE)).isEqualTo(7);
        assertThat(p.menorDeIdade(HOJE)).isTrue();
        assertThat(p.ativo()).isTrue();
        assertThat(r.extrairEventos()).singleElement().isInstanceOf(PacienteCadastrado.class);
    }

    @Test
    void validaNomeENascimento() {
        Responsavel r = comConsentimento();
        assertThatThrownBy(() -> r.cadastrarPaciente(UUID.randomUUID(), " ", null, null, HOJE))
                .isInstanceOf(DadosInvalidosException.class);
        assertThatThrownBy(() -> r.cadastrarPaciente(UUID.randomUUID(), "Ana", HOJE.plusDays(1), null, HOJE))
                .isInstanceOf(DadosInvalidosException.class);
    }

    @Test
    void revogarImpedeNovosPacientes() {
        Responsavel r = comConsentimento();
        r.revogarConsentimento(Canal.PAINEL, null, AGORA);
        assertThat(r.possuiConsentimento()).isFalse();
        assertThat(r.extrairRegistrosNovos()).singleElement()
                .satisfies(reg -> assertThat(reg.acao()).isEqualTo(RegistroConsentimento.Acao.REVOGADO));
        assertThatThrownBy(() -> r.cadastrarPaciente(UUID.randomUUID(), "Pedro", null, null, HOJE))
                .isInstanceOf(ConsentimentoAusenteException.class);
    }

    @Test
    void anonimizacaoApagaDadosEBloqueiaAlteracoes() {
        Responsavel r = comConsentimento();
        Paciente p = r.cadastrarPaciente(UUID.randomUUID(), "Pedro", LocalDate.of(2019, 3, 10), Demanda.VOZ, HOJE);
        r.extrairEventos();

        r.anonimizar(AGORA);
        p.anonimizar(AGORA);

        assertThat(r.nome()).isNull();
        assertThat(r.telefone()).isNull();
        assertThat(r.possuiConsentimento()).isFalse();
        assertThat(r.extrairEventos()).singleElement().isInstanceOfSatisfying(TitularAnonimizado.class,
                e -> assertThat(e.telefoneAnterior()).isEqualTo("5511999990000"));
        assertThat(p.nome()).isEqualTo("Paciente anonimizado");
        assertThat(p.dataNascimento()).isNull();
        assertThat(p.demanda()).isNull();
        assertThat(p.ativo()).isFalse();
        assertThatThrownBy(p::reativar).isInstanceOf(TitularAnonimizadoException.class);
        assertThatThrownBy(() -> r.registrarConsentimento("v", Canal.PAINEL, "x", AGORA))
                .isInstanceOf(TitularAnonimizadoException.class);
    }
}
