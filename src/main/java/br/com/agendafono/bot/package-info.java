/**
 * Bot de agendamento no WhatsApp: máquina de estados determinística (SDD backend, seção 8).
 *
 * <p>API pública (este pacote): {@link br.com.agendafono.bot.Bot}, chamado pela mensageria para cada mensagem
 * recebida; {@link br.com.agendafono.bot.AtendimentoHumano}, a fila da recepção; e o evento
 * {@link br.com.agendafono.bot.TransbordoSolicitado}. O bot usa agenda, pacientes e clínica
 * só pelas APIs públicas desses módulos e grava as respostas na fila de saída (outbox) da mensageria.
 */
package br.com.agendafono.bot;
