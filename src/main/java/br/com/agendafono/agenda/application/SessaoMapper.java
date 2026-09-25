package br.com.agendafono.agenda.application;

import br.com.agendafono.agenda.SessaoView;
import br.com.agendafono.agenda.domain.Sessao;

final class SessaoMapper {

    private SessaoMapper() {
    }

    static SessaoView view(Sessao s) {
        return new SessaoView(s.id(), s.clinicaId(), s.pacienteId(), s.profissionalId(), s.recursoId(),
                s.serieId(), s.tipo(), s.periodo().inicio(), s.periodo().fim(), s.status(), s.expiraEm(),
                s.versao());
    }
}
