package br.com.agendafono.pacientes;

import br.com.agendafono.pacientes.Views.ExportacaoDados;

import java.util.UUID;

/** Pedidos do titular (LGPD art. 18), sempre feitos pela clínica, que é a controladora. */
public interface DireitosDoTitular {

    /** Acesso aos dados: responsável, pacientes, histórico de consentimento e metadados dos anexos. */
    ExportacaoDados exportar(UUID clinicaId, UUID responsavelId);

    /**
     * Eliminação por anonimização: apaga nome, telefone, nascimento, demanda e arquivos; mantém sessões e
     * o histórico de consentimento sem identificação. Irreversível.
     */
    void anonimizar(UUID clinicaId, UUID responsavelId);
}
