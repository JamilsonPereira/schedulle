package br.com.agendafono.clinica;

public class NomeDuplicadoException extends ClinicaException {

    public NomeDuplicadoException(String mensagem) {
        super("nome-duplicado", mensagem);
    }
}
