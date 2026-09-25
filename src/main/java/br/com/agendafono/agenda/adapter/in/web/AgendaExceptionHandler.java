package br.com.agendafono.agenda.adapter.in.web;

import br.com.agendafono.agenda.AgendaException;
import br.com.agendafono.agenda.HorarioForaDaAgendaException;
import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.RecursoNaoEncontradoException;
import br.com.agendafono.agenda.ReservaExpiradaException;
import br.com.agendafono.agenda.TransicaoInvalidaException;
import br.com.agendafono.agenda.VersaoDesatualizadaException;
import br.com.agendafono.agenda.adapter.in.web.AgendaDtos.HorarioLivreResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Traduz erros da agenda para Problem Details (RFC 9457) com {@code type} estável: {@code /erros/<codigo>}. */
@RestControllerAdvice(assignableTypes = {SessaoController.class, DisponibilidadeController.class})
class AgendaExceptionHandler {

    @ExceptionHandler(AgendaException.class)
    ProblemDetail agenda(AgendaException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status(e), e.getMessage());
        problema.setType(URI.create("/erros/" + e.codigo()));
        problema.setTitle(titulo(e));
        if (e instanceof HorarioIndisponivelException indisponivel) {
            problema.setProperty("alternativas",
                    indisponivel.alternativas().stream().map(HorarioLivreResponse::de).toList());
        }
        if (e instanceof HorarioForaDaAgendaException fora) {
            problema.setProperty("motivo", fora.motivo());
        }
        return problema;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problema.setType(URI.create("/erros/requisicao-invalida"));
        problema.setTitle("Requisição inválida");
        return problema;
    }

    private static HttpStatus status(AgendaException e) {
        return switch (e) {
            case HorarioIndisponivelException ignored -> HttpStatus.CONFLICT;
            case VersaoDesatualizadaException ignored -> HttpStatus.CONFLICT;
            case ReservaExpiradaException ignored -> HttpStatus.CONFLICT;
            case RecursoNaoEncontradoException ignored -> HttpStatus.NOT_FOUND;
            case TransicaoInvalidaException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case HorarioForaDaAgendaException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static String titulo(AgendaException e) {
        return switch (e) {
            case HorarioIndisponivelException ignored -> "Horário indisponível";
            case VersaoDesatualizadaException ignored -> "Versão desatualizada";
            case ReservaExpiradaException ignored -> "Reserva expirada";
            case RecursoNaoEncontradoException ignored -> "Não encontrado";
            case TransicaoInvalidaException ignored -> "Ação não permitida";
            case HorarioForaDaAgendaException ignored -> "Horário fora da agenda";
            default -> "Regra da agenda violada";
        };
    }
}
