package br.com.agendafono.pacientes.adapter.in.web;

import br.com.agendafono.compartilhado.web.ClinicaId;
import br.com.agendafono.pacientes.Anexos;
import br.com.agendafono.pacientes.Views.ConteudoAnexo;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/anexos")
class AnexoController {

    private final Anexos anexos;

    AnexoController(Anexos anexos) {
        this.anexos = anexos;
    }

    /** Sempre como download, nunca exibido inline; sem cache (dado de saúde). */
    @GetMapping("/{id}/conteudo")
    ResponseEntity<byte[]> conteudo(ClinicaId clinica, @PathVariable UUID id) {
        ConteudoAnexo c = anexos.abrir(clinica.valor(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(c.anexo().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(c.anexo().nomeArquivo(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(c.bytes());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remover(ClinicaId clinica, @PathVariable UUID id) {
        anexos.remover(clinica.valor(), id);
        return ResponseEntity.noContent().build();
    }
}
