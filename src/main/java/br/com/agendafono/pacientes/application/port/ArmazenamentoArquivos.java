package br.com.agendafono.pacientes.application.port;

/**
 * Onde ficam os bytes dos anexos. Em desenvolvimento, disco local; em produção, S3 com SSE-KMS (Passo 8).
 * A chave nunca contém dados pessoais.
 */
public interface ArmazenamentoArquivos {

    void salvar(String chave, byte[] conteudo, String contentType);

    byte[] ler(String chave);

    /** Idempotente: remover uma chave inexistente não é erro. */
    void remover(String chave);
}
