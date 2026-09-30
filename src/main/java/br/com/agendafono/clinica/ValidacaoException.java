package br.com.agendafono.clinica;

public class ValidacaoException extends ClinicaException {

    public ValidacaoException(String mensagem) {
        super("dados-invalidos", mensagem);
    }
}
