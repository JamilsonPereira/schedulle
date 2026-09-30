package br.com.agendafono.clinica;

public class CredenciaisInvalidasException extends ClinicaException {

    public CredenciaisInvalidasException() {
        super("credenciais-invalidas", "E-mail ou senha inválidos, ou acesso temporariamente bloqueado");
    }
}
