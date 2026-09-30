package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.application.port.ClinicaRepository;
import br.com.agendafono.clinica.domain.Clinica;
import br.com.agendafono.clinica.domain.PoliticaClinica;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

@Repository
class ClinicaJdbcRepository implements ClinicaRepository {

    private final JdbcClient jdbc;

    ClinicaJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Clinica> buscar(UUID clinicaId) {
        return jdbc.sql("""
                        SELECT id, nome, fuso, versao,
                               (politica ->> 'antecedenciaMinimaMin')::int       AS antecedencia_min,
                               (politica ->> 'janelaMaximaDias')::int            AS janela_dias,
                               (politica ->> 'passoMin')::int                    AS passo_min,
                               (politica ->> 'ttlReservaMin')::int               AS ttl_min,
                               (politica ->> 'antecedenciaAvisoFaltaHoras')::int AS aviso_falta_horas
                          FROM clinica WHERE id = :id
                        """)
                .param("id", clinicaId)
                .query((rs, n) -> Clinica.reconstituir(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        ZoneId.of(rs.getString("fuso")),
                        new PoliticaClinica(inteiro(rs, "antecedencia_min"), inteiro(rs, "janela_dias"),
                                inteiro(rs, "passo_min"), inteiro(rs, "ttl_min"), inteiro(rs, "aviso_falta_horas")),
                        rs.getInt("versao")))
                .optional();
    }

    @Override
    public void inserir(Clinica c) {
        jdbc.sql("""
                        INSERT INTO clinica (id, nome, fuso, politica, versao)
                        VALUES (:id, :nome, :fuso, CAST(:politica AS jsonb), :versao)
                        """)
                .param("id", c.id())
                .param("nome", c.nome())
                .param("fuso", c.fuso().getId())
                .param("politica", c.politica().comoJson())
                .param("versao", c.versao())
                .update();
    }

    @Override
    public void atualizar(Clinica c) {
        int linhas = jdbc.sql("""
                        UPDATE clinica
                           SET nome = :nome, fuso = :fuso, politica = CAST(:politica AS jsonb),
                               versao = versao + 1, atualizado_em = now()
                         WHERE id = :id AND versao = :versao
                        """)
                .param("nome", c.nome())
                .param("fuso", c.fuso().getId())
                .param("politica", c.politica().comoJson())
                .param("id", c.id())
                .param("versao", c.versao())
                .update();
        if (linhas == 0) {
            throw new ConflitoDeVersaoException();
        }
        c.incrementarVersao();
    }

    private static Integer inteiro(ResultSet rs, String coluna) throws SQLException {
        int v = rs.getInt(coluna);
        return rs.wasNull() ? null : v;
    }
}
