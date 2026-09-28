package br.com.agendafono.pacientes;

import java.util.UUID;

public class NaoEncontradoException extends PacientesException {

    public NaoEncontradoException(String tipo, UUID id) {
        super("nao-encontrado", tipo + " não encontrado: " + id);
    }
}
