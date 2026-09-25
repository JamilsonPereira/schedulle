package br.com.agendafono.agenda;

/**
 * Erro de regra de negócio da agenda. O {@link #codigo()} vira o {@code type} do Problem Details
 * (ex.: {@code /erros/horario-indisponivel}), estável para o painel e para o bot.
 */
public abstract class AgendaException extends RuntimeException {

    private final String codigo;

    protected AgendaException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
