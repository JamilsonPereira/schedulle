package br.com.agendafono.pacientes.application.port;

import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.domain.Paciente;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface PacienteRepository {

    Optional<Paciente> buscar(UUID clinicaId, UUID pacienteId);

    List<Paciente> doResponsavel(UUID clinicaId, UUID responsavelId);

    void inserir(Paciente paciente);

    /** @throws RegistroDesatualizadoException */
    void atualizar(Paciente paciente);

    /**
     * Pesquisa por parte do nome do paciente ou do responsável, ou por dígitos do telefone.
     * Anonimizados nunca aparecem.
     */
    Pagina<LinhaPesquisa> pesquisar(UUID clinicaId, String termo, Boolean ativo, int pagina, int tamanho);

    /** Nome dos pacientes informados (anonimizados vêm com o nome já substituído). */
    Map<UUID, String> nomes(UUID clinicaId, Collection<UUID> pacienteIds);

    record LinhaPesquisa(UUID id, String nome, LocalDate dataNascimento, String demanda, boolean ativo,
                         UUID responsavelId, String responsavelNome, String telefoneE164) {
    }
}
