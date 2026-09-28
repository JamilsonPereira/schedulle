package br.com.agendafono.pacientes;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.pacientes.Anexos.NovoAnexo;
import br.com.agendafono.pacientes.CadastroPacientes.AtualizarPaciente;
import br.com.agendafono.pacientes.CadastroPacientes.CadastrarPaciente;
import br.com.agendafono.pacientes.CadastroPacientes.CadastroPeloPainel;
import br.com.agendafono.pacientes.PacienteConsulta.Pesquisa;
import br.com.agendafono.pacientes.Views.AnexoView;
import br.com.agendafono.pacientes.Views.ExportacaoDados;
import br.com.agendafono.pacientes.Views.PacienteResumo;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Casos de uso de pacientes contra um Postgres real. */
class PacientesIntegrationTest extends IntegracaoBase {

    private static final byte[] PDF = "%PDF-1.7\nconteudo de teste".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    CadastroPacientes cadastro;

    @Autowired
    PacienteConsulta consulta;

    @Autowired
    DireitosDoTitular direitos;

    @Autowired
    Anexos anexos;

    @Autowired
    JdbcTemplate jdbc;

    UUID clinica;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE clinica CASCADE");
        clinica = jdbc.queryForObject("INSERT INTO clinica (nome) VALUES ('Clínica') RETURNING id", UUID.class);
    }

    private PacienteView cadastrarPeloPainel(String telefone, String nomePaciente) {
        return cadastro.cadastrarPeloPainel(new CadastroPeloPainel(clinica, Telefone.digitado(telefone),
                "Maria Silva", nomePaciente, LocalDate.of(2019, 3, 10), Demanda.LINGUAGEM, true, "teste"));
    }

    // ------------------------------------------------------------------ fluxo do bot

    @Test
    void botIdentificaResponsavelMesmoSemONonoDigito() {
        ResponsavelView criado = cadastro.identificarResponsavel(clinica, Telefone.doWhatsApp("5511999990000"));
        ResponsavelView deNovo = cadastro.identificarResponsavel(clinica, Telefone.doWhatsApp("551199990000"));

        assertThat(deNovo.id()).isEqualTo(criado.id());
        assertThat(criado.possuiConsentimento()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM responsavel", Integer.class)).isEqualTo(1);
    }

    @Test
    void pacienteSoEntraDepoisDoConsentimento() {
        ResponsavelView r = cadastro.identificarResponsavel(clinica, Telefone.doWhatsApp("5511999990000"));
        CadastrarPaciente comando = new CadastrarPaciente(clinica, r.id(), "Pedro", LocalDate.of(2019, 3, 10),
                Demanda.GAGUEIRA);

        assertThatThrownBy(() -> cadastro.cadastrarPaciente(comando)).isInstanceOf(ConsentimentoAusenteException.class);

        cadastro.registrarConsentimento(clinica, r.id(), "2026-09-v1", Canal.WHATSAPP, "wamid.HBgM");
        PacienteView p = cadastro.cadastrarPaciente(comando);

        assertThat(p.nome()).isEqualTo("Pedro");
        assertThat(p.idade()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT evidencia FROM consentimento WHERE responsavel_id = ?",
                String.class, r.id())).isEqualTo("wamid.HBgM");
    }

    // ------------------------------------------------------------------ painel

    @Test
    void painelCriaResponsavelConsentimentoEPacienteDeUmaVez() {
        PacienteView pedro = cadastrarPeloPainel("(11) 99999-0000", "Pedro Silva");
        PacienteView ana = cadastrarPeloPainel("11999990000", "Ana Silva");

        assertThat(ana.responsavelId()).isEqualTo(pedro.responsavelId());
        ResponsavelView r = consulta.responsavel(clinica, pedro.responsavelId()).orElseThrow();
        assertThat(r.consentimento().canal()).isEqualTo(Canal.PAINEL);
        assertThat(consulta.ficha(clinica, pedro.id()).outrosPacientesDoResponsavel())
                .extracting(PacienteView::nome).containsExactly("Ana Silva");
    }

    @Test
    void painelSemConsentimentoColetadoRecusa() {
        assertThatThrownBy(() -> cadastro.cadastrarPeloPainel(new CadastroPeloPainel(clinica,
                Telefone.digitado("(11) 98888-7777"), null, "Pedro", null, null, false, "teste")))
                .isInstanceOf(ConsentimentoAusenteException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM responsavel", Integer.class)).isZero();
    }

    @Test
    void pesquisaPorNomeTelefoneEPaginacao() {
        cadastrarPeloPainel("(11) 99999-0000", "Pedro Silva");
        cadastrarPeloPainel("(11) 99999-0000", "Ana Silva");
        cadastrarPeloPainel("(21) 98888-7777", "João Souza");

        Pagina<PacienteResumo> silva = consulta.pesquisar(clinica, new Pesquisa("silva", null, 0, 25));
        assertThat(silva.totalElementos()).isEqualTo(3); // "Maria Silva" é a responsável dos três
        Pagina<PacienteResumo> joao = consulta.pesquisar(clinica, new Pesquisa("souza", null, 0, 25));
        assertThat(joao.conteudo()).extracting(PacienteResumo::nome).containsExactly("João Souza");
        Pagina<PacienteResumo> porTelefone = consulta.pesquisar(clinica, new Pesquisa("8888-7777", null, 0, 25));
        assertThat(porTelefone.conteudo()).singleElement()
                .satisfies(l -> assertThat(l.telefoneMascarado()).isEqualTo("(21) 9****-7777"));

        Pagina<PacienteResumo> pagina2 = consulta.pesquisar(clinica, new Pesquisa(null, true, 1, 2));
        assertThat(pagina2.totalElementos()).isEqualTo(3);
        assertThat(pagina2.conteudo()).extracting(PacienteResumo::nome).containsExactly("Pedro Silva");
    }

    @Test
    void edicaoComVersaoDesatualizadaFalha() {
        PacienteView p = cadastrarPeloPainel("(11) 99999-0000", "Pedro");
        cadastro.atualizarPaciente(new AtualizarPaciente(clinica, p.id(), "Pedro Henrique", null, Demanda.VOZ, 0));

        assertThatThrownBy(() -> cadastro.atualizarPaciente(
                new AtualizarPaciente(clinica, p.id(), "Outro", null, null, 0)))
                .isInstanceOf(RegistroDesatualizadoException.class);
        assertThat(consulta.paciente(clinica, p.id()).orElseThrow().nome()).isEqualTo("Pedro Henrique");
    }

    @Test
    void outraClinicaNaoEnxergaNemUsaOPaciente() {
        PacienteView p = cadastrarPeloPainel("(11) 99999-0000", "Pedro");
        UUID outra = jdbc.queryForObject("INSERT INTO clinica (nome) VALUES ('Outra') RETURNING id", UUID.class);

        assertThat(consulta.paciente(outra, p.id())).isEmpty();
        assertThat(consulta.pesquisar(outra, new Pesquisa(null, null, 0, 25)).conteudo()).isEmpty();
        assertThatThrownBy(() -> anexos.anexar(new NovoAnexo(outra, p.id(), TipoAnexo.OUTRO, "x.pdf", Canal.PAINEL,
                PDF))).isInstanceOf(NaoEncontradoException.class);
    }

    // ------------------------------------------------------------------ anexos

    @Test
    void anexaAbreERemoveArquivo() {
        PacienteView p = cadastrarPeloPainel("(11) 99999-0000", "Pedro");

        AnexoView a = anexos.anexar(new NovoAnexo(clinica, p.id(), TipoAnexo.PEDIDO_MEDICO, "pedido.pdf",
                Canal.WHATSAPP, PDF));

        assertThat(anexos.listar(clinica, p.id())).extracting(AnexoView::id).containsExactly(a.id());
        assertThat(anexos.abrir(clinica, a.id()).bytes()).isEqualTo(PDF);

        anexos.remover(clinica, a.id());
        assertThat(anexos.listar(clinica, p.id())).isEmpty();
        assertThatThrownBy(() -> anexos.abrir(clinica, a.id())).isInstanceOf(NaoEncontradoException.class);
    }

    // ------------------------------------------------------------------ LGPD

    @Test
    void exportaEDepoisAnonimiza() {
        PacienteView p = cadastrarPeloPainel("(11) 99999-0000", "Pedro");
        AnexoView a = anexos.anexar(new NovoAnexo(clinica, p.id(), TipoAnexo.PEDIDO_MEDICO, "pedido.pdf",
                Canal.PAINEL, PDF));
        String chave = jdbc.queryForObject("SELECT chave_armazenamento FROM anexo WHERE id = ?", String.class, a.id());

        ExportacaoDados exportacao = direitos.exportar(clinica, p.responsavelId());
        assertThat(exportacao.responsavel().telefone()).isEqualTo("+5511999990000");
        assertThat(exportacao.pacientes()).extracting(PacienteView::nome).containsExactly("Pedro");
        assertThat(exportacao.historicoConsentimento()).hasSize(1);
        assertThat(exportacao.anexos()).hasSize(1);

        direitos.anonimizar(clinica, p.responsavelId());

        ResponsavelView r = consulta.responsavel(clinica, p.responsavelId()).orElseThrow();
        assertThat(r.anonimizado()).isTrue();
        assertThat(r.nome()).isNull();
        assertThat(r.telefone()).isNull();
        assertThat(consulta.paciente(clinica, p.id()).orElseThrow().nome()).isEqualTo("Paciente anonimizado");
        assertThat(consulta.pesquisar(clinica, new Pesquisa(null, null, 0, 25)).conteudo()).isEmpty();
        assertThat(anexos.listar(clinica, p.id())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM anexo WHERE chave_armazenamento = ?", Integer.class,
                chave)).isZero();
        // o histórico de consentimento é mantido como prova, sem dados pessoais
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consentimento WHERE responsavel_id = ?",
                Integer.class, p.responsavelId())).isEqualTo(1);
        // o mesmo telefone pode voltar a se cadastrar como um novo responsável
        assertThat(cadastro.identificarResponsavel(clinica, Telefone.digitado("(11) 99999-0000")).id())
                .isNotEqualTo(p.responsavelId());
    }
}
