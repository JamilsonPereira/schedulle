package br.com.agendafono.clinica.adapter.out.persistence;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.GradeSobrepostaException;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.domain.IntervaloGrade;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.compartilhado.persistencia.ErrosSql;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.csv;
import static br.com.agendafono.clinica.adapter.out.persistence.Jdbc.textos;

@Repository
class ProfissionalJdbcRepository implements ProfissionalRepository {

    private static final String COLUNAS =
            "id, clinica_id, usuario_id, nome, registro_crfa, subareas, duracao_padrao_min, ativo, versao";

    private final JdbcClient jdbc;

    ProfissionalJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Profissional> buscar(UUID clinicaId, UUID profissionalId) {
        List<Linha> linhas = jdbc.sql("SELECT " + COLUNAS + " FROM profissional WHERE clinica_id = :clinica AND id = :id")
                .param("clinica", clinicaId).param("id", profissionalId)
                .query(this::linha).list();
        return comGrade(linhas).stream().findFirst();
    }

    @Override
    public Optional<Profissional> doUsuario(UUID clinicaId, UUID usuarioId) {
        List<Linha> linhas = jdbc.sql("SELECT " + COLUNAS
                        + " FROM profissional WHERE clinica_id = :clinica AND usuario_id = :usuario")
                .param("clinica", clinicaId).param("usuario", usuarioId)
                .query(this::linha).list();
        return comGrade(linhas).stream().findFirst();
    }

    @Override
    public List<Profissional> listar(UUID clinicaId, boolean apenasAtivos, Subarea subarea) {
        String sql = "SELECT " + COLUNAS + " FROM profissional WHERE clinica_id = :clinica"
                + (apenasAtivos ? " AND ativo" : "")
                + (subarea != null ? " AND :subarea = ANY (subareas)" : "")
                + " ORDER BY lower(nome), id";
        var consulta = jdbc.sql(sql).param("clinica", clinicaId);
        if (subarea != null) {
            consulta = consulta.param("subarea", subarea.name());
        }
        return comGrade(consulta.query(this::linha).list());
    }

    @Override
    public void inserir(Profissional p) {
        traduzindo(() -> jdbc.sql("""
                        INSERT INTO profissional (id, clinica_id, usuario_id, nome, registro_crfa, subareas,
                                                  duracao_padrao_min, ativo, versao)
                        VALUES (:id, :clinica, :usuario, :nome, :registro, string_to_array(:subareas, ','),
                                :duracao, :ativo, :versao)
                        """)
                .param("id", p.id()).param("clinica", p.clinicaId()).param("usuario", p.usuarioId())
                .param("nome", p.nome()).param("registro", p.registroCrfa()).param("subareas", csv(p.subareas()))
                .param("duracao", p.duracaoPadraoMin()).param("ativo", p.ativo()).param("versao", p.versao())
                .update());
        gravarGrade(p);
    }

    @Override
    public void atualizar(Profissional p) {
        int linhas = traduzindo(() -> jdbc.sql("""
                        UPDATE profissional
                           SET usuario_id = :usuario, nome = :nome, registro_crfa = :registro,
                               subareas = string_to_array(:subareas, ','), duracao_padrao_min = :duracao,
                               ativo = :ativo, versao = versao + 1, atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("usuario", p.usuarioId()).param("nome", p.nome()).param("registro", p.registroCrfa())
                .param("subareas", csv(p.subareas())).param("duracao", p.duracaoPadraoMin())
                .param("ativo", p.ativo()).param("id", p.id()).param("clinica", p.clinicaId())
                .param("versao", p.versao())
                .update());
        if (linhas == 0) {
            throw new ConflitoDeVersaoException();
        }
        p.incrementarVersao();
        gravarGrade(p);
    }

    private void gravarGrade(Profissional p) {
        jdbc.sql("DELETE FROM grade_semanal WHERE profissional_id = :id").param("id", p.id()).update();
        for (IntervaloGrade i : p.grade()) {
            traduzindo(() -> jdbc.sql("""
                            INSERT INTO grade_semanal (profissional_id, dia_semana, hora_inicio, hora_fim, recurso_id)
                            VALUES (:profissional, :dia, :inicio, :fim, :recurso)
                            """)
                    .param("profissional", p.id()).param("dia", i.dia().getValue())
                    .param("inicio", Time.valueOf(i.inicio())).param("fim", Time.valueOf(i.fim()))
                    .param("recurso", i.recursoId())
                    .update());
        }
    }

    private List<Profissional> comGrade(List<Linha> linhas) {
        if (linhas.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<IntervaloGrade>> grades = new HashMap<>();
        jdbc.sql("""
                        SELECT profissional_id, dia_semana, hora_inicio, hora_fim, recurso_id
                          FROM grade_semanal WHERE profissional_id IN (:ids)
                         ORDER BY dia_semana, hora_inicio
                        """)
                .param("ids", linhas.stream().map(Linha::id).toList())
                .query((ResultSet rs) -> {
                    grades.computeIfAbsent(rs.getObject("profissional_id", UUID.class), k -> new ArrayList<>())
                            .add(new IntervaloGrade(DayOfWeek.of(rs.getInt("dia_semana")),
                                    rs.getTime("hora_inicio").toLocalTime(), rs.getTime("hora_fim").toLocalTime(),
                                    rs.getObject("recurso_id", UUID.class)));
                });
        return linhas.stream().map(l -> Profissional.reconstituir(l.id(), l.clinicaId(), l.usuarioId(), l.nome(),
                l.registro(), l.subareas(), l.duracao(), l.ativo(), grades.getOrDefault(l.id(), List.of()),
                l.versao())).toList();
    }

    private Linha linha(ResultSet rs, int n) throws SQLException {
        Set<Subarea> subareas = EnumSet.noneOf(Subarea.class);
        for (String s : textos(rs, "subareas")) {
            try {
                subareas.add(Subarea.valueOf(s));
            } catch (IllegalArgumentException ignorado) {
                // valor antigo fora da lista é descartado
            }
        }
        return new Linha(rs.getObject("id", UUID.class), rs.getObject("clinica_id", UUID.class),
                rs.getObject("usuario_id", UUID.class), rs.getString("nome"), rs.getString("registro_crfa"),
                subareas, rs.getInt("duracao_padrao_min"), rs.getBoolean("ativo"), rs.getInt("versao"));
    }

    private static int traduzindo(java.util.function.IntSupplier comando) {
        try {
            return comando.getAsInt();
        } catch (DataAccessException e) {
            if (ErrosSql.violou(e, ErrosSql.VIOLACAO_EXCLUSAO, "grade_sem_sobreposicao")) {
                throw new GradeSobrepostaException();
            }
            if (ErrosSql.violou(e, ErrosSql.VIOLACAO_UNICIDADE, "profissional_usuario_uk")) {
                throw new ValidacaoException("Este usuário já está vinculado a outro profissional");
            }
            throw e;
        }
    }

    private record Linha(UUID id, UUID clinicaId, UUID usuarioId, String nome, String registro,
                         Set<Subarea> subareas, int duracao, boolean ativo, int versao) {
    }
}
