package br.com.agendafono.pacientes;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.compartilhado.seguranca.Papel;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP de pacientes, responsáveis e anexos. */
class PacienteApiIntegrationTest extends IntegracaoBase {

    private static final byte[] PDF = "%PDF-1.7\nteste".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    UUID clinica;
    String recepcao;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE clinica CASCADE");
        clinica = jdbc.queryForObject("INSERT INTO clinica (nome) VALUES ('Clínica') RETURNING id", UUID.class);
        recepcao = bearer(clinica, Papel.RECEPCAO);
    }

    private ResultActions cadastrar(String telefone, String nome, boolean consentimento) throws Exception {
        String corpo = """
                {"telefoneResponsavel": "%s", "nomeResponsavel": "Maria Silva", "nome": "%s",
                 "dataNascimento": "2019-03-10", "demanda": "LINGUAGEM", "consentimentoColetado": %s}
                """.formatted(telefone, nome, consentimento);
        return mvc.perform(post("/api/v1/pacientes")
                .header("Authorization", recepcao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    private String idCriado(ResultActions r) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void cadastroSemConsentimentoRetorna422() throws Exception {
        cadastrar("(11) 99999-0000", "Pedro", false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/erros/consentimento-ausente"));
    }

    @Test
    void telefoneInvalidoRetorna400() throws Exception {
        cadastrar("9999", "Pedro", true)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/erros/telefone-invalido"));
    }

    @Test
    void cadastraEListaComTelefoneMascarado() throws Exception {
        cadastrar("(11) 99999-0000", "Pedro Silva", true)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/pacientes/")))
                .andExpect(jsonPath("$.idade").isNumber());

        mvc.perform(get("/api/v1/pacientes").header("Authorization", recepcao).param("busca", "pedro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].telefoneMascarado").value("(11) 9****-0000"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("99999-0000"))));
    }

    @Test
    void fichaTrazResponsavelEAnexos() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));

        mvc.perform(multipart("/api/v1/pacientes/{id}/anexos", id)
                        .file(new MockMultipartFile("arquivo", "pedido.pdf", "application/pdf", PDF))
                        .param("tipo", "PEDIDO_MEDICO")
                        .header("Authorization", recepcao))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("application/pdf"));

        mvc.perform(get("/api/v1/pacientes/{id}", id).header("Authorization", recepcao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paciente.nome").value("Pedro"))
                .andExpect(jsonPath("$.responsavel.telefone").value("+5511999990000"))
                .andExpect(jsonPath("$.responsavel.consentimento.canal").value("PAINEL"))
                .andExpect(jsonPath("$.anexos[0].nomeArquivo").value("pedido.pdf"));
    }

    @Test
    void downloadDeAnexoSaiComoAttachmentSemCache() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));
        String anexoId = JsonPath.read(mvc.perform(multipart("/api/v1/pacientes/{id}/anexos", id)
                        .file(new MockMultipartFile("arquivo", "pedido.pdf", "application/pdf", PDF))
                        .header("Authorization", recepcao))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/api/v1/anexos/{id}/conteudo", anexoId).header("Authorization", recepcao))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void arquivoQueNaoEPdfNemImagemRetorna422() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));
        mvc.perform(multipart("/api/v1/pacientes/{id}/anexos", id)
                        .file(new MockMultipartFile("arquivo", "pedido.pdf", "application/pdf",
                                "MZ executavel".getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", recepcao))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/erros/anexo-invalido"));
    }

    @Test
    void exportacaoEAnonimizacao() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));
        String responsavelId = jdbc.queryForObject("SELECT responsavel_id::text FROM paciente WHERE id = ?::uuid",
                String.class, id);

        String admin = bearer(clinica, Papel.ADMIN);

        // Direitos do titular (LGPD) são exclusivos do ADMIN
        mvc.perform(get("/api/v1/responsaveis/{id}/exportacao", responsavelId)
                        .header("Authorization", recepcao))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/responsaveis/{id}/exportacao", responsavelId)
                        .header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.pacientes[0].nome").value("Pedro"))
                .andExpect(jsonPath("$.historicoConsentimento[0].acao").value("CONCEDIDO"));

        mvc.perform(post("/api/v1/responsaveis/{id}/anonimizacao", responsavelId)
                        .header("Authorization", admin))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/responsaveis/{id}/consentimento", responsavelId)
                        .header("Authorization", recepcao))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.type").value("/erros/titular-anonimizado"));
    }

    @Test
    void fonoConsultaMasNaoCadastra() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));
        String fono = bearer(clinica, Papel.FONO);

        mvc.perform(get("/api/v1/pacientes/{id}", id).header("Authorization", fono))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/pacientes/{id}/inativar", id).header("Authorization", fono))
                .andExpect(status().isForbidden());
    }

    @Test
    void outraClinicaNaoEnxergaOPaciente() throws Exception {
        String id = idCriado(cadastrar("(11) 99999-0000", "Pedro", true));

        mvc.perform(get("/api/v1/pacientes/{id}", id).header("Authorization", bearer(UUID.randomUUID(), Papel.ADMIN)))
                .andExpect(status().isNotFound());
    }
}
