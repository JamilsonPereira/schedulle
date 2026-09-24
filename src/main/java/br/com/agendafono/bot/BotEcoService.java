package br.com.agendafono.bot;

import br.com.agendafono.mensageria.fila.EventoEntrada;
import org.springframework.stereotype.Service;

@Service
public class BotEcoService {

    public String responder(EventoEntrada evento) {
        String texto = evento.texto();
        if (texto == null || texto.isBlank()) {
            return "Recebi sua mensagem. Em breve a recepcao assume por aqui.";
        }
        return "Eco: " + texto;
    }
}
