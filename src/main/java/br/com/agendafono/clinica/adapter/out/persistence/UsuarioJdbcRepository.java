package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.EmailJaCadastradoException;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.persistencia.ErrosSql;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.csv;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.instant;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.textos;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.utc;

@Repository
class UsuarioJdbcRepository implements UsuarioRepository {

    private static final String COLUNAS = "id, clinica_id, nome, email, senha_hash, papeis, ativo, tentativas_falhas,"
            + " bloqueado_ate, precisa_trocar_senha, ultimo_login_em, versao";

    private final JdbcClient jdbc;

    UsuarioJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> buscar(UUID clinicaId, UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM usuario WHERE clinica_id = :clinica AND id = :id")
                .param("clinica", clinicaId).param("id", usuarioId)
                .query(USUARIO).optional();
    }

    @Override
    public Optional<Usuario> porEmail(String emailNormalizado) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM usuario WHERE email = :email")
                .param("email", emailNormalizado)
                .query(USUARIO).optional();
    }

    @Override
    public List<Usuario> listar(UUID clinicaId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM usuario WHERE clinica_id = :clinica ORDER BY lower(nome), id")
                .param("clinica", clinicaId)
                .query(USUARIO).list();
    }

    @Override
    public void inserir(Usuario u) {
        try {
            jdbc.sql("""
                            INSERT INTO usuario (id, clinica_id, nome, email, senha_hash, papeis, ativo,
                                                 precisa_trocar_senha, versao)
                            VALUES (:id, :clinica, :nome, :email, :hash, string_to_array(:papeis, ','), :ativo,
                                    :trocar, :versao)
                            """)
                    .param("id", u.id()).param("clinica", u.clinicaId()).param("nome", u.nome())
                    .param("email", u.email()).param("hash", u.senhaHash()).param("papeis", csv(u.papeis()))
                    .param("ativo", u.ativo()).param("trocar", u.precisaTrocarSenha()).param("versao", u.versao())
                    .update();
        } catch (DataAccessException e) {
            if (ErrosSql.violou(e, ErrosSql.VIOLACAO_UNICIDADE, "usuario_email_uk")) {
                throw new EmailJaCadastradoException();
            }
            throw e;
        }
    }

    @Override
    public void atualizar(Usuario u) {
        int linhas = jdbc.sql("""
                        UPDATE usuario
                           SET nome = :nome, senha_hash = :hash, papeis = string_to_array(:papeis, ','),
                               ativo = :ativo, precisa_trocar_senha = :trocar, tentativas_falhas = :tentativas,
                               bloqueado_ate = :bloqueado, versao = versao + 1, atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("nome", u.nome()).param("hash", u.senhaHash()).param("papeis", csv(u.papeis()))
                .param("ativo", u.ativo()).param("trocar", u.precisaTrocarSenha())
                .param("tentativas", u.tentativasFalhas()).param("bloqueado", utc(u.bloqueadoAte()))
                .param("id", u.id()).param("clinica", u.clinicaId()).param("versao", u.versao())
                .update();
        if (linhas == 0) {
            throw new ConflitoDeVersaoException();
        }
        u.incrementarVersao();
    }

    @Override
    public void atualizarEstadoDeLogin(Usuario u) {
        jdbc.sql("""
                        UPDATE usuario
                           SET tentativas_falhas = :tentativas, bloqueado_ate = :bloqueado, ultimo_login_em = :ultimo
                         WHERE id = :id AND clinica_id = :clinica
                        """)
                .param("tentativas", u.tentativasFalhas()).param("bloqueado", utc(u.bloqueadoAte()))
                .param("ultimo", utc(u.ultimoLoginEm())).param("id", u.id()).param("clinica", u.clinicaId())
                .update();
    }

    private static final RowMapper<Usuario> USUARIO = (rs, n) -> {
        Set<Papel> papeis = EnumSet.noneOf(Papel.class);
        for (String p : textos(rs, "papeis")) {
            papeis.add(Papel.valueOf(p));
        }
        return Usuario.reconstituir(
                rs.getObject("id", UUID.class), rs.getObject("clinica_id", UUID.class), rs.getString("nome"),
                rs.getString("email"), rs.getString("senha_hash"), papeis, rs.getBoolean("ativo"),
                rs.getInt("tentativas_falhas"), instant(rs, "bloqueado_ate"), rs.getBoolean("precisa_trocar_senha"),
                instant(rs, "ultimo_login_em"), rs.getInt("versao"));
    };
}
