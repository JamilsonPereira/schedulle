package br.com.agendafono.compartilhado.seguranca;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quem fez a requisição, extraído do JWT. Parâmetro de controller resolvido automaticamente.
 *
 * @param profissionalId profissional vinculado ao usuário (perfil FONO); nulo se não houver
 */
public record UsuarioAutenticado(UUID usuarioId, UUID clinicaId, UUID profissionalId, Set<Papel> papeis) {

    public UsuarioAutenticado {
        Objects.requireNonNull(usuarioId, "usuarioId");
        Objects.requireNonNull(clinicaId, "clinicaId");
        papeis = papeis.isEmpty() ? EnumSet.noneOf(Papel.class) : EnumSet.copyOf(papeis);
    }

    public boolean tem(Papel papel) {
        return papeis.contains(papel);
    }

    /** Só FONO: enxerga e altera apenas o que é do próprio profissional. */
    public boolean somenteFono() {
        return tem(Papel.FONO) && !tem(Papel.ADMIN) && !tem(Papel.RECEPCAO);
    }
}
