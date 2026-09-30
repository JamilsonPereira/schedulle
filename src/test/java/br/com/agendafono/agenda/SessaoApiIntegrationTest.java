package br.com.agendafono.agenda;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrato HTTP da agenda: status, Problem Details e regras de perfil (JWT). */
class SessaoApiIntegrationTest extends IntegracaoBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    AgendaCenario c;
    String recepcao;

    @BeforeEach
    void setUp() {
        c = new AgendaCenario(jdbc).criar();
        recepcao = bearer(c.clinica, Papel.RECEPCAO);
    }

    private ResultActions criarSessao(String hhmm) throws Exception {
        String corpo = """
                {"pacienteId": "%s", "profissionalId": "%s", "tipo": "TERAPIA", "inicio": "%s"}
                """.formatted(c.paciente, c.fono, c.as(hhmm));
        return mvc.perform(post("/api/v1/sessoes")
                .header("Authorization", recepcao)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo));
    }

    @Test
    void criaSessaoERetorna201ComLocation() throws Exception {
        criarSessao("08:00")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/sessoes/")))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.inicio").value(c.as("08:00").toString()))
                .andExpect(jsonPath("$.versao").value(0));
    }

    @Test
    void conflitoRetorna409ComProblemDetailsEAlternativas() throws Exception {
        criarSessao("08:40").andExpect(status().isCreated());

        criarSessao("08:40")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("/erros/horario-indisponivel"))
                .andExpect(jsonPath("$.alternativas", hasSize(3)))
                .andExpect(jsonPath("$.alternativas[0].inicio").exists());
    }

    @Test
    void horarioForaDaGradeRetorna422ComMotivo() throws Exception {
        criarSessao("13:00")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/erros/horario-fora-da-agenda"))
                .andExpect(jsonPath("$.motivo").value("FORA_DA_GRADE"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/sessoes")
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void headerAntigoDeClinicaNaoAutentica() throws Exception {
        mvc.perform(get("/api/v1/sessoes")
                        .header("X-Clinica-Id", c.clinica.toString())
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeOutraClinicaNaoEnxergaASessao() throws Exception {
        String location = criarSessao("08:00").andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(get("/api/v1/sessoes/{id}", id)
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void fonoNaoAgenda() throws Exception {
        String fono = bearer(c.clinica, UUID.randomUUID(), c.fono, Papel.FONO);
        mvc.perform(post("/api/v1/sessoes")
                        .header("Authorization", fono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": "%s", "profissionalId": "%s", "tipo": "TERAPIA", "inicio": "%s"}
                                """.formatted(c.paciente, c.fono, c.as("08:00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void fonoSoVeERegistraAPropriaAgenda() throws Exception {
        UUID outroFono = c.novoProfissional();
        c.grade(outroFono, java.time.DayOfWeek.MONDAY, "08:00", "12:00", null);
        String minha = idDe(criarSessao("08:00"));
        String doOutro = idDe(mvc.perform(post("/api/v1/sessoes")
                .header("Authorization", recepcao)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pacienteId": "%s", "profissionalId": "%s", "tipo": "TERAPIA", "inicio": "%s"}
                        """.formatted(c.novoPaciente("+5511988880000"), outroFono, c.as("09:00")))));
        String fono = bearer(c.clinica, UUID.randomUUID(), c.fono, Papel.FONO);

        // Lista só as próprias, mesmo pedindo a agenda do outro fono
        mvc.perform(get("/api/v1/sessoes")
                        .header("Authorization", fono)
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString())
                        .param("profissionalId", outroFono.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(minha));

        mvc.perform(get("/api/v1/sessoes/{id}", doOutro).header("Authorization", fono))
                .andExpect(status().isNotFound());

        // Não cancela; registra atendimento só na própria
        mvc.perform(patch("/api/v1/sessoes/{id}/status", minha)
                        .header("Authorization", fono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"CANCELAR\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/sessoes/{id}/status", doOutro)
                        .header("Authorization", fono)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"REGISTRAR_ATENDIMENTO\"}"))
                .andExpect(status().isNotFound());
    }

    private static String idDe(ResultActions r) throws Exception {
        String location = r.andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Test
    void consultaDisponibilidade() throws Exception {
        mvc.perform(get("/api/v1/disponibilidade")
                        .header("Authorization", recepcao)
                        .param("profissionalId", c.fono.toString())
                        .param("de", c.segunda.toString())
                        .param("ate", c.segunda.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].inicio").value(c.as("08:00").toString()));
    }

    @Test
    void cancelaPeloPatchDeStatus() throws Exception {
        String location = criarSessao("08:00").andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(patch("/api/v1/sessoes/{id}/status", id)
                        .header("Authorization", recepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"CANCELAR\", \"versao\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"))
                .andExpect(jsonPath("$.versao").value(1));

        mvc.perform(patch("/api/v1/sessoes/{id}/status", id)
                        .header("Authorization", recepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"CANCELAR\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("/erros/transicao-invalida"));
    }

    @Test
    void listaSessoesDoDia() throws Exception {
        criarSessao("08:00").andExpect(status().isCreated());
        criarSessao("10:00").andExpect(status().isCreated());

        mvc.perform(get("/api/v1/sessoes")
                        .header("Authorization", recepcao)
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString())
                        .param("profissionalId", c.fono.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].inicio").value(c.as("08:00").toString()));
    }
}
