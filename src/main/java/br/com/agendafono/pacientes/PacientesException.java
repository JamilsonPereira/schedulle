package br.com.agendafono.pacientes;

/**
 * Erro de regra do módulo pacientes. O {@link #codigo()} vira o {@code type} do Problem Details.
 * As mensagens nunca incluem dados pessoais (nome, telefone), porque podem acabar em logs.
 */
public abstract class PacientesException extends RuntimeException {

    private final String codigo;

    protected PacientesException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
