package br.com.agendafono.pacientes.adapter.out.persistence;

import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.application.port.PacienteRepository;
import br.com.agendafono.pacientes.domain.Paciente;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static br.com.agendafono.pacientes.adapter.out.persistence.ResponsavelJdbcRepository.instant;
import static br.com.agendafono.pacientes.adapter.out.persistence.ResponsavelJdbcRepository.utc;

@Repository
class PacienteJdbcRepository implements PacienteRepository {

    private static final String COLUNAS =
            "id, clinica_id, responsavel_id, nome, data_nascimento, demanda, ativo, anonimizado_em, versao";

    /** Termo só com dígitos e pontuação de telefone, com pelo menos 4 dígitos. */
    private static final Pattern TERMO_TELEFONE = Pattern.compile("^[0-9\\s()+\\-.]*$");

    private final JdbcClient jdbc;

    PacienteJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Paciente> buscar(UUID clinicaId, UUID pacienteId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM paciente WHERE clinica_id = :clinica AND id = :id")
                .param("clinica", clinicaId)
                .param("id", pacienteId)
                .query(PACIENTE)
                .optional();
    }

    @Override
    public List<Paciente> doResponsavel(UUID clinicaId, UUID responsavelId) {
        return jdbc.sql("SELECT " + COLUNAS + " FROM paciente"
                        + " WHERE clinica_id = :clinica AND responsavel_id = :responsavel ORDER BY criado_em, id")
                .param("clinica", clinicaId)
                .param("responsavel", responsavelId)
                .query(PACIENTE)
                .list();
    }

    @Override
    public void inserir(Paciente p) {
        jdbc.sql("""
                        INSERT INTO paciente (id, clinica_id, responsavel_id, nome, data_nascimento, demanda, ativo,
                                              anonimizado_em, versao)
                        VALUES (:id, :clinica, :responsavel, :nome, :nascimento, :demanda, :ativo, :anonimizado,
                                :versao)
                        """)
                .param("id", p.id())
                .param("clinica", p.clinicaId())
                .param("responsavel", p.responsavelId())
                .param("nome", p.nome())
                .param("nascimento", p.dataNascimento() != null ? Date.valueOf(p.dataNascimento()) : null)
                .param("demanda", p.demanda() != null ? p.demanda().name() : null)
                .param("ativo", p.ativo())
                .param("anonimizado", utc(p.anonimizadoEm()))
                .param("versao", p.versao())
                .update();
    }

    @Override
    public void atualizar(Paciente p) {
        int linhas = jdbc.sql("""
                        UPDATE paciente
                           SET nome = :nome,
                               data_nascimento = :nascimento,
                               demanda = :demanda,
                               ativo = :ativo,
                               anonimizado_em = :anonimizado,
                               versao = versao + 1,
                               atualizado_em = now()
                         WHERE id = :id AND clinica_id = :clinica AND versao = :versao
                        """)
                .param("nome", p.nome())
                .param("nascimento", p.dataNascimento() != null ? Date.valueOf(p.dataNascimento()) : null)
                .param("demanda", p.demanda() != null ? p.demanda().name() : null)
                .param("ativo", p.ativo())
                .param("anonimizado", utc(p.anonimizadoEm()))
                .param("id", p.id())
                .param("clinica", p.clinicaId())
                .param("versao", p.versao())
                .update();
        if (linhas == 0) {
            throw new RegistroDesatualizadoException();
        }
        p.incrementarVersao();
    }

    @Override
    public Pagina<LinhaPesquisa> pesquisar(UUID clinicaId, String termo, Boolean ativo, int pagina, int tamanho) {
        StringBuilder filtro = new StringBuilder(
                " FROM paciente p JOIN responsavel r ON r.id = p.responsavel_id AND r.clinica_id = p.clinica_id"
                        + " WHERE p.clinica_id = :clinica AND p.anonimizado_em IS NULL");
        Map<String, Object> params = new HashMap<>();
        params.put("clinica", clinicaId);

        if (ativo != null) {
            filtro.append(" AND p.ativo = :ativo");
            params.put("ativo", ativo);
        }
        if (termo != null) {
            String digitos = termo.replaceAll("\\D", "");
            if (TERMO_TELEFONE.matcher(termo).matches() && digitos.length() >= 4) {
                filtro.append(" AND r.telefone_e164 LIKE :padrao");
                params.put("padrao", "%" + digitos + "%");
            } else {
                filtro.append(" AND (lower(p.nome) LIKE :padrao ESCAPE '\\' OR lower(r.nome) LIKE :padrao ESCAPE '\\')");
                params.put("padrao", "%" + escaparLike(termo.toLowerCase()) + "%");
            }
        }

        long total = jdbc.sql("SELECT count(*)" + filtro).params(params).query(Long.class).single();

        params.put("limite", tamanho);
        params.put("deslocamento", (long) pagina * tamanho);
        List<LinhaPesquisa> linhas = jdbc.sql("""
                        SELECT p.id, p.nome, p.data_nascimento, p.demanda, p.ativo,
                               r.id AS responsavel_id, r.nome AS responsavel_nome, r.telefone_e164""" + filtro
                        + " ORDER BY lower(p.nome), p.id LIMIT :limite OFFSET :deslocamento")
                .params(params)
                .query((rs, n) -> new LinhaPesquisa(
                        rs.getObject("id", UUID.class),
                        rs.getString("nome"),
                        data(rs.getDate("data_nascimento")),
                        rs.getString("demanda"),
                        rs.getBoolean("ativo"),
                        rs.getObject("responsavel_id", UUID.class),
                        rs.getString("responsavel_nome"),
                        rs.getString("telefone_e164")))
                .list();
        return new Pagina<>(linhas, pagina, tamanho, total);
    }

    @Override
    public Map<UUID, String> nomes(UUID clinicaId, Collection<UUID> pacienteIds) {
        if (pacienteIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> nomes = new HashMap<>();
        jdbc.sql("SELECT id, nome FROM paciente WHERE clinica_id = :clinica AND id IN (:ids)")
                .param("clinica", clinicaId)
                .param("ids", List.copyOf(pacienteIds))
                .query((java.sql.ResultSet rs) -> {
                    nomes.put(rs.getObject("id", UUID.class), rs.getString("nome"));
                });
        return nomes;
    }

    static String escaparLike(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static LocalDate data(Date d) {
        return d != null ? d.toLocalDate() : null;
    }

    private static final RowMapper<Paciente> PACIENTE = (rs, n) -> {
        String demanda = rs.getString("demanda");
        return Paciente.reconstituir(
                rs.getObject("id", UUID.class),
                rs.getObject("clinica_id", UUID.class),
                rs.getObject("responsavel_id", UUID.class),
                rs.getString("nome"),
                data(rs.getDate("data_nascimento")),
                demanda != null ? Demanda.valueOf(demanda) : null,
                rs.getBoolean("ativo"),
                instant(rs, "anonimizado_em"),
                rs.getInt("versao"));
    };
}
