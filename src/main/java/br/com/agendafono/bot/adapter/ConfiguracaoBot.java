package br.com.agendafono.bot.adapter;

import br.com.agendafono.bot.application.ServicoBot;
import br.com.agendafono.bot.application.TradutorDeEntrada;
import br.com.agendafono.bot.application.etapas.Etapas;
import br.com.agendafono.bot.application.etapas.Fluxos;
import br.com.agendafono.bot.application.port.ConversaRepository;
import br.com.agendafono.bot.application.port.SaidaWhatsApp;
import br.com.agendafono.bot.application.port.ServicosDaClinica;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Monta o motor do bot. As etapas e o motor não conhecem Spring; só este arquivo e os adaptadores. */
@Configuration(proxyBeanMethods = false)
class ConfiguracaoBot {

    @Bean
    Etapas etapasDoBot(ServicosDaClinica servicos,
                       @Value("${bot.politica-privacidade-url:https://agendafono.com.br/privacidade}") String url) {
        return new Etapas(new Fluxos(servicos), url);
    }

    @Bean
    ServicoBot servicoBot(ConversaRepository conversas, ServicosDaClinica servicos, SaidaWhatsApp saida,
                          Etapas etapasDoBot, ObjectMapper json, ApplicationEventPublisher eventos) {
        return new ServicoBot(conversas, servicos, saida, etapasDoBot, new TradutorDeEntrada(json), eventos,
                Clock.systemUTC());
    }
}
