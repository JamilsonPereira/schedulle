package br.com.agendafono.pacientes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Telefone em formato E.164 ({@code +5511999990000}).
 *
 * <p>Use {@link #doWhatsApp(String)} para o {@code wa_id} que chega da Meta (só dígitos, com DDI) e
 * {@link #digitado(String)} para o que a recepção digita no painel ({@code (11) 99999-0000}).
 *
 * <p>Celulares brasileiros podem chegar do WhatsApp sem o nono dígito; {@link #variantes()} devolve as
 * duas formas para que a busca encontre o responsável em qualquer uma delas.
 */
public record Telefone(String e164) {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{7,14}$");
    private static final String DDI_BRASIL = "55";

    public Telefone {
        Objects.requireNonNull(e164, "e164");
        if (!E164.matcher(e164).matches()) {
            throw new TelefoneInvalidoException(e164);
        }
    }

    /** {@code wa_id} do WhatsApp: só dígitos, já com o código do país. */
    public static Telefone doWhatsApp(String waId) {
        if (waId == null) {
            throw new TelefoneInvalidoException(null);
        }
        return new Telefone("+" + waId.replaceAll("\\D", ""));
    }

    /**
     * Número digitado por uma pessoa. Com "+", vale como internacional; sem "+", 10 ou 11 dígitos são
     * tratados como número brasileiro com DDD; 12 ou 13 dígitos começando com 55, como brasileiro com DDI.
     */
    public static Telefone digitado(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new TelefoneInvalidoException(texto);
        }
        String limpo = texto.trim();
        String digitos = limpo.replaceAll("\\D", "");
        if (limpo.startsWith("+")) {
            return new Telefone("+" + digitos);
        }
        if (digitos.length() == 10 || digitos.length() == 11) {
            return new Telefone("+" + DDI_BRASIL + digitos);
        }
        if (digitos.startsWith(DDI_BRASIL) && (digitos.length() == 12 || digitos.length() == 13)) {
            return new Telefone("+" + digitos);
        }
        throw new TelefoneInvalidoException(texto);
    }

    /** Formato usado pela API do WhatsApp (sem o "+"). */
    public String somenteDigitos() {
        return e164.substring(1);
    }

    public boolean brasileiro() {
        return e164.startsWith("+" + DDI_BRASIL);
    }

    /** Este número e, para celular brasileiro, a forma com/sem o nono dígito. */
    public List<Telefone> variantes() {
        List<Telefone> todas = new ArrayList<>(2);
        todas.add(this);
        if (!brasileiro()) {
            return todas;
        }
        String nacional = e164.substring(3); // DDD + número
        if (nacional.length() == 11 && nacional.charAt(2) == '9') {
            todas.add(new Telefone("+" + DDI_BRASIL + nacional.substring(0, 2) + nacional.substring(3)));
        } else if (nacional.length() == 10 && "6789".indexOf(nacional.charAt(2)) >= 0) {
            todas.add(new Telefone("+" + DDI_BRASIL + nacional.substring(0, 2) + "9" + nacional.substring(2)));
        }
        return todas;
    }

    /** Para listas e logs: {@code (11) 9****-0000}. Nunca exibe o número inteiro. */
    public String mascarado() {
        String fim = e164.substring(e164.length() - 4);
        if (brasileiro()) {
            String nacional = e164.substring(3);
            String ddd = nacional.substring(0, 2);
            return nacional.length() == 11
                    ? "(" + ddd + ") " + nacional.charAt(2) + "****-" + fim
                    : "(" + ddd + ") ****-" + fim;
        }
        return "+" + "*".repeat(Math.max(0, e164.length() - 5)) + fim;
    }

    @Override
    public String toString() {
        return mascarado();
    }
}
