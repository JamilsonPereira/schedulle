package br.com.agendafono.compartilhado.auditoria;

import java.util.UUID;

/**
 * Trilha de auditoria (SDD, seção 11). Registra quem fez o quê e em qual entidade, nunca dados pessoais.
 * Grava na transação corrente; se ela for desfeita, o registro também é.
 */
public interface Auditoria {

    void registrar(UUID clinicaId, UUID usuarioId, String acao, String entidade, UUID entidadeId);
}
