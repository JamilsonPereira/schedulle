package br.com.agendafono.pacientes.application.port;

import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TelefoneJaCadastradoException;
import br.com.agendafono.pacientes.domain.RegistroConsentimento;
import br.com.agendafono.pacientes.domain.Responsavel;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResponsavelRepository {

    Optional<Responsavel> buscar(UUID clinicaId, UUID responsavelId);

    /** Primeiro que casar com qualquer um dos telefones (variantes com/sem nono dígito). */
    Optional<Responsavel> buscarPorTelefone(UUID clinicaId, Collection<Telefone> telefones);

    /** Grava o responsável e os registros novos de consentimento. @throws TelefoneJaCadastradoException */
    void inserir(Responsavel responsavel);

    /** @throws RegistroDesatualizadoException @throws TelefoneJaCadastradoException */
    void atualizar(Responsavel responsavel);

    List<RegistroConsentimento> historicoConsentimento(UUID clinicaId, UUID responsavelId);
}
