package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ValidacaoException;

final class Textos {

    private Textos() {
    }

    static String obrigatorio(String valor, String campo, int min, int max) {
        String v = opcional(valor, campo, min, max);
        if (v == null) {
            throw new ValidacaoException("Informe " + campo);
        }
        return v;
    }

    static String opcional(String valor, String campo, int min, int max) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String v = valor.trim().replaceAll("\\s+", " ");
        if (v.length() < min || v.length() > max) {
            throw new ValidacaoException(campo + " deve ter entre " + min + " e " + max + " caracteres");
        }
        return v;
    }
}
