package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.fila.MensageriaFilaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class WebhookEntranteService {

    private static final Logger log = LoggerFactory.getLogger(WebhookEntranteService.class);

    private final WhatsAppWebhookParser parser;
    private final MensageriaFilaRepository filaRepository;

    public WebhookEntranteService(WhatsAppWebhookParser parser, MensageriaFilaRepository filaRepository) {
        this.parser = parser;
        this.filaRepository = filaRepository;
    }

    @Transactional
    public ResultadoEnfileiramento enfileirar(byte[] rawBody) {
        List<MensagemWebhook> mensagens = extrair(rawBody);
        int inseridas = 0;
        int duplicadas = 0;
        int desconhecidas = 0;

        for (MensagemWebhook mensagem : mensagens) {
            UUID clinicaId = filaRepository.buscarClinicaPorPhoneNumberId(mensagem.phoneNumberId()).orElse(null);
            if (clinicaId == null) {
                desconhecidas++;
                log.warn("Webhook com phone_number_id nao cadastrado recebido");
                continue;
            }
            if (filaRepository.inserirEntrada(clinicaId, mensagem, rawBody)) {
                inseridas++;
            } else {
                duplicadas++;
            }
        }
        return new ResultadoEnfileiramento(inseridas, duplicadas, desconhecidas);
    }

    private List<MensagemWebhook> extrair(byte[] rawBody) {
        try {
            return parser.extrairMensagens(rawBody);
        } catch (IOException e) {
            throw new WebhookPayloadInvalidoException(e);
        }
    }

    public record ResultadoEnfileiramento(int inseridas, int duplicadas, int desconhecidas) {
    }
}
