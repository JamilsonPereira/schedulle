package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.SenhaFracaException;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;

/** Política mínima de senha (SDD, seção 11) e geração de senha temporária. */
public final class Senhas {

    public static final int TAMANHO_MINIMO = 10;

    /** Amostra das senhas mais usadas no Brasil; a checagem contra vazamentos completos vem depois. */
    private static final Set<String> COMUNS = Set.of("1234567890", "0123456789", "12345678910", "senha12345",
            "senha123456", "qwertyuiop", "abcdefghij", "1q2w3e4r5t", "mudar12345", "brasil2026", "fono123456");

    private static final char[] ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Senhas() {
    }

    public static void validar(String senha, String email) {
        if (senha == null || senha.length() < TAMANHO_MINIMO) {
            throw new SenhaFracaException("A senha deve ter pelo menos " + TAMANHO_MINIMO + " caracteres");
        }
        if (senha.length() > 128) {
            throw new SenhaFracaException("A senha pode ter no máximo 128 caracteres");
        }
        String minuscula = senha.toLowerCase(Locale.ROOT);
        if (COMUNS.contains(minuscula) || senha.chars().distinct().count() < 4) {
            throw new SenhaFracaException("Senha muito comum; escolha outra");
        }
        String usuarioDoEmail = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@")[0];
        if (usuarioDoEmail.length() >= 4 && minuscula.contains(usuarioDoEmail)) {
            throw new SenhaFracaException("A senha não pode conter o e-mail");
        }
    }

    /** 14 caracteres sem símbolos ambíguos (0/O, 1/l/I), para ditar ou copiar com segurança. */
    public static String temporaria() {
        StringBuilder s = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            s.append(ALFABETO[ALEATORIO.nextInt(ALFABETO.length)]);
        }
        return s.toString();
    }
}
