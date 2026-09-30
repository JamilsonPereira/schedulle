package br.com.agendafono.clinica;

public class SessaoExpiradaException extends ClinicaException {

    public SessaoExpiradaException() {
        super("sessao-expirada", "Sessão expirada; faça login novamente");
    }
}
