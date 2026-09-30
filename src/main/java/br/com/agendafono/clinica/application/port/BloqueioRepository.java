package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.Bloqueio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BloqueioRepository {

    Optional<Bloqueio> buscar(UUID clinicaId, UUID bloqueioId);

    /** Bloqueios que tocam {@code [de, ate)}; com {@code profissionalId}, inclui também os da clínica toda. */
    List<Bloqueio> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate);

    void inserir(Bloqueio bloqueio);

    void remover(UUID clinicaId, UUID bloqueioId);
}
