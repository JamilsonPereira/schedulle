package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.application.port.RefreshTokenRepository;
import br.com.agendafono.clinica.domain.RefreshToken;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.instant;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.utc;

@Repository
class RefreshTokenJdbcRepository implements RefreshTokenRepository {

    private final JdbcClient jdbc;

    RefreshTokenJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void inserir(RefreshToken t) {
        jdbc.sql("""
                        INSERT INTO refresh_token (id, clinica_id, usuario_id, familia, hash, expira_em)
                        VALUES (:id, :c, :u, :familia, :hash, :expira)
                        """)
                .param("id", t.id()).param("c", t.clinicaId()).param("u", t.usuarioId())
                .param("familia", t.familia()).param("hash", t.hash()).param("expira", utc(t.expiraEm()))
                .update();
    }

    @Override
    public Optional<RefreshToken> porHash(String hash) {
        return jdbc.sql("""
                        SELECT id, clinica_id, usuario_id, familia, hash, expira_em, usado_em, revogado_em
                          FROM refresh_token WHERE hash = :hash
                        """)
                .param("hash", hash)
                .query((rs, n) -> new RefreshToken(rs.getObject("id", UUID.class),
                        rs.getObject("clinica_id", UUID.class), rs.getObject("usuario_id", UUID.class),
                        rs.getObject("familia", UUID.class), rs.getString("hash"), instant(rs, "expira_em"),
                        instant(rs, "usado_em"), instant(rs, "revogado_em")))
                .optional();
    }

    @Override
    public boolean marcarUsado(UUID id, Instant agora) {
        return jdbc.sql("UPDATE refresh_token SET usado_em = :agora WHERE id = :id AND usado_em IS NULL"
                        + " AND revogado_em IS NULL")
                .param("agora", utc(agora)).param("id", id).update() == 1;
    }

    @Override
    public void revogarFamilia(UUID familia, Instant agora) {
        jdbc.sql("UPDATE refresh_token SET revogado_em = :agora WHERE familia = :f AND revogado_em IS NULL")
                .param("agora", utc(agora)).param("f", familia).update();
    }

    @Override
    public void revogarDoUsuario(UUID usuarioId, Instant agora) {
        jdbc.sql("UPDATE refresh_token SET revogado_em = :agora WHERE usuario_id = :u AND revogado_em IS NULL")
                .param("agora", utc(agora)).param("u", usuarioId).update();
    }
}
