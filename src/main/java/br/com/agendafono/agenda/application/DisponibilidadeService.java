package br.com.agendafono.agenda.application;

import br.com.agendafono.agenda.Disponibilidade;
import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.application.port.ConfiguracaoAgendaPort.ConfiguracaoProfissional;
import br.com.agendafono.compartilhado.Relogio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
class DisponibilidadeService implements Disponibilidade {

    private final AgendaDoProfissional agenda;
    private final Relogio relogio;

    DisponibilidadeService(AgendaDoProfissional agenda, Relogio relogio) {
        this.agenda = agenda;
        this.relogio = relogio;
    }

    @Override
    public List<HorarioLivre> consultar(Consulta consulta) {
        ConfiguracaoProfissional cfg = agenda.configuracao(consulta.clinicaId(), consulta.profissionalId());
        return agenda.livres(consulta.clinicaId(), cfg, consulta.de(), consulta.ate(),
                agenda.duracao(consulta.duracaoMin(), cfg), consulta.origem(), relogio.agora());
    }
}
