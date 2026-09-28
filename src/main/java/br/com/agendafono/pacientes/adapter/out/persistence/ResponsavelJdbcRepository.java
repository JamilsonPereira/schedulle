package br.com.agendafono.pacientes.adapter.out.persistence;

import br.com.agendafono.compartilhado.persistencia.ErrosSql;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TelefoneJaCadastradoException;
import br.com.agendafono.pacientes.application.port.ResponsavelRepository;
import br.com.agendafono.pacientes.domain.Consentimento;
import br.com.agendafono.pacientes.domain.RegistroConsentimento;
import br.com.agendafono.pacientes.domain.Responsavel;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ResponsavelJdbcRepository implements ResponsavelRepository {

    private static final String TELEFONE_UNICO = "responsavel_telefone_uk";

    private static final String COLUNAS = "id, clinica_id, nome, telefone_e164, consentimento_em,"
            + " consentimento_versao, consentimento_canal, consentimento_evidencia, anonimizado_em, versao";

    private final JdbcClient jdbc;

    ResponsavelJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Responsavel> buscar(UUID clinicaId, UUID responsavelId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM responsavel WHERE clinica_id = :clinica AND id = :id")
                .param("clinica", clinicaId)
                .param("id", responsavelId)
                .query(RESPONSAVEL)
                .optional();
    }

    @Override
    public Optional<Responsavel> buscarPorTelefone(UUID clinicaId, Collection<Telefone> telefones) {
        if (telefones.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + COLUNAS + " FROM responsavel"
                        + " WHERE clinica_id = :clinica AND telefone_e164 IN (:telefones)"
                        + " ORDER BY criado_em LIMIT 1")
                .param("clinica", clinicaId)
                .param("telefones", telefones.stream().map(Telefone::e164).toList())
                .query(RESPONSAVEL)
                .optional();
    }

    @Override
    public void inserir(Responsavel r) {
        Consentimento c = r.consentimento();
        traduzindoTelefoneDuplicado(() -> jdbc.sql("""
                        INSERT INTO responsavel (id, clinica_id, nome, telefone_e164, consentimento_em,
                                                 consentimento_versao, consentimento_canal,
                                                 consentimento_evidencia, anonimizado_em, versao)
                        VALUES (:id, :clinica, :nome, :telefone, :consEm, :consVersao, :consCanal, :consEvidencia,
                                :anonimizado, :versao)
                        """)
                .param("id", r.id())
                .param("clinica", r.clinicaId())
                .param("nome", r.nome())
                .param("telefone", r.telefone() != null ? r.telefone().e164() : null)
                .param("consEm", c != null ? utc(c.registradoEm()) : null)
                .param("consVersao", c != null ? c.versaoTexto() : null)
                .param("consCanal", c != null ? c.canal().name() : null)
                .param("consEvidencia", c != null ? c.evidencia() : null)
                .param("anonimizado", utc(r.anonimizadoEm()))
                .param("versao", r.versao())
                .update());
        gravarHistorico(r);
    }

    @Override
    public void atualizar(Responsavel r) {
        Consentimento c = r.consentimento();
        int linhas = traduzindoTelefoneDuplicado(() -> jdbc.sql("""
                        UPDATE responsavel
                           SET nome = :nome,
                               telefone_e164 = :telefone,
                               consentimento_em = :consEm,
                               consentimento_versao = :consVersao,
                               consentimento_canal = :consCanal,
                               consentimento_evidencia = :consEvidencia,
                               anonimizado_em = :anonimizado,
                               versao = versao + 1,
                               atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("nome", r.nome())
                .param("telefone", r.telefone() != null ? r.telefone().e164() : null)
                .param("consEm", c != null ? utc(c.registradoEm()) : null)
                .param("consVersao", c != null ? c.versaoTexto() : null)
                .param("consCanal", c != null ? c.canal().name() : null)
                .param("consEvidencia", c != null ? c.evidencia() : null)
                .param("anonimizado", utc(r.anonimizadoEm()))
                .param("id", r.id())
                .param("clinica", r.clinicaId())
                .param("versao", r.versao())
                .update());
        if (linhas == 0) {
            throw new RegistroDesatualizadoException();
        }
        r.incrementarVersao();
        gravarHistorico(r);
    }

    @Override
    public List<RegistroConsentimento> historicoConsentimento(UUID clinicaId, UUID responsavelId) {
        return jdbc.sql("""
                        SELECT acao, versao_texto, canal, evidencia, registrado_em
                          FROM consentimento
                         WHERE clinica_id = :clinica AND responsavel_id = :responsavel
                         ORDER BY registrado_em, id
                        """)
                .param("clinica", clinicaId)
                .param("responsavel", responsavelId)
                .query((rs, n) -> new RegistroConsentimento(
                        RegistroConsentimento.Acao.valueOf(rs.getString("acao")),
                        rs.getString("versao_texto"),
                        Canal.valueOf(rs.getString("canal")),
                        rs.getString("evidencia"),
                        instant(rs, "registrado_em")))
                .list();
    }

    private void gravarHistorico(Responsavel r) {
        for (RegistroConsentimento reg : r.extrairRegistrosNovos()) {
            jdbc.sql("""
                            INSERT INTO consentimento (clinica_id, responsavel_id, acao, versao_texto, canal,
                                                       evidencia, registrado_em)
                            VALUES (:clinica, :responsavel, :acao, :versao, :canal, :evidencia, :em)
                            """)
                    .param("clinica", r.clinicaId())
                    .param("responsavel", r.id())
                    .param("acao", reg.acao().name())
                    .param("versao", reg.versaoTexto())
                    .param("canal", reg.canal().name())
                    .param("evidencia", reg.evidencia())
                    .param("em", utc(reg.registradoEm()))
                    .update();
        }
    }

    private static int traduzindoTelefoneDuplicado(java.util.function.IntSupplier comando) {
        try {
            return comando.getAsInt();
        } catch (DataAccessException e) {
            if (ErrosSql.violou(e, ErrosSql.VIOLACAO_UNICIDADE, TELEFONE_UNICO)) {
                throw new TelefoneJaCadastradoException();
            }
            throw e;
        }
    }

    private static final RowMapper<Responsavel> RESPONSAVEL = (rs, n) -> {
        String telefone = rs.getString("telefone_e164");
        Instant consentidoEm = instant(rs, "consentimento_em");
        Consentimento consentimento = null;
        if (consentidoEm != null) {
            String canal = rs.getString("consentimento_canal");
            String evidencia = rs.getString("consentimento_evidencia");
            consentimento = new Consentimento(
                    rs.getString("consentimento_versao"),
                    canal != null ? Canal.valueOf(canal) : Canal.WHATSAPP,
                    evidencia != null ? evidencia : "nao-registrada",
                    consentidoEm);
        }
        return Responsavel.reconstituir(
                rs.getObject("id", UUID.class),
                rs.getObject("clinica_id", UUID.class),
                rs.getString("nome"),
                telefone != null ? new Telefone(telefone) : null,
                consentimento,
                instant(rs, "anonimizado_em"),
                rs.getInt("versao"));
    };

    static Instant instant(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime v = rs.getObject(coluna, OffsetDateTime.class);
        return v != null ? v.toInstant() : null;
    }

    static OffsetDateTime utc(Instant i) {
        return i != null ? i.atOffset(ZoneOffset.UTC) : null;
    }
}
