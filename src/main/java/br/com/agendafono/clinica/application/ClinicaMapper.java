package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.Views.BloqueioView;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.IntervaloGradeView;
import br.com.agendafono.clinica.Views.PoliticaView;
import br.com.agendafono.clinica.Views.ProfissionalView;
import br.com.agendafono.clinica.Views.RecursoView;
import br.com.agendafono.clinica.Views.UsuarioView;
import br.com.agendafono.clinica.domain.Bloqueio;
import br.com.agendafono.clinica.domain.Clinica;
import br.com.agendafono.clinica.domain.PoliticaClinica;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.clinica.domain.Recurso;
import br.com.agendafono.clinica.domain.Usuario;

import java.time.Instant;
import java.util.UUID;

final class ClinicaMapper {

    private ClinicaMapper() {
    }

    static ClinicaView view(Clinica c) {
        PoliticaClinica p = c.politica();
        return new ClinicaView(c.id(), c.nome(), c.fuso().getId(),
                new PoliticaView(p.antecedenciaMinimaMin(), p.janelaMaximaDias(), p.passoMin(), p.ttlReservaMin(),
                        p.antecedenciaAvisoFaltaHoras()),
                c.versao());
    }

    static ProfissionalView view(Profissional p) {
        return new ProfissionalView(p.id(), p.nome(), p.registroCrfa(), p.subareas(), p.duracaoPadraoMin(),
                p.usuarioId(), p.ativo(),
                p.grade().stream().map(i -> new IntervaloGradeView(i.dia(), i.inicio(), i.fim(), i.recursoId()))
                        .toList(),
                p.versao());
    }

    static RecursoView view(Recurso r) {
        return new RecursoView(r.id(), r.nome(), r.tipo(), r.ativo(), r.versao());
    }

    static BloqueioView view(Bloqueio b) {
        return new BloqueioView(b.id(), b.profissionalId(), b.inicio(), b.fim(), b.motivo());
    }

    static UsuarioView view(Usuario u, UUID profissionalId, Instant agora) {
        boolean bloqueado = u.bloqueadoAte() != null && u.bloqueadoAte().isAfter(agora);
        return new UsuarioView(u.id(), u.nome(), u.email(), u.papeis(), u.ativo(), u.precisaTrocarSenha(),
                bloqueado, u.ultimoLoginEm(), profissionalId, u.versao());
    }
}
