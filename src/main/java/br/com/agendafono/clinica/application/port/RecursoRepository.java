package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.Recurso;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecursoRepository {

    Optional<Recurso> buscar(UUID clinicaId, UUID recursoId);

    List<Recurso> listar(UUID clinicaId);

    /** Quantos dos IDs informados são salas ativas desta clínica. */
    int contarAtivos(UUID clinicaId, Collection<UUID> ids);

    /** @throws br.com.agendafono.clinica.NomeDuplicadoException */
    void inserir(Recurso recurso);

    void atualizar(Recurso recurso);
}
