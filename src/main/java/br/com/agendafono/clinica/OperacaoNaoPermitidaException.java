package br.com.agendafono.clinica;

public class OperacaoNaoPermitidaException extends ClinicaException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super("operacao-nao-permitida", mensagem);
    }
}
