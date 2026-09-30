package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void inserir(RefreshToken token);

    Optional<RefreshToken> porHash(String hash);

    /** Marca como usado só se ainda não foi (proteção contra corrida). @return se marcou */
    boolean marcarUsado(UUID id, Instant agora);

    void revogarFamilia(UUID familia, Instant agora);

    void revogarDoUsuario(UUID usuarioId, Instant agora);
}
