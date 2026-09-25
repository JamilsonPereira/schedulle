package br.com.agendafono.agenda;

import java.util.UUID;

/** Sessão ou profissional inexistente (ou de outra clínica). */
public class RecursoNaoEncontradoException extends AgendaException {

    public RecursoNaoEncontradoException(String tipo, UUID id) {
        super("nao-encontrado", tipo + " não encontrado: " + id);
    }
}
