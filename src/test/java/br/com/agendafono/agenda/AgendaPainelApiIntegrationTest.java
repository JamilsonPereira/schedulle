package br.com.agendafono.agenda;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/agenda/sessoes: leitura do painel web, com o nome do paciente e as regras de perfil. */
class AgendaPainelApiIntegrationTest extends IntegracaoBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    AgendaCenario c;
    String recepcao;

    @BeforeEach
    void setUp() throws Exception {
        c = new AgendaCenario(jdbc).criar();
        recepcao = bearer(c.clinica, Papel.RECEPCAO);
        agendar(c.paciente, c.fono, "08:00");
        agendar(c.paciente, c.fono, "10:00");
    }

    private void agendar(UUID paciente, UUID profissional, String hhmm) throws Exception {
        mvc.perform(post("/api/v1/sessoes")
                        .header("Authorization", recepcao)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pacienteId": "%s", "profissionalId": "%s", "tipo": "TERAPIA", "inicio": "%s"}
                                """.formatted(paciente, profissional, c.as(hhmm))))
                .andExpect(status().isCreated());
    }

    @Test
    void trazONomeDoPaciente() throws Exception {
        mvc.perform(get("/api/v1/agenda/sessoes")
                        .header("Authorization", recepcao)
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].inicio").value(c.as("08:00").toString()))
                .andExpect(jsonPath("$[0].pacienteNome").value("Paciente"))
                .andExpect(jsonPath("$[0].versao").value(0));
    }

    @Test
    void historicoDoPacienteMaisRecentesPrimeiro() throws Exception {
        mvc.perform(get("/api/v1/agenda/sessoes")
                        .header("Authorization", recepcao)
                        .param("de", c.as("00:00").minus(Duration.ofDays(30)).toString())
                        .param("ate", c.as("23:59").toString())
                        .param("pacienteId", c.paciente.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].inicio").value(c.as("10:00").toString()));
    }

    @Test
    void fonoSoVeAPropriaAgenda() throws Exception {
        UUID outroFono = c.novoProfissional();
        c.grade(outroFono, DayOfWeek.MONDAY, "08:00", "12:00", null);
        agendar(c.novoPaciente("+5511988880000"), outroFono, "09:00");
        String fono = bearer(c.clinica, UUID.randomUUID(), c.fono, Papel.FONO);

        mvc.perform(get("/api/v1/agenda/sessoes")
                        .header("Authorization", fono)
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString())
                        .param("profissionalId", outroFono.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void intervaloGrandeDemaisRetorna400() throws Exception {
        mvc.perform(get("/api/v1/agenda/sessoes")
                        .header("Authorization", recepcao)
                        .param("de", c.as("00:00").minus(Duration.ofDays(500)).toString())
                        .param("ate", c.as("23:59").toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void outraClinicaNaoVeNada() throws Exception {
        mvc.perform(get("/api/v1/agenda/sessoes")
                        .header("Authorization", bearer(UUID.randomUUID(), Papel.ADMIN))
                        .param("de", c.as("00:00").toString())
                        .param("ate", c.as("23:59").toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
