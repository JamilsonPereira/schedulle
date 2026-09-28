package br.com.agendafono.pacientes;

public class DadosInvalidosException extends PacientesException {

    public DadosInvalidosException(String mensagem) {
        super("dados-invalidos", mensagem);
    }
}
