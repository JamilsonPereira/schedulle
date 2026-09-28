package br.com.agendafono.pacientes;

public class AnexoInvalidoException extends PacientesException {

    public AnexoInvalidoException(String mensagem) {
        super("anexo-invalido", mensagem);
    }
}
