package br.com.agendafono.pacientes.domain;

import java.util.Optional;

/**
 * Identifica o tipo real do arquivo pelos primeiros bytes (assinatura), sem confiar na extensão
 * nem no Content-Type informado por quem enviou.
 */
public enum FormatoArquivo {

    JPEG("image/jpeg", ".jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", ".png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    PDF("application/pdf", ".pdf", new int[]{0x25, 0x50, 0x44, 0x46, 0x2D}); // %PDF-

    private final String contentType;
    private final String extensao;
    private final int[] assinatura;

    FormatoArquivo(String contentType, String extensao, int[] assinatura) {
        this.contentType = contentType;
        this.extensao = extensao;
        this.assinatura = assinatura;
    }

    public static Optional<FormatoArquivo> detectar(byte[] conteudo) {
        for (FormatoArquivo f : values()) {
            if (f.confere(conteudo)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

    private boolean confere(byte[] conteudo) {
        if (conteudo == null || conteudo.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((conteudo[i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }

    public String contentType() {
        return contentType;
    }

    public String extensao() {
        return extensao;
    }
}
