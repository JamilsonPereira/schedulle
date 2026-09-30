package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.ClinicaException;
import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.CredenciaisInvalidasException;
import br.com.agendafono.clinica.EmailJaCadastradoException;
import br.com.agendafono.clinica.GradeSobrepostaException;
import br.com.agendafono.clinica.NomeDuplicadoException;
import br.com.agendafono.clinica.OperacaoNaoPermitidaException;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.SenhaFracaException;
import br.com.agendafono.clinica.SessaoExpiradaException;
import br.com.agendafono.clinica.ValidacaoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/** Problem Details (RFC 9457) para os erros do módulo clínica. */
@RestControllerAdvice(assignableTypes = {AuthController.class, UsuarioController.class, ClinicaController.class,
        ProfissionalController.class, BloqueioController.class, PlataformaController.class})
class ClinicaExceptionHandler {

    @ExceptionHandler(ClinicaException.class)
    ProblemDetail clinica(ClinicaException e) {
        HttpStatus status = switch (e) {
            case RegistroNaoEncontradoException ignored -> HttpStatus.NOT_FOUND;
            case ValidacaoException ignored -> HttpStatus.BAD_REQUEST;
            case SenhaFracaException ignored -> HttpStatus.BAD_REQUEST;
            case ConflitoDeVersaoException ignored -> HttpStatus.CONFLICT;
            case EmailJaCadastradoException ignored -> HttpStatus.CONFLICT;
            case NomeDuplicadoException ignored -> HttpStatus.CONFLICT;
            case GradeSobrepostaException ignored -> HttpStatus.CONFLICT;
            case CredenciaisInvalidasException ignored -> HttpStatus.UNAUTHORIZED;
            case SessaoExpiradaException ignored -> HttpStatus.UNAUTHORIZED;
            case OperacaoNaoPermitidaException ignored -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, e.getMessage());
        problema.setType(URI.create("/erros/" + e.codigo()));
        problema.setTitle(status.getReasonPhrase());
        return problema;
    }
}
