package br.com.agendafono.clinica;

import br.com.agendafono.clinica.Views.ClinicaView;

import java.util.Optional;
import java.util.UUID;

public interface ClinicaConsulta {

    Optional<ClinicaView> clinica(UUID clinicaId);
}
