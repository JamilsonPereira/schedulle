package br.com.agendafono.mensageria.fila;

import br.com.agendafono.bot.BotEcoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EventoEntradaProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventoEntradaProcessor.class);

    private final MensageriaFilaRepository filaRepository;
    private final BotEcoService botEcoService;

    public EventoEntradaProcessor(MensageriaFilaRepository filaRepository, BotEcoService botEcoService) {
        this.filaRepository = filaRepository;
        this.botEcoService = botEcoService;
    }

    @Scheduled(fixedDelayString = "${mensageria.entrada.fixed-delay:PT1S}")
    @Transactional
    public void processarProximo() {
        filaRepository.proximaEntrada().ifPresent(this::processar);
    }

    private void processar(EventoEntrada evento) {
        try {
            filaRepository.registrarMensagemEntrada(evento);
            filaRepository.criarSaidaTexto(
                    evento.clinicaId(),
                    evento.phoneNumberId(),
                    evento.telefone(),
                    botEcoService.responder(evento));
            filaRepository.marcarEntradaProcessada(evento.id());
        } catch (RuntimeException e) {
            filaRepository.marcarEntradaComErro(evento.id(), e.getMessage());
            log.warn("Falha ao processar evento de entrada {}", evento.id(), e);
        }
    }
}
