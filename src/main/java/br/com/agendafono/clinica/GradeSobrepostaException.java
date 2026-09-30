package br.com.agendafono.clinica;

public class GradeSobrepostaException extends ClinicaException {

    public GradeSobrepostaException() {
        super("grade-sobreposta", "Há intervalos da grade que se sobrepõem no mesmo dia");
    }
}
