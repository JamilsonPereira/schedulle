package br.com.agendafono.bot.adapter.out.persistence;

import br.com.agendafono.bot.application.port.ConversaRepository;
import br.com.agendafono.bot.domain.ContextoConversa;
import br.com.agendafono.bot.domain.Conversa;
import br.com.agendafono.bot.domain.EstadoConversa;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tabela {@code conversa}; o contexto vai em jsonb. Um contexto ilegível (versão antiga) recomeça vazio. */
@Repository
class ConversaJdbcRepository implements ConversaRepository {

    private static final Logger log = LoggerFactory.getLogger(ConversaJdbcRepository.class);
    private static final String COLUNAS = "id, clinica_id, responsavel_id, estado, contexto::text AS contexto, modo,"
            + " tentativas_falhas, ultima_msg_em, versao";

    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final RowMapper<Conversa> mapper;

    ConversaJdbcRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = this::mapear;
    }

    @Override
    public Optional<Conversa> doResponsavel(UUID clinicaId, UUID responsavelId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM conversa"
                        + " WHERE clinica_id = :clinica AND responsavel_id = :responsavel FOR UPDATE")
                .param("clinica", clinicaId)
                .param("responsavel", responsavelId)
                .query(mapper)
                .optional();
    }

    @Override
    public Optional<Conversa> buscar(UUID clinicaId, UUID conversaId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM conversa WHERE clinica_id = :clinica AND id = :id FOR UPDATE")
                .param("clinica", clinicaId)
                .param("id", conversaId)
                .query(mapper)
                .optional();
    }

    @Override
    public void salvar(Conversa c) {
        if (c.nova()) {
            jdbc.sql("""
                            INSERT INTO conversa (id, clinica_id, responsavel_id, estado, contexto, modo,
                                                  tentativas_falhas, ultima_msg_em, versao, atualizado_em)
                            VALUES (:id, :clinica, :responsavel, :estado, CAST(:contexto AS jsonb), :modo, :falhas,
                                    :ultima, :versao, now())
                            """)
                    .param("id", c.id())
                    .param("clinica", c.clinicaId())
                    .param("responsavel", c.responsavelId())
                    .param("estado", c.estado().name())
                    .param("contexto", escrever(c.contexto()))
                    .param("modo", c.modo().name())
                    .param("falhas", c.tentativasFalhas())
                    .param("ultima", utc(c.ultimaMensagemEm()))
                    .param("versao", c.versao())
                    .update();
            c.marcarPersistida();
            return;
        }
        int linhas = jdbc.sql("""
                        UPDATE conversa
                           SET estado = :estado, contexto = CAST(:contexto AS jsonb), modo = :modo,
                               tentativas_falhas = :falhas, ultima_msg_em = :ultima, versao = versao + 1,
                               atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("estado", c.estado().name())
                .param("contexto", escrever(c.contexto()))
                .param("modo", c.modo().name())
                .param("falhas", c.tentativasFalhas())
                .param("ultima", utc(c.ultimaMensagemEm()))
                .param("id", c.id())
                .param("clinica", c.clinicaId())
                .param("versao", c.versao())
                .update();
        if (linhas == 0) {
            throw new OptimisticLockingFailureException("Conversa " + c.id() + " foi alterada por outra transação");
        }
        c.incrementarVersao();
    }

    @Override
    public void apagarDoResponsavel(UUID clinicaId, UUID responsavelId) {
        jdbc.sql("DELETE FROM conversa WHERE clinica_id = :clinica AND responsavel_id = :responsavel")
                .param("clinica", clinicaId)
                .param("responsavel", responsavelId)
                .update();
    }

    @Override
    public List<Conversa> emModoHumano(UUID clinicaId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM conversa WHERE clinica_id = :clinica AND modo = 'HUMANO'"
                        + " ORDER BY ultima_msg_em NULLS LAST, id")
                .param("clinica", clinicaId)
                .query(mapper)
                .list();
    }

    private Conversa mapear(ResultSet rs, int linha) throws SQLException {
        return Conversa.reconstituir(
                rs.getObject("id", UUID.class),
                rs.getObject("clinica_id", UUID.class),
                rs.getObject("responsavel_id", UUID.class),
                EstadoConversa.valueOf(rs.getString("estado")),
                ler(rs.getString("contexto")),
                Conversa.Modo.valueOf(rs.getString("modo")),
                rs.getInt("tentativas_falhas"),
                instant(rs, "ultima_msg_em"),
                rs.getInt("versao"));
    }

    String escrever(ContextoConversa contexto) {
        try {
            return json.writeValueAsString(contexto);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Contexto da conversa não serializável", e);
        }
    }

    ContextoConversa ler(String texto) {
        if (texto == null || texto.isBlank()) {
            return ContextoConversa.vazio();
        }
        try {
            ContextoConversa c = json.readValue(texto, ContextoConversa.class);
            return c.schemaVersao() == ContextoConversa.VERSAO ? c : ContextoConversa.vazio();
        } catch (Exception e) {
            log.warn("Contexto de conversa ilegível; recomeçando vazio ({})", e.getClass().getSimpleName());
            return ContextoConversa.vazio();
        }
    }

    private static Instant instant(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime v = rs.getObject(coluna, OffsetDateTime.class);
        return v != null ? v.toInstant() : null;
    }

    private static OffsetDateTime utc(Instant i) {
        return i != null ? i.atOffset(ZoneOffset.UTC) : null;
    }
}
