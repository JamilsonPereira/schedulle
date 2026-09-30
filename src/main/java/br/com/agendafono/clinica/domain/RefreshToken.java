package br.com.agendafono.clinica.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token opaco (SDD, seção 10). Só o hash é guardado. Cada uso gera um novo token da mesma família;
 * reutilizar um token já usado indica roubo e revoga a família inteira.
 */
public record RefreshToken(UUID id, UUID clinicaId, UUID usuarioId, UUID familia, String hash, Instant expiraEm,
                           Instant usadoEm, Instant revogadoEm) {

    public boolean valido(Instant agora) {
        return usadoEm == null && revogadoEm == null && expiraEm.isAfter(agora);
    }

    public boolean reutilizado() {
        return usadoEm != null && revogadoEm == null;
    }
}
