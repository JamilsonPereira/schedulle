package br.com.agendafono.agenda;

import java.util.UUID;

/** Porta de entrada pública para registrar o resultado de uma sessão depois do seu início. */
public interface Presenca {

    enum Resultado { ATENDIDA, FALTA_SEM_AVISO }

    SessaoView registrar(UUID clinicaId, UUID sessaoId, Resultado resultado, Integer versaoEsperada);
}
