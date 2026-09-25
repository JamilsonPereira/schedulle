package br.com.agendafono.agenda.adapter.out.persistence;

import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.application.port.ConfiguracaoAgendaPort;
import br.com.agendafono.agenda.domain.BlocoGrade;
import br.com.agendafono.agenda.domain.PoliticaAgendamento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lê grade, bloqueios, fuso e política direto das tabelas do módulo {@code clinica}.
 * PROVISÓRIO: no Passo 2 passa a chamar a API pública do módulo {@code clinica} ({@code ProfissionalConsulta}).
 *
 * <p>Chaves aceitas em {@code clinica.politica} (todas opcionais; ausentes usam o padrão):
 * {@code antecedenciaMinimaMin}, {@code janelaMaximaDias}, {@code passoMin}, {@code ttlReservaMin},
 * {@code antecedenciaAvisoFaltaHoras}.
 */
@Component
class ConfiguracaoAgendaJdbcAdapter implements ConfiguracaoAgendaPort {

    private final JdbcClient jdbc;

    ConfiguracaoAgendaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConfiguracaoProfissional> carregar(UUID clinicaId, UUID profissionalId) {
        Optional<Base> base = jdbc.sql("""
                        SELECT c.fuso,
                               p.duracao_padrao_min,
                               (c.politica ->> 'antecedenciaMinimaMin')::int      AS antecedencia_min,
                               (c.politica ->> 'janelaMaximaDias')::int           AS janela_dias,
                               (c.politica ->> 'passoMin')::int                   AS passo_min,
                               (c.politica ->> 'ttlReservaMin')::int              AS ttl_min,
                               (c.politica ->> 'antecedenciaAvisoFaltaHoras')::int AS aviso_falta_horas
                          FROM profissional p
                          JOIN clinica c ON c.id = p.clinica_id
                         WHERE p.id = :profissional AND p.clinica_id = :clinica AND p.ativo
                        """)
                .param("profissional", profissionalId)
                .param("clinica", clinicaId)
                .query((rs, n) -> new Base(
                        ZoneId.of(rs.getString("fuso")),
                        Duration.ofMinutes(rs.getInt("duracao_padrao_min")),
                        politica(rs)))
                .optional();

        return base.map(b -> new ConfiguracaoProfissional(profissionalId, b.zona(), b.duracaoPadrao(),
                grade(profissionalId), b.politica()));
    }

    @Override
    public List<Periodo> bloqueios(UUID clinicaId, UUID profissionalId, Periodo intervalo) {
        return jdbc.sql("""
                        SELECT lower(periodo) AS inicio, upper(periodo) AS fim
                          FROM bloqueio
                         WHERE clinica_id = :clinica
                           AND (profissional_id = :profissional OR profissional_id IS NULL)
                           AND periodo && tstzrange(:de, :ate, '[)')
                        """)
                .param("clinica", clinicaId)
                .param("profissional", profissionalId)
                .param("de", intervalo.inicio().atOffset(ZoneOffset.UTC))
                .param("ate", intervalo.fim().atOffset(ZoneOffset.UTC))
                .query((rs, n) -> new Periodo(
                        rs.getObject("inicio", OffsetDateTime.class).toInstant(),
                        rs.getObject("fim", OffsetDateTime.class).toInstant()))
                .list();
    }

    private List<BlocoGrade> grade(UUID profissionalId) {
        return jdbc.sql("""
                        SELECT dia_semana, hora_inicio, hora_fim, recurso_id
                          FROM grade_semanal
                         WHERE profissional_id = :profissional
                         ORDER BY dia_semana, hora_inicio
                        """)
                .param("profissional", profissionalId)
                .query((rs, n) -> new BlocoGrade(
                        DayOfWeek.of(rs.getInt("dia_semana")),
                        rs.getTime("hora_inicio").toLocalTime(),
                        rs.getTime("hora_fim").toLocalTime(),
                        rs.getObject("recurso_id", UUID.class)))
                .list();
    }

    private static PoliticaAgendamento politica(ResultSet rs) throws SQLException {
        PoliticaAgendamento padrao = PoliticaAgendamento.PADRAO;
        Integer passo = inteiro(rs, "passo_min");
        return new PoliticaAgendamento(
                minutos(rs, "antecedencia_min", padrao.antecedenciaMinima()),
                dias(rs, "janela_dias", padrao.janelaMaxima()),
                passo != null ? Duration.ofMinutes(passo) : padrao.passo(),
                minutos(rs, "ttl_min", padrao.ttlReserva()),
                horas(rs, "aviso_falta_horas", padrao.antecedenciaAvisoFalta()));
    }

    private static Duration minutos(ResultSet rs, String coluna, Duration padrao) throws SQLException {
        Integer v = inteiro(rs, coluna);
        return v != null ? Duration.ofMinutes(v) : padrao;
    }

    private static Duration horas(ResultSet rs, String coluna, Duration padrao) throws SQLException {
        Integer v = inteiro(rs, coluna);
        return v != null ? Duration.ofHours(v) : padrao;
    }

    private static Duration dias(ResultSet rs, String coluna, Duration padrao) throws SQLException {
        Integer v = inteiro(rs, coluna);
        return v != null ? Duration.ofDays(v) : padrao;
    }

    private static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }

    private record Base(ZoneId zona, Duration duracaoPadrao, PoliticaAgendamento politica) {
    }
}
