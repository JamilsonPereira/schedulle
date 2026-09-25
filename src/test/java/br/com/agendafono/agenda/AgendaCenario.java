package br.com.agendafono.agenda;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * Monta no banco uma clínica de teste: um fono com grade de segunda das 08:00 às 12:00, sessões de 40 min,
 * e um paciente. A "segunda" é sempre a próxima segunda-feira a pelo menos 3 dias de hoje, para ficar
 * dentro da antecedência mínima e da janela do bot com o relógio real.
 */
class AgendaCenario {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");

    private final JdbcTemplate jdbc;

    final LocalDate segunda = LocalDate.now(SP).plusDays(3).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
    UUID clinica;
    UUID fono;
    UUID paciente;

    AgendaCenario(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    AgendaCenario criar() {
        jdbc.execute("TRUNCATE clinica CASCADE");
        clinica = id("INSERT INTO clinica (nome) VALUES ('Clínica Teste') RETURNING id");
        fono = novoProfissional();
        grade(fono, DayOfWeek.MONDAY, "08:00", "12:00", null);
        paciente = novoPaciente("+5511999990000");
        return this;
    }

    UUID novoProfissional() {
        return id("INSERT INTO profissional (clinica_id, nome, duracao_padrao_min) VALUES (?, 'Fono', 40) RETURNING id",
                clinica);
    }

    UUID novoPaciente(String telefone) {
        UUID responsavel = id("INSERT INTO responsavel (clinica_id, telefone_e164) VALUES (?, ?) RETURNING id",
                clinica, telefone);
        return id("INSERT INTO paciente (clinica_id, responsavel_id, nome) VALUES (?, ?, 'Paciente') RETURNING id",
                clinica, responsavel);
    }

    UUID novaClinica() {
        return id("INSERT INTO clinica (nome) VALUES ('Outra clínica') RETURNING id");
    }

    void grade(UUID profissional, DayOfWeek dia, String inicio, String fim, UUID recurso) {
        jdbc.update("INSERT INTO grade_semanal (profissional_id, dia_semana, hora_inicio, hora_fim, recurso_id)"
                        + " VALUES (?, ?, ?::time, ?::time, ?)",
                profissional, dia.getValue(), inicio, fim, recurso);
    }

    void bloqueio(String inicio, String fim) {
        jdbc.update("INSERT INTO bloqueio (clinica_id, profissional_id, periodo)"
                        + " VALUES (?, NULL, tstzrange(?::timestamptz, ?::timestamptz, '[)'))",
                clinica, as(inicio).toString(), as(fim).toString());
    }

    /** Insere direto no banco uma reserva do bot que já venceu (o job ainda não rodou). */
    UUID reservaVencida(String inicio, String fim) {
        return id("INSERT INTO sessao (clinica_id, paciente_id, profissional_id, tipo, periodo, status, expira_em)"
                        + " VALUES (?, ?, ?, 'AVALIACAO', tstzrange(?::timestamptz, ?::timestamptz, '[)'),"
                        + " 'RESERVADA', now() - interval '1 minute') RETURNING id",
                clinica, paciente, fono, as(inicio).toString(), as(fim).toString());
    }

    String statusNoBanco(UUID sessao) {
        return jdbc.queryForObject("SELECT status FROM sessao WHERE id = ?", String.class, sessao);
    }

    /** Horário "HH:mm" da segunda de teste, no fuso da clínica. */
    Instant as(String hhmm) {
        return ZonedDateTime.of(segunda, LocalTime.parse(hhmm), SP).toInstant();
    }

    private UUID id(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}
