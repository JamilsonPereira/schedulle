package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.domain.Profissional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfissionalRepository {

    Optional<Profissional> buscar(UUID clinicaId, UUID profissionalId);

    Optional<Profissional> doUsuario(UUID clinicaId, UUID usuarioId);

    /** {@code subarea} nula = todas; {@code apenasAtivos} filtra inativos. */
    List<Profissional> listar(UUID clinicaId, boolean apenasAtivos, Subarea subarea);

    void inserir(Profissional profissional);

    /** Grava dados e substitui a grade inteira. @throws br.com.agendafono.clinica.ConflitoDeVersaoException */
    void atualizar(Profissional profissional);
}
