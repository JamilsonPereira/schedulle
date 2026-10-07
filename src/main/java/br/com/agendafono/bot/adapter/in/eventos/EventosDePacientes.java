package br.com.agendafono.bot.adapter.in.eventos;

import br.com.agendafono.bot.application.port.ConversaRepository;
import br.com.agendafono.pacientes.Eventos.TitularAnonimizado;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Anonimização do titular (LGPD): a conversa pode guardar o nome do paciente no contexto, então é apagada na
 * mesma transação. Revogação de consentimento não precisa de nada aqui: o motor volta ao consentimento na
 * próxima mensagem.
 */
@Component
class EventosDePacientes {

    private final ConversaRepository conversas;

    EventosDePacientes(ConversaRepository conversas) {
        this.conversas = conversas;
    }

    @EventListener
    void aoAnonimizar(TitularAnonimizado evento) {
        conversas.apagarDoResponsavel(evento.clinicaId(), evento.responsavelId());
    }
}
