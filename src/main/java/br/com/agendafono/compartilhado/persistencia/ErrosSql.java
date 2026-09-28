package br.com.agendafono.compartilhado.persistencia;

import java.sql.SQLException;

/** Ajuda a reconhecer violações de constraint do Postgres sem depender das classes do driver. */
public final class ErrosSql {

    public static final String VIOLACAO_UNICIDADE = "23505";
    public static final String VIOLACAO_EXCLUSAO = "23P01";
    public static final String VIOLACAO_CHAVE_ESTRANGEIRA = "23503";

    private ErrosSql() {
    }

    public static String sqlState(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    /** Verdadeiro se o erro é {@code sqlState} na constraint {@code constraint} (nome aparece na mensagem). */
    public static boolean violou(Throwable erro, String sqlState, String constraint) {
        if (!sqlState.equals(sqlState(erro))) {
            return false;
        }
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
