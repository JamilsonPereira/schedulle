package br.com.agendafono.clinica;

public class RegistroNaoEncontradoException extends ClinicaException {

    public RegistroNaoEncontradoException(String tipo, java.util.UUID id) {
        super("nao-encontrado", tipo + " não encontrado: " + id);
    }
}
