package br.com.agendafono.clinica;

/** Erro de regra do módulo clínica; {@link #codigo()} vira o {@code type} do Problem Details. */
public abstract class ClinicaException extends RuntimeException {

    private final String codigo;

    protected ClinicaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
