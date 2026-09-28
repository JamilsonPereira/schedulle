package br.com.agendafono.pacientes.adapter.out.persistence;

import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.TipoAnexo;
import br.com.agendafono.pacientes.application.port.AnexoRepository;
import br.com.agendafono.pacientes.domain.Anexo;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.agendafono.pacientes.adapter.out.persistence.ResponsavelJdbcRepository.instant;
import static br.com.agendafono.pacientes.adapter.out.persistence.ResponsavelJdbcRepository.utc;

@Repository
class AnexoJdbcRepository implements AnexoRepository {

    private static final String COLUNAS = "a.id, a.clinica_id, a.paciente_id, a.tipo, a.nome_arquivo,"
            + " a.content_type, a.tamanho_bytes, a.sha256, a.chave_armazenamento, a.origem, a.criado_em";

    private final JdbcClient jdbc;

    AnexoJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void inserir(Anexo a) {
        jdbc.sql("""
                        INSERT INTO anexo (id, clinica_id, paciente_id, tipo, nome_arquivo, content_type,
                                           tamanho_bytes, sha256, chave_armazenamento, origem, criado_em)
                        VALUES (:id, :clinica, :paciente, :tipo, :nome, :contentType, :tamanho, :sha256, :chave,
                                :origem, :criadoEm)
                        """)
                .param("id", a.id())
                .param("clinica", a.clinicaId())
                .param("paciente", a.pacienteId())
                .param("tipo", a.tipo().name())
                .param("nome", a.nomeArquivo())
                .param("contentType", a.contentType())
                .param("tamanho", a.tamanhoBytes())
                .param("sha256", a.sha256())
                .param("chave", a.chave())
                .param("origem", a.origem().name())
                .param("criadoEm", utc(a.criadoEm()))
                .update();
    }

    @Override
    public Optional<Anexo> buscar(UUID clinicaId, UUID anexoId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM anexo a WHERE a.clinica_id = :clinica AND a.id = :id")
                .param("clinica", clinicaId)
                .param("id", anexoId)
                .query(ANEXO)
                .optional();
    }

    @Override
    public List<Anexo> doPaciente(UUID clinicaId, UUID pacienteId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM anexo a"
                        + " WHERE a.clinica_id = :clinica AND a.paciente_id = :paciente ORDER BY a.criado_em DESC")
                .param("clinica", clinicaId)
                .param("paciente", pacienteId)
                .query(ANEXO)
                .list();
    }

    @Override
    public List<Anexo> doResponsavel(UUID clinicaId, UUID responsavelId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM anexo a"
                        + " JOIN paciente p ON p.id = a.paciente_id AND p.clinica_id = a.clinica_id"
                        + " WHERE a.clinica_id = :clinica AND p.responsavel_id = :responsavel"
                        + " ORDER BY a.criado_em")
                .param("clinica", clinicaId)
                .param("responsavel", responsavelId)
                .query(ANEXO)
                .list();
    }

    @Override
    public void remover(UUID clinicaId, UUID anexoId) {
        jdbc.sql("DELETE FROM anexo WHERE clinica_id = :clinica AND id = :id")
                .param("clinica", clinicaId)
                .param("id", anexoId)
                .update();
    }

    private static final RowMapper<Anexo> ANEXO = (rs, n) -> new Anexo(
            rs.getObject("id", UUID.class),
            rs.getObject("clinica_id", UUID.class),
            rs.getObject("paciente_id", UUID.class),
            TipoAnexo.valueOf(rs.getString("tipo")),
            rs.getString("nome_arquivo"),
            rs.getString("content_type"),
            rs.getLong("tamanho_bytes"),
            rs.getString("sha256"),
            rs.getString("chave_armazenamento"),
            Canal.valueOf(rs.getString("origem")),
            instant(rs, "criado_em"));
}
