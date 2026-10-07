package br.com.agendafono.bot.application.etapas;

import br.com.agendafono.bot.TransbordoSolicitado.Motivo;
import br.com.agendafono.bot.application.port.ServicosDaClinica;
import br.com.agendafono.bot.application.port.ServicosDaClinica.BuscaDeHorarios;
import br.com.agendafono.bot.domain.ContextoConversa;
import br.com.agendafono.bot.domain.EstadoConversa;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.MensagensBot;
import br.com.agendafono.bot.domain.PacienteDoContato;
import br.com.agendafono.bot.domain.Transicao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Passos usados por mais de uma etapa: mostrar o menu, começar o agendamento e oferecer horários. */
public final class Fluxos {

    static final int HORARIOS_POR_VEZ = 5;

    private final ServicosDaClinica servicos;

    public Fluxos(ServicosDaClinica servicos) {
        this.servicos = servicos;
    }

    Transicao menu(ContextoConversa ctx, Situacao s, MensagemSaida... antes) {
        List<MensagemSaida> respostas = new ArrayList<>(List.of(antes));
        respostas.add(MensagensBot.menu(s.clinica().nome()));
        return new Transicao(EstadoConversa.MENU, ctx, respostas, Transicao.Resultado.OK, null);
    }

    /** Agendar avaliação: escolhe entre os pacientes já cadastrados ou cadastra um novo. */
    Transicao iniciarAgendamento(Situacao s) {
        List<PacienteDoContato> pacientes = servicos.pacientes(s.clinicaId(), s.responsavelId());
        ContextoConversa ctx = ContextoConversa.vazio();
        if (pacientes.isEmpty()) {
            return Transicao.para(EstadoConversa.NOVO_NOME, ctx, MensagensBot.perguntarNome());
        }
        return Transicao.para(EstadoConversa.PARA_QUEM, ctx.comPacientesOferecidos(pacientes),
                MensagensBot.paraQuem(pacientes));
    }

    /** Busca horários a partir do dia e mostra a lista; sem nenhum horário, passa para a recepção. */
    Transicao ofertarHorarios(ContextoConversa ctx, Situacao s, LocalDate aPartirDe, MensagemSaida... antes) {
        LocalDate inicio = aPartirDe == null || aPartirDe.isBefore(s.hoje()) ? s.hoje() : aPartirDe;
        BuscaDeHorarios busca = servicos.horarios(s.clinicaId(), ctx.demanda(), inicio, HORARIOS_POR_VEZ);
        if (busca.horarios().isEmpty()) {
            return Transicao.transbordar(ctx, Motivo.SEM_HORARIOS.name(), MensagensBot.semHorarios());
        }
        ContextoConversa novo = ctx.comHorarios(busca.horarios(), busca.proximaBusca());
        List<MensagemSaida> respostas = new ArrayList<>(List.of(antes));
        respostas.add(listaDeHorarios(novo, s));
        return new Transicao(EstadoConversa.ESCOLHER_HORARIO, novo, respostas, Transicao.Resultado.OK, null);
    }

    MensagemSaida listaDeHorarios(ContextoConversa ctx, Situacao s) {
        return MensagensBot.horarios(ctx.pacienteNome(), ctx.horariosOferecidos(), s.clinica().fuso(),
                ctx.buscarAPartirDe() != null);
    }

    /** Primeiro dia da oferta atual, para buscar de novo a partir dele. */
    static LocalDate primeiroDia(ContextoConversa ctx, Situacao s) {
        return ctx.horariosOferecidos().stream().map(HorarioOferta::inicio).min(java.util.Comparator.naturalOrder())
                .map(i -> i.atZone(s.clinica().fuso()).toLocalDate())
                .orElse(s.hoje());
    }

    ServicosDaClinica servicos() {
        return servicos;
    }
}
