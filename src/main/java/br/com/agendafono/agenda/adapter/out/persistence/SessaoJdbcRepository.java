package br.com.agendafono.agenda.adapter.out.persistence;

import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.StatusSessao;
import br.com.agendafono.agenda.TipoSessao;
import br.com.agendafono.agenda.VersaoDesatualizadaException;
import br.com.agendafono.agenda.application.port.SessaoRepository;
import br.com.agendafono.agenda.domain.Sessao;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Persistência de sessões com JdbcClient (ADR-07): {@code tstzrange} e as consultas de agenda
 * ficam mais claras em SQL do que em JPA. Horários trafegam como {@link OffsetDateTime} em UTC.
 */
@Repository
class SessaoJdbcRepository implements SessaoRepository {

    /** SQLState do Postgres para violação de constraint EXCLUDE. */
    private static final String EXCLUSION_VIOLATION = "23P01";

    /** Mesma regra da constraint: reservas vencidas não ocupam horário na leitura. */
    private static final String OCUPA_HORARIO =
            "(status IN ('AGENDADA', 'CONFIRMADA') OR (status = 'RESERVADA' AND expira_em > :agora))";

    private static final String COLUNAS = "id, clinica_id, paciente_id, profissional_id, recurso_id, serie_id, tipo,"
            + " lower(periodo) AS inicio, upper(periodo) AS fim, status, expira_em, versao";

    private final JdbcClient jdbc;

    SessaoJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Sessao> buscar(UUID clinicaId, UUID sessaoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM sessao WHERE id = :id AND clinica_id = :clinica")
                .param("id", sessaoId)
                .param("clinica", clinicaId)
                .query(SESSAO)
                .optional();
    }

    @Override
    public void inserir(Sessao s) {
        executarTraduzindoConflito(() -> jdbc.sql("""
                        INSERT INTO sessao (id, clinica_id, paciente_id, profissional_id, recurso_id, serie_id,
                                            tipo, periodo, status, expira_em, versao)
                        VALUES (:id, :clinica, :paciente, :profissional, :recurso, :serie,
                                :tipo, tstzrange(:inicio, :fim, '[)'), :status, :expira, :versao)
                        """)
                .param("id", s.id())
                .param("clinica", s.clinicaId())
                .param("paciente", s.pacienteId())
                .param("profissional", s.profissionalId())
                .param("recurso", s.recursoId())
                .param("serie", s.serieId())
                .param("tipo", s.tipo().name())
                .param("inicio", utc(s.periodo().inicio()))
                .param("fim", utc(s.periodo().fim()))
                .param("status", s.status().name())
                .param("expira", utc(s.expiraEm()))
                .param("versao", s.versao())
                .update());
    }

    @Override
    public void atualizar(Sessao s) {
        int linhas = executarTraduzindoConflito(() -> jdbc.sql("""
                        UPDATE sessao
                           SET recurso_id = :recurso,
                               periodo = tstzrange(:inicio, :fim, '[)'),
                               status = :status,
                               expira_em = :expira,
                               versao = versao + 1,
                               atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("recurso", s.recursoId())
                .param("inicio", utc(s.periodo().inicio()))
                .param("fim", utc(s.periodo().fim()))
                .param("status", s.status().name())
                .param("expira", utc(s.expiraEm()))
                .param("id", s.id())
                .param("clinica", s.clinicaId())
                .param("versao", s.versao())
                .update());
        if (linhas == 0) {
            throw new VersaoDesatualizadaException();
        }
        s.incrementarVersao();
    }

    @Override
    public List<Sessao> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate) {
        String sql = "SELECT " + COLUNAS + " FROM sessao"
                + " WHERE clinica_id = :clinica AND lower(periodo) >= :de AND lower(periodo) < :ate"
                + (profissionalId != null ? " AND profissional_id = :profissional" : "")
                + " ORDER BY lower(periodo), profissional_id";
        var consulta = jdbc.sql(sql)
                .param("clinica", clinicaId)
                .param("de", utc(de))
                .param("ate", utc(ate));
        if (profissionalId != null) {
            consulta = consulta.param("profissional", profissionalId);
        }
        return consulta.query(SESSAO).list();
    }

    @Override
    public List<Periodo> ocupadosDoProfissional(UUID clinicaId, UUID profissionalId, Periodo intervalo,
                                                Instant agora, UUID ignorarSessaoId) {
        String sql = "SELECT lower(periodo) AS inicio, upper(periodo) AS fim FROM sessao"
                + " WHERE clinica_id = :clinica AND profissional_id = :profissional"
                + " AND periodo && tstzrange(:de, :ate, '[)')"
                + " AND " + OCUPA_HORARIO
                + (ignorarSessaoId != null ? " AND id <> :ignorar" : "");
        var consulta = jdbc.sql(sql)
                .param("clinica", clinicaId)
                .param("profissional", profissionalId)
                .param("de", utc(intervalo.inicio()))
                .param("ate", utc(intervalo.fim()))
                .param("agora", utc(agora));
        if (ignorarSessaoId != null) {
            consulta = consulta.param("ignorar", ignorarSessaoId);
        }
        return consulta.query(PERIODO).list();
    }

    @Override
    public Map<UUID, List<Periodo>> ocupadosPorRecurso(UUID clinicaId, Collection<UUID> recursos, Periodo intervalo,
                                                       Instant agora, UUID ignorarSessaoId) {
        if (recursos.isEmpty()) {
            return Map.of();
        }
        String sql = "SELECT recurso_id, lower(periodo) AS inicio, upper(periodo) AS fim FROM sessao"
                + " WHERE clinica_id = :clinica AND recurso_id IN (:recursos)"
                + " AND periodo && tstzrange(:de, :ate, '[)')"
                + " AND " + OCUPA_HORARIO
                + (ignorarSessaoId != null ? " AND id <> :ignorar" : "");
        var consulta = jdbc.sql(sql)
                .param("clinica", clinicaId)
                .param("recursos", List.copyOf(recursos))
                .param("de", utc(intervalo.inicio()))
                .param("ate", utc(intervalo.fim()))
                .param("agora", utc(agora));
        if (ignorarSessaoId != null) {
            consulta = consulta.param("ignorar", ignorarSessaoId);
        }
        Map<UUID, List<Periodo>> porRecurso = new HashMap<>();
        consulta.query((ResultSet rs) -> {
            porRecurso.computeIfAbsent(rs.getObject("recurso_id", UUID.class), k -> new ArrayList<>())
                    .add(new Periodo(instant(rs, "inicio"), instant(rs, "fim")));
        });
        return porRecurso;
    }

    @Override
    public int cancelarReservasExpiradas(UUID clinicaId, UUID profissionalId, UUID recursoId, Instant agora) {
        String sql = "UPDATE sessao SET status = 'CANCELADA', versao = versao + 1, atualizado_em = now()"
                + " WHERE clinica_id = :clinica AND status = 'RESERVADA' AND expira_em <= :agora"
                + " AND (profissional_id = :profissional"
                + (recursoId != null ? " OR recurso_id = :recurso" : "") + ")";
        var comando = jdbc.sql(sql)
                .param("clinica", clinicaId)
                .param("agora", utc(agora))
                .param("profissional", profissionalId);
        if (recursoId != null) {
            comando = comando.param("recurso", recursoId);
        }
        return comando.update();
    }

    @Override
    public int cancelarTodasReservasExpiradas(Instant agora) {
        return jdbc.sql("""
                        UPDATE sessao
                           SET status = 'CANCELADA', versao = versao + 1, atualizado_em = now()
                         WHERE status = 'RESERVADA' AND expira_em <= :agora
                        """)
                .param("agora", utc(agora))
                .update();
    }

    // ------------------------------------------------------------------ mapeamento

    private static final RowMapper<Periodo> PERIODO = (rs, n) ->
            new Periodo(instant(rs, "inicio"), instant(rs, "fim"));

    private static final RowMapper<Sessao> SESSAO = (rs, n) -> Sessao.reconstituir(
            rs.getObject("id", UUID.class),
            rs.getObject("clinica_id", UUID.class),
            rs.getObject("paciente_id", UUID.class),
            rs.getObject("profissional_id", UUID.class),
            rs.getObject("recurso_id", UUID.class),
            rs.getObject("serie_id", UUID.class),
            TipoSessao.valueOf(rs.getString("tipo")),
            new Periodo(instant(rs, "inicio"), instant(rs, "fim")),
            StatusSessao.valueOf(rs.getString("status")),
            instant(rs, "expira_em"),
            rs.getInt("versao"));

    private static Instant instant(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
        return valor != null ? valor.toInstant() : null;
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante != null ? instante.atOffset(ZoneOffset.UTC) : null;
    }

    private static int executarTraduzindoConflito(IntSupplier comando) {
        try {
            return comando.getAsInt();
        } catch (DataAccessException e) {
            if (EXCLUSION_VIOLATION.equals(sqlState(e))) {
                throw new HorarioIndisponivelException();
            }
            throw e;
        }
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
