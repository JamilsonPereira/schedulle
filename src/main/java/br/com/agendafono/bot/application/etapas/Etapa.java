package br.com.agendafono.bot.application.etapas;

import br.com.agendafono.bot.domain.ContextoConversa;
import br.com.agendafono.bot.domain.Entrada;
import br.com.agendafono.bot.domain.EstadoConversa;
import br.com.agendafono.bot.domain.Transicao;

/** Um estado da conversa: recebe a mensagem e o contexto e devolve a transição (SDD backend, seção 8). */
public interface Etapa {

    EstadoConversa estado();

    Transicao tratar(Entrada entrada, ContextoConversa contexto, Situacao situacao);
}
