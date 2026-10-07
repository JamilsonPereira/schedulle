package br.com.agendafono.bot;

/**
 * Porta de entrada do bot. A mensageria chama uma vez por mensagem recebida, em ordem por contato (lock por
 * telefone no worker). As respostas vão para a fila de saída na mesma transação.
 */
public interface Bot {

    void processar(MensagemDoContato mensagem);
}
