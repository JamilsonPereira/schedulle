package br.com.agendafono.agenda;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Garante a regra RN-03 direto no banco: um profissional (ou sala) nunca tem
 * duas sessões ativas sobrepostas, mesmo com requisições concorrentes.
 */
@SpringBootTest
@Testcontainers
class SessaoConflitoIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final String EXCLUSION_VIOLATION = "23P01";

    @Autowired
    JdbcTemplate jdbc;

    UUID clinica;
    UUID paciente;
    UUID fonoA;
    UUID fonoB;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE sessao, paciente, responsavel, profissional, recurso, clinica CASCADE");

        clinica = insertReturningId("INSERT INTO clinica (nome) VALUES ('Clínica Teste') RETURNING id");
        UUID responsavel = insertReturningId(
                "INSERT INTO responsavel (clinica_id, telefone_e164) VALUES (?, '+5511999990000') RETURNING id", clinica);
        paciente = insertReturningId(
                "INSERT INTO paciente (clinica_id, responsavel_id, nome) VALUES (?, ?, 'Ana') RETURNING id",
                clinica, responsavel);
        fonoA = insertReturningId("INSERT INTO profissional (clinica_id, nome) VALUES (?, 'Fono A') RETURNING id", clinica);
        fonoB = insertReturningId("INSERT INTO profissional (clinica_id, nome) VALUES (?, 'Fono B') RETURNING id", clinica);

        agendar(fonoA, null, "14:00", "14:40", "AGENDADA");
    }

    @Test
    void bloqueiaSessaoSobrepostaDoMesmoProfissional() {
        assertThatThrownBy(() -> agendar(fonoA, null, "14:20", "15:00", "AGENDADA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
    }

    @Test
    void bloqueiaReservaSobrepostaEmHorarioConfirmado() {
        assertThatThrownBy(() -> agendar(fonoA, null, "14:00", "14:40", "RESERVADA"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteSessaoAdjacente() {
        assertThatCode(() -> agendar(fonoA, null, "14:40", "15:20", "AGENDADA")).doesNotThrowAnyException();
    }

    @Test
    void permiteOutroProfissionalNoMesmoHorario() {
        assertThatCode(() -> agendar(fonoB, null, "14:00", "14:40", "AGENDADA")).doesNotThrowAnyException();
    }

    @Test
    void sessaoCanceladaNaoOcupaHorario() {
        assertThatCode(() -> agendar(fonoA, null, "14:00", "14:40", "CANCELADA")).doesNotThrowAnyException();
    }

    @Test
    void bloqueiaMesmaSalaParaProfissionaisDiferentes() {
        UUID cabine = insertReturningId(
                "INSERT INTO recurso (clinica_id, nome, tipo) VALUES (?, 'Cabine 1', 'CABINE') RETURNING id", clinica);
        agendar(fonoA, cabine, "16:00", "16:40", "AGENDADA");

        assertThatThrownBy(() -> agendar(fonoB, cabine, "16:30", "17:10", "AGENDADA"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo(EXCLUSION_VIOLATION));
    }

    // ------------------------------------------------------------------

    private void agendar(UUID profissional, UUID recurso, String inicio, String fim, String status) {
        jdbc.update("""
                INSERT INTO sessao (clinica_id, paciente_id, profissional_id, recurso_id, tipo, periodo, status, expira_em)
                VALUES (?, ?, ?, ?, 'TERAPIA',
                        tstzrange(('2026-10-01 ' || ? || ' America/Sao_Paulo')::timestamptz,
                                  ('2026-10-01 ' || ? || ' America/Sao_Paulo')::timestamptz),
                        ?, CASE WHEN ? = 'RESERVADA' THEN now() + interval '5 minutes' END)
                """, clinica, paciente, profissional, recurso, inicio, fim, status, status);
    }

    private UUID insertReturningId(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
