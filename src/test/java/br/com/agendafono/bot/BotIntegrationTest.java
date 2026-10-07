package br.com.agendafono.bot;

import br.com.agendafono.IntegracaoBase;
import br.com.agendafono.bot.domain.MensagensBot;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roteiro "oi → avaliação agendada" com banco real: o bot cadastra o responsável e o paciente, reserva e confirma
 * na agenda, grava a conversa e enfileira as respostas em evento_saida (com botões e listas).
 */
class BotIntegrationTest extends IntegracaoBase {

    static final String TELEFONE = "5511988887777";

    @Autowired
    Bot bot;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    UUID clinica;
    int wamid;

    @BeforeEach
    void cenario() {
        clinica = jdbc.queryForObject("INSERT INTO clinica (nome) VALUES ('Clínica do Bot') RETURNING id", UUID.class);
        UUID fono = jdbc.queryForObject("INSERT INTO profissional (clinica_id, nome, subareas, duracao_padrao_min)"
                + " VALUES (?, 'Dra. Ana', '{LINGUAGEM}', 40) RETURNING id", UUID.class, clinica);
        for (int dia = 1; dia <= 5; dia++) {
            jdbc.update("INSERT INTO grade_semanal (profissional_id, dia_semana, hora_inicio, hora_fim)"
                    + " VALUES (?, ?, '08:00'::time, '12:00'::time)", fono, dia);
        }
    }

    @Test
    void doOiAteAAvaliacaoAgendada() {
        texto("oi");
        clicar(MensagensBot.ACEITO);
        clicar(MensagensBot.AGENDAR);
        texto("Maria Souza");
        texto("10/03/2019");
        clicar("demanda:LINGUAGEM");
        assertThat(estado()).isEqualTo("ESCOLHER_HORARIO");
        clicar("h1");
        assertThat(estado()).isEqualTo("CONFIRMAR");
        clicar(MensagensBot.CONFIRMAR);

        assertThat(estado()).isEqualTo("MENU");
        Map<String, Object> sessao = jdbc.queryForMap("SELECT s.status, s.tipo, p.nome, p.demanda"
                + " FROM sessao s JOIN paciente p ON p.id = s.paciente_id WHERE s.clinica_id = ?", clinica);
        assertThat(sessao.get("status")).isEqualTo("AGENDADA");
        assertThat(sessao.get("tipo")).isEqualTo("AVALIACAO");
        assertThat(sessao.get("nome")).isEqualTo("Maria Souza");
        assertThat(sessao.get("demanda")).isEqualTo("LINGUAGEM");

        assertThat(jdbc.queryForObject("SELECT consentimento_canal FROM responsavel WHERE clinica_id = ?",
                String.class, clinica)).isEqualTo("WHATSAPP");

        List<Map<String, Object>> saida = jdbc.queryForList("SELECT tipo, texto, payload ->> 'type' AS interativo"
                + " FROM evento_saida WHERE clinica_id = ? ORDER BY criado_em", clinica);
        assertThat(saida.get(0).get("interativo")).isEqualTo("button"); // consentimento
        assertThat(saida).anyMatch(m -> "list".equals(m.get("interativo"))); // horários
        assertThat((String) saida.get(saida.size() - 1).get("texto")).startsWith("Pronto! A avaliação de Maria");
        assertThat(saida).allMatch(m -> "TEXT".equals(m.get("tipo")) == (m.get("interativo") == null));
    }

    @Test
    void transbordoApareceNaFilaDaRecepcaoEVoltaAoBot() throws Exception {
        texto("oi");
        texto("atendente");
        assertThat(jdbc.queryForObject("SELECT modo FROM conversa WHERE clinica_id = ?", String.class, clinica))
                .isEqualTo("HUMANO");
        int antes = enviadas();
        texto("alguém aí?");
        assertThat(enviadas()).isEqualTo(antes); // em atendimento humano o bot fica quieto

        String recepcao = bearer(clinica, Papel.RECEPCAO);
        mvc.perform(get("/api/v1/conversas/em-atendimento").header("Authorization", recepcao))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].telefoneMascarado").isNotEmpty());
        mvc.perform(get("/api/v1/conversas/em-atendimento").header("Authorization", bearer(clinica, Papel.FONO)))
                .andExpect(status().isForbidden());

        UUID conversa = jdbc.queryForObject("SELECT id FROM conversa WHERE clinica_id = ?", UUID.class, clinica);
        mvc.perform(post("/api/v1/conversas/{id}/devolver-ao-bot", conversa).header("Authorization", recepcao))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/conversas/{id}/devolver-ao-bot", UUID.randomUUID()).header("Authorization", recepcao))
                .andExpect(status().isNotFound());

        texto("oi");
        assertThat(enviadas()).isGreaterThan(antes);
    }

    // ------------------------------------------------------------------

    void texto(String texto) {
        bot.processar(new MensagemDoContato(clinica, "PNID", TELEFONE, "wamid.bot." + (++wamid), "TEXT", texto,
                "{\"type\":\"text\",\"text\":{\"body\":\"" + texto + "\"}}", Instant.now()));
    }

    void clicar(String id) {
        bot.processar(new MensagemDoContato(clinica, "PNID", TELEFONE, "wamid.bot." + (++wamid), "INTERACTIVE", null,
                "{\"type\":\"interactive\",\"interactive\":{\"type\":\"button_reply\",\"button_reply\":{\"id\":\""
                        + id + "\",\"title\":\"x\"}}}", Instant.now()));
    }

    String estado() {
        return jdbc.queryForObject("SELECT estado FROM conversa WHERE clinica_id = ?", String.class, clinica);
    }

    int enviadas() {
        return jdbc.queryForObject("SELECT count(*) FROM evento_saida WHERE clinica_id = ?", Integer.class, clinica);
    }
}
