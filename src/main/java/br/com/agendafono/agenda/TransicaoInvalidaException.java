package br.com.agendafono.agenda;

/** A ação pedida não é permitida no status atual da sessão. */
public class TransicaoInvalidaException extends AgendaException {

    public TransicaoInvalidaException(StatusSessao atual, String acao) {
        super("transicao-invalida", "Não é possível " + acao + " uma sessão com status " + atual);
    }

    public TransicaoInvalidaException(String mensagem) {
        super("transicao-invalida", mensagem);
    }
}
