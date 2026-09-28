package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Anexos;
import br.com.agendafono.pacientes.AnexoInvalidoException;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.TipoAnexo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Metadados de um arquivo anexado ao paciente. O conteúdo fica no armazenamento, pela {@code chave}. */
public record Anexo(UUID id, UUID clinicaId, UUID pacienteId, TipoAnexo tipo, String nomeArquivo,
                    String contentType, long tamanhoBytes, String sha256, String chave, Canal origem,
                    Instant criadoEm) {

    public Anexo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(clinicaId);
        Objects.requireNonNull(pacienteId);
        Objects.requireNonNull(tipo);
        Objects.requireNonNull(nomeArquivo);
        Objects.requireNonNull(contentType);
        Objects.requireNonNull(sha256);
        Objects.requireNonNull(chave);
        Objects.requireNonNull(origem);
        Objects.requireNonNull(criadoEm);
    }

    /**
     * Valida o arquivo e monta os metadados. A chave de armazenamento não carrega nome nem dado pessoal:
     * {@code <clinica>/<paciente>/<anexo><extensão>}.
     */
    public static Anexo novo(UUID id, UUID clinicaId, UUID pacienteId, TipoAnexo tipo, String nomeInformado,
                             Canal origem, byte[] conteudo, Instant agora) {
        if (conteudo.length == 0) {
            throw new AnexoInvalidoException("O arquivo está vazio");
        }
        if (conteudo.length > Anexos.TAMANHO_MAXIMO_BYTES) {
            throw new AnexoInvalidoException("O arquivo passa de 10 MB");
        }
        FormatoArquivo formato = FormatoArquivo.detectar(conteudo)
                .orElseThrow(() -> new AnexoInvalidoException("Envie uma imagem JPEG/PNG ou um PDF"));
        String chave = clinicaId + "/" + pacienteId + "/" + id + formato.extensao();
        return new Anexo(id, clinicaId, pacienteId, tipo, nomeSeguro(nomeInformado, formato), formato.contentType(),
                conteudo.length, sha256(conteudo), chave, origem, agora);
    }

    /** Remove caminhos e caracteres de controle; limita o tamanho; garante uma extensão coerente. */
    static String nomeSeguro(String informado, FormatoArquivo formato) {
        String base = informado == null ? "" : informado;
        base = base.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[\\p{Cntrl}\"<>:|?*]", "").trim();
        if (base.isEmpty() || base.startsWith(".")) {
            base = "anexo" + formato.extensao();
        }
        String minusculo = base.toLowerCase();
        boolean extensaoConfere = minusculo.endsWith(formato.extensao())
                || (formato == FormatoArquivo.JPEG && minusculo.endsWith(".jpeg"));
        if (!extensaoConfere) {
            base = base + formato.extensao();
        }
        if (base.length() > 100) {
            base = base.substring(0, 100 - formato.extensao().length()) + formato.extensao();
        }
        return base;
    }

    private static String sha256(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
