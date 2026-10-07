package br.com.agendafono.bot.adapter.out.persistence;

import br.com.agendafono.bot.domain.ContextoConversa;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.PacienteDoContato;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** O contexto vai e volta do jsonb com o mesmo ObjectMapper da aplicação (ConfiguracaoJackson2). */
class ContextoJsonTest {

    private final ConversaJdbcRepository repo = new ConversaJdbcRepository(null, JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build());

    @Test
    void idaEVolta() {
        HorarioOferta h = new HorarioOferta("h1", UUID.randomUUID(), "Dra. Ana", Instant.parse("2026-10-08T12:00:00Z"),
                Instant.parse("2026-10-08T12:40:00Z"));
        ContextoConversa ctx = ContextoConversa.vazio()
                .comPacientesOferecidos(List.of(new PacienteDoContato("p1", UUID.randomUUID(), "João", "VOZ")))
                .comNovoNome("Maria Souza")
                .comNovoNascimento(LocalDate.of(2019, 3, 10))
                .comHorarios(List.of(h), LocalDate.of(2026, 10, 9))
                .comReserva(UUID.randomUUID(), h);

        String json = repo.escrever(ctx);
        assertThat(json).contains("\"novoNascimento\":\"2019-03-10\"").contains("\"inicio\":\"2026-10-08T12:00:00Z\"");
        assertThat(repo.ler(json)).isEqualTo(ctx);
    }

    @Test
    void contextoVazioDoBancoOuDeOutraVersaoRecomecaVazio() {
        assertThat(repo.ler("{}")).isEqualTo(ContextoConversa.vazio());
        assertThat(repo.ler("{\"schemaVersao\":99,\"novoNome\":\"x\"}")).isEqualTo(ContextoConversa.vazio());
        assertThat(repo.ler("não é json")).isEqualTo(ContextoConversa.vazio());
        assertThat(repo.ler(null)).isEqualTo(ContextoConversa.vazio());
    }
}
