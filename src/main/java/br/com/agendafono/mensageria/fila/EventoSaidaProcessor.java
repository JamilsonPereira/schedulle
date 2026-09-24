package br.com.agendafono.mensageria.fila;

import br.com.agendafono.mensageria.ProvedorWhatsApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventoSaidaProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventoSaidaProcessor.class);

    private final MensageriaFilaRepository filaRepository;
    private final ProvedorWhatsApp provedorWhatsApp;

    public EventoSaidaProcessor(MensageriaFilaRepository filaRepository, ProvedorWhatsApp provedorWhatsApp) {
        this.filaRepository = filaRepository;
        this.provedorWhatsApp = provedorWhatsApp;
    }

    @Scheduled(fixedDelayString = "${mensageria.saida.fixed-delay:PT1S}")
    public void processarProximo() {
        filaRepository.proximaSaida().ifPresent(this::processar);
    }

    private void processar(EventoSaida evento) {
        try {
            provedorWhatsApp.enviarTexto(evento.phoneNumberId(), evento.telefone(), evento.texto());
            filaRepository.marcarSaidaEnviada(evento.id());
        } catch (RuntimeException e) {
            filaRepository.marcarSaidaComErro(evento.id(), e.getMessage());
            log.warn("Falha ao enviar evento de saida {}", evento.id(), e);
        }
    }
}
