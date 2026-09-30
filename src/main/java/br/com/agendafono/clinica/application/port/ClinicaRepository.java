package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.Clinica;

import java.util.Optional;
import java.util.UUID;

public interface ClinicaRepository {

    Optional<Clinica> buscar(UUID clinicaId);

    void inserir(Clinica clinica);

    /** @throws br.com.agendafono.clinica.ConflitoDeVersaoException */
    void atualizar(Clinica clinica);
}
