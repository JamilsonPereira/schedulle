package br.com.agendafono.clinica;

public class SenhaFracaException extends ClinicaException {

    public SenhaFracaException(String mensagem) {
        super("senha-fraca", mensagem);
    }
}
