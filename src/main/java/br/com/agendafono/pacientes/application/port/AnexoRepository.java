package br.com.agendafono.pacientes.application.port;

import br.com.agendafono.pacientes.domain.Anexo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnexoRepository {

    void inserir(Anexo anexo);

    Optional<Anexo> buscar(UUID clinicaId, UUID anexoId);

    List<Anexo> doPaciente(UUID clinicaId, UUID pacienteId);

    List<Anexo> doResponsavel(UUID clinicaId, UUID responsavelId);

    void remover(UUID clinicaId, UUID anexoId);
}
