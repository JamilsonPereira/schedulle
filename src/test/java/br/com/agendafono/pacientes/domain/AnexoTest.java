package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.AnexoInvalidoException;
import br.com.agendafono.pacientes.Anexos;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.TipoAnexo;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnexoTest {

    private static final byte[] PDF = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    private static Anexo anexo(String nome, byte[] conteudo) {
        return Anexo.novo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), TipoAnexo.PEDIDO_MEDICO, nome,
                Canal.PAINEL, conteudo, Instant.now());
    }

    @Test
    void detectaTipoPeloConteudoENaoPelaExtensao() {
        Anexo a = anexo("foto.png", PDF);
        assertThat(a.contentType()).isEqualTo("application/pdf");
        assertThat(a.nomeArquivo()).isEqualTo("foto.png.pdf");
        assertThat(anexo("pedido.jpeg", JPEG).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void recusaFormatosNaoPermitidos() {
        assertThatThrownBy(() -> anexo("virus.pdf", "MZ\u0090\u0000".getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOf(AnexoInvalidoException.class);
        assertThatThrownBy(() -> anexo("vazio.pdf", new byte[0])).isInstanceOf(AnexoInvalidoException.class);
    }

    @Test
    void recusaArquivoMaiorQueDezMb() {
        byte[] grande = new byte[(int) Anexos.TAMANHO_MAXIMO_BYTES + 1];
        System.arraycopy(PDF, 0, grande, 0, PDF.length);
        assertThatThrownBy(() -> anexo("grande.pdf", grande)).isInstanceOf(AnexoInvalidoException.class);
    }

    @Test
    void limpaNomeDoArquivoEChaveNaoTemDadoPessoal() {
        Anexo a = anexo("C:\\Users\\maria\\..\\pedido \"médico\" <Pedro>.pdf", PDF);
        assertThat(a.nomeArquivo()).isEqualTo("pedido médico Pedro.pdf");
        assertThat(a.chave()).matches("[0-9a-f-]{36}/[0-9a-f-]{36}/[0-9a-f-]{36}\\.pdf");
        assertThat(anexo(null, PDF).nomeArquivo()).isEqualTo("anexo.pdf");
        assertThat(a.sha256()).hasSize(64);
    }
}
