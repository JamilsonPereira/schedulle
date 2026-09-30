package br.com.agendafono.compartilhado.auditoria;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class AuditoriaJdbc implements Auditoria {

    private final JdbcClient jdbc;

    AuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(UUID clinicaId, UUID usuarioId, String acao, String entidade, UUID entidadeId) {
        jdbc.sql("""
                        INSERT INTO auditoria (clinica_id, usuario_id, acao, entidade, entidade_id)
                        VALUES (:clinica, :usuario, :acao, :entidade, :entidadeId)
                        """)
                .param("clinica", clinicaId)
                .param("usuario", usuarioId)
                .param("acao", acao)
                .param("entidade", entidade)
                .param("entidadeId", entidadeId)
                .update();
    }
}
