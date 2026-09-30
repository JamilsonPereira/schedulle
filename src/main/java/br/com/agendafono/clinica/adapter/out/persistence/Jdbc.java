package br.com.agendafono.clinica.adapter.out.persistence;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Utilidades de mapeamento compartilhadas pelos repositórios do módulo. */
final class Jdbc {

    private Jdbc() {
    }

    static Instant instant(ResultSet rs, String coluna) throws SQLException {
        OffsetDateTime v = rs.getObject(coluna, OffsetDateTime.class);
        return v != null ? v.toInstant() : null;
    }

    static OffsetDateTime utc(Instant i) {
        return i != null ? i.atOffset(ZoneOffset.UTC) : null;
    }

    /** Para gravar em coluna text[] via {@code string_to_array(:param, ',')}; nomes de enum não têm vírgula. */
    static String csv(Collection<? extends Enum<?>> valores) {
        return valores.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    static List<String> textos(ResultSet rs, String coluna) throws SQLException {
        Array array = rs.getArray(coluna);
        if (array == null) {
            return List.of();
        }
        return List.of((String[]) array.getArray());
    }
}
