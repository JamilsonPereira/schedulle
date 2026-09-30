package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.application.port.BloqueioRepository;
import br.com.agendafono.clinica.domain.Bloqueio;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.instant;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.utc;

@Repository
class BloqueioJdbcRepository implements BloqueioRepository {

    private static final String COLUNAS =
            "id, clinica_id, profissional_id, lower(periodo) AS inicio, upper(periodo) AS fim, motivo";

    private final JdbcClient jdbc;

    BloqueioJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Bloqueio> buscar(UUID clinicaId, UUID bloqueioId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM bloqueio WHERE clinica_id = :c AND id = :id")
                .param("c", clinicaId).param("id", bloqueioId).query(BLOQUEIO).optional();
    }

    @Override
    public List<Bloqueio> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate) {
        String sql = "SELECT " + COLUNAS + " FROM bloqueio WHERE clinica_id = :c"
                + " AND periodo && tstzrange(:de, :ate, '[)')"
                + (profissionalId != null ? " AND (profissional_id = :p OR profissional_id IS NULL)" : "")
                + " ORDER BY lower(periodo)";
        var consulta = jdbc.sql(sql).param("c", clinicaId).param("de", utc(de)).param("ate", utc(ate));
        if (profissionalId != null) {
            consulta = consulta.param("p", profissionalId);
        }
        return consulta.query(BLOQUEIO).list();
    }

    @Override
    public void inserir(Bloqueio b) {
        jdbc.sql("""
                        INSERT INTO bloqueio (id, clinica_id, profissional_id, periodo, motivo)
                        VALUES (:id, :c, :p, tstzrange(:inicio, :fim, '[)'), :motivo)
                        """)
                .param("id", b.id()).param("c", b.clinicaId()).param("p", b.profissionalId())
                .param("inicio", utc(b.inicio())).param("fim", utc(b.fim())).param("motivo", b.motivo())
                .update();
    }

    @Override
    public void remover(UUID clinicaId, UUID bloqueioId) {
        jdbc.sql("DELETE FROM bloqueio WHERE clinica_id = :c AND id = :id")
                .param("c", clinicaId).param("id", bloqueioId).update();
    }

    private static final RowMapper<Bloqueio> BLOQUEIO = (rs, n) -> new Bloqueio(
            rs.getObject("id", UUID.class), rs.getObject("clinica_id", UUID.class),
            rs.getObject("profissional_id", UUID.class), instant(rs, "inicio"), instant(rs, "fim"),
            rs.getString("motivo"));
}
