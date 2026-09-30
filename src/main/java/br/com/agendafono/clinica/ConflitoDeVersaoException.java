package br.com.agendafono.clinica;

public class ConflitoDeVersaoException extends ClinicaException {

    public ConflitoDeVersaoException() {
        super("versao-desatualizada", "O registro foi alterado por outra pessoa; recarregue e tente de novo");
    }
}
