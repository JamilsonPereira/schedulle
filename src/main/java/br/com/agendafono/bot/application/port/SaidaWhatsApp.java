package br.com.agendafono.bot.application.port;

import br.com.agendafono.bot.domain.MensagemSaida;

import java.util.List;
import java.util.UUID;

/** Grava as respostas na fila de saída da mensageria (outbox), na transação corrente. */
public interface SaidaWhatsApp {

    void enfileirar(UUID clinicaId, String phoneNumberId, String telefone, List<MensagemSaida> mensagens);
}
