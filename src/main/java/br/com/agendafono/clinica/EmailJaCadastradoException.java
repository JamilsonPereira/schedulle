package br.com.agendafono.clinica;

public class EmailJaCadastradoException extends ClinicaException {

    public EmailJaCadastradoException() {
        super("email-ja-cadastrado", "Já existe um usuário com este e-mail");
    }
}
