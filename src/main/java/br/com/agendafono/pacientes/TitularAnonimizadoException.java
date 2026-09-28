package br.com.agendafono.pacientes;

public class TitularAnonimizadoException extends PacientesException {

    public TitularAnonimizadoException() {
        super("titular-anonimizado", "Os dados deste titular foram anonimizados e não podem mais ser alterados");
    }
}
