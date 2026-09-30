package br.com.agendafono.clinica;

import br.com.agendafono.clinica.Views.ProfissionalView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Leitura de profissionais para outros módulos (ex.: o bot escolhe fonos pela subárea da demanda). */
public interface ProfissionalConsulta {

    Optional<ProfissionalView> profissional(UUID clinicaId, UUID profissionalId);

    /** Profissionais ativos; {@code subarea} nula = todos. */
    List<ProfissionalView> ativos(UUID clinicaId, Subarea subarea);
}
