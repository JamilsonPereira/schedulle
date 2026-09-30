package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.NomeDuplicadoException;
import br.com.agendafono.clinica.TipoRecurso;
import br.com.agendafono.clinica.application.port.RecursoRepository;
import br.com.agendafono.clinica.domain.Recurso;
import br.com.agendafono.compartilhado.persistencia.ErrosSql;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class RecursoJdbcRepository implements RecursoRepository {

    private final JdbcClient jdbc;

    RecursoJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Recurso> buscar(UUID clinicaId, UUID recursoId) {
        return jdbc.sql("SELECT id, clinica_id, nome, tipo, ativo, versao FROM recurso WHERE clinica_id = :c AND id = :id")
                .param("c", clinicaId).param("id", recursoId).query(RECURSO).optional();
    }

    @Override
    public List<Recurso> listar(UUID clinicaId) {
        return jdbc.sql("SELECT id, clinica_id, nome, tipo, ativo, versao FROM recurso WHERE clinica_id = :c"
                        + " ORDER BY lower(nome), id")
                .param("c", clinicaId).query(RECURSO).list();
    }

    @Override
    public int contarAtivos(UUID clinicaId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbc.sql("SELECT count(*) FROM recurso WHERE clinica_id = :c AND ativo AND id IN (:ids)")
                .param("c", clinicaId).param("ids", List.copyOf(ids)).query(Integer.class).single();
    }

    @Override
    public void inserir(Recurso r) {
        traduzindo(() -> jdbc.sql("""
                        INSERT INTO recurso (id, clinica_id, nome, tipo, ativo, versao)
                        VALUES (:id, :c, :nome, :tipo, :ativo, :versao)
                        """)
                .param("id", r.id()).param("c", r.clinicaId()).param("nome", r.nome()).param("tipo", r.tipo().name())
                .param("ativo", r.ativo()).param("versao", r.versao()).update());
    }

    @Override
    public void atualizar(Recurso r) {
        int linhas = traduzindo(() -> jdbc.sql("""
                        UPDATE recurso SET nome = :nome, tipo = :tipo, ativo = :ativo, versao = versao + 1
                         WHERE id = :id AND clinica_id = :c AND versao = :versao
                        """)
                .param("nome", r.nome()).param("tipo", r.tipo().name()).param("ativo", r.ativo())
                .param("id", r.id()).param("c", r.clinicaId()).param("versao", r.versao()).update());
        if (linhas == 0) {
            throw new ConflitoDeVersaoException();
        }
        r.incrementarVersao();
    }

    private static int traduzindo(java.util.function.IntSupplier comando) {
        try {
            return comando.getAsInt();
        } catch (DataAccessException e) {
            if (ErrosSql.violou(e, ErrosSql.VIOLACAO_UNICIDADE, "recurso_nome_uk")) {
                throw new NomeDuplicadoException("Já existe uma sala com este nome");
            }
            throw e;
        }
    }

    private static final RowMapper<Recurso> RECURSO = (rs, n) -> Recurso.reconstituir(
            rs.getObject("id", UUID.class), rs.getObject("clinica_id", UUID.class), rs.getString("nome"),
            TipoRecurso.valueOf(rs.getString("tipo")), rs.getBoolean("ativo"), rs.getInt("versao"));
}
