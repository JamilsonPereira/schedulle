package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.Usuario;

import java.time.Instant;
import java.util.UUID;

/** Emite o JWT de acesso. */
public interface EmissorDeTokens {

    TokenDeAcesso emitir(Usuario usuario, UUID profissionalId, Instant agora);

    record TokenDeAcesso(String valor, Instant expiraEm) {
    }
}
