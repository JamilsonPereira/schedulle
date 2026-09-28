package br.com.agendafono.pacientes.adapter.in.web;

import br.com.agendafono.pacientes.AnexoInvalidoException;
import br.com.agendafono.pacientes.ConsentimentoAusenteException;
import br.com.agendafono.pacientes.DadosInvalidosException;
import br.com.agendafono.pacientes.NaoEncontradoException;
import br.com.agendafono.pacientes.PacientesException;
import br.com.agendafono.pacientes.RegistroDesatualizadoException;
import br.com.agendafono.pacientes.TelefoneInvalidoException;
import br.com.agendafono.pacientes.TelefoneJaCadastradoException;
import br.com.agendafono.pacientes.TitularAnonimizadoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Problem Details (RFC 9457) para os erros do módulo pacientes. */
@RestControllerAdvice(assignableTypes = {PacienteController.class, ResponsavelController.class,
        AnexoController.class})
class PacientesExceptionHandler {

    @ExceptionHandler(PacientesException.class)
    ProblemDetail pacientes(PacientesException e) {
        HttpStatus status = switch (e) {
            case NaoEncontradoException ignored -> HttpStatus.NOT_FOUND;
            case RegistroDesatualizadoException ignored -> HttpStatus.CONFLICT;
            case TelefoneJaCadastradoException ignored -> HttpStatus.CONFLICT;
            case TelefoneInvalidoException ignored -> HttpStatus.BAD_REQUEST;
            case DadosInvalidosException ignored -> HttpStatus.BAD_REQUEST;
            case AnexoInvalidoException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case ConsentimentoAusenteException ignored -> HttpStatus.UNPROCESSABLE_ENTITY;
            case TitularAnonimizadoException ignored -> HttpStatus.GONE;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problema.setType(URI.create("/erros/" + e.codigo()));
        problema.setTitle(status.getReasonPhrase());
        return problema;
    }
}
