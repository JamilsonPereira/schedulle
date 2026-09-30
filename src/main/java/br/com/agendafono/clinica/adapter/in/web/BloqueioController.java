package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.Views.BloqueioView;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.BloqueioRequest;
import br.com.agendafono.clinica.application.BloqueiosService;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import br.com.agendafono.compartilhado.web.ClinicaId;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bloqueios de agenda (férias, feriados, congressos). Todos os perfis podem criar; o perfil FONO só na
 * própria agenda (regra no {@link BloqueiosService}).
 */
@RestController
@RequestMapping("/api/v1/bloqueios")
class BloqueioController {

    private final BloqueiosService bloqueios;

    BloqueioController(BloqueiosService bloqueios) {
        this.bloqueios = bloqueios;
    }

    /** Bloqueios que tocam {@code [de, ate)}; com {@code profissionalId}, inclui os da clínica inteira. */
    @GetMapping
    List<BloqueioView> listar(ClinicaId clinica,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
                              @RequestParam(required = false) UUID profissionalId) {
        return bloqueios.listar(clinica.valor(), profissionalId, de, ate);
    }

    @PostMapping
    ResponseEntity<BloqueioView> criar(UsuarioAutenticado ator, @Valid @RequestBody BloqueioRequest r) {
        BloqueioView criado = bloqueios.criar(ator, r.profissionalId(), r.inicio(), r.fim(), r.motivo());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> remover(UsuarioAutenticado ator, @PathVariable UUID id) {
        bloqueios.remover(ator, id);
        return ResponseEntity.noContent().build();
    }
}
