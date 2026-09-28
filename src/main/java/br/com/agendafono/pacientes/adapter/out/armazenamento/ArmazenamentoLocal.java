package br.com.agendafono.pacientes.adapter.out.armazenamento;

import br.com.agendafono.pacientes.application.PacientesProperties;
import br.com.agendafono.pacientes.application.port.ArmazenamentoArquivos;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Armazenamento em disco local, para desenvolvimento e testes. Em produção entra um adaptador S3
 * com criptografia SSE-KMS (SDD, seção 11), implementando a mesma porta.
 */
@Component
class ArmazenamentoLocal implements ArmazenamentoArquivos {

    private final Path raiz;

    ArmazenamentoLocal(PacientesProperties propriedades) {
        this.raiz = Path.of(propriedades.diretorioAnexos()).toAbsolutePath().normalize();
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String contentType) {
        Path destino = resolver(chave);
        try {
            Files.createDirectories(destino.getParent());
            Path temporario = Files.createTempFile(destino.getParent(), ".upload-", ".tmp");
            Files.write(temporario, conteudo);
            Files.move(temporario, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar anexo", e);
        }
    }

    @Override
    public byte[] ler(String chave) {
        try {
            return Files.readAllBytes(resolver(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Arquivo do anexo indisponível", e);
        }
    }

    @Override
    public void remover(String chave) {
        try {
            Files.deleteIfExists(resolver(chave));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao remover anexo", e);
        }
    }

    /** Impede que uma chave malformada escape da pasta raiz (path traversal). */
    private Path resolver(String chave) {
        Path caminho = raiz.resolve(chave).normalize();
        if (!caminho.startsWith(raiz)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return caminho;
    }
}
