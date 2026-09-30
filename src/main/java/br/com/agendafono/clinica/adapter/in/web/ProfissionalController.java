package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.Views.ProfissionalView;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.GradeRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.ProfissionalRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.VersaoRequest;
import br.com.agendafono.clinica.application.ProfissionaisService;
import br.com.agendafono.clinica.application.ProfissionaisService.DadosProfissional;
import br.com.agendafono.clinica.domain.IntervaloGrade;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import br.com.agendafono.compartilhado.web.ClinicaId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** Profissionais e grade semanal. Leitura para todos os perfis; escrita só ADMIN. */
@RestController
@RequestMapping("/api/v1/profissionais")
class ProfissionalController {

    private final ProfissionaisService profissionais;

    ProfissionalController(ProfissionaisService profissionais) {
        this.profissionais = profissionais;
    }

    @GetMapping
    List<ProfissionalView> listar(ClinicaId clinica, @RequestParam(defaultValue = "true") boolean apenasAtivos) {
        return profissionais.listar(clinica.valor(), apenasAtivos);
    }

    @GetMapping("/{id}")
    ProfissionalView buscar(ClinicaId clinica, @PathVariable UUID id) {
        return profissionais.profissional(clinica.valor(), id)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Profissional", id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<ProfissionalView> criar(UsuarioAutenticado ator, @Valid @RequestBody ProfissionalRequest r) {
        ProfissionalView criado = profissionais.criar(ator, dados(r));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    ProfissionalView atualizar(UsuarioAutenticado ator, @PathVariable UUID id,
                               @Valid @RequestBody ProfissionalRequest r) {
        return profissionais.atualizar(ator, id, dados(r), r.versao());
    }

    /** Substitui a grade inteira do profissional. */
    @PutMapping("/{id}/grade")
    @PreAuthorize("hasRole('ADMIN')")
    ProfissionalView definirGrade(UsuarioAutenticado ator, @PathVariable UUID id, @Valid @RequestBody GradeRequest r) {
        List<IntervaloGrade> grade = r.intervalos().stream()
                .map(i -> new IntervaloGrade(i.dia(), i.inicio(), i.fim(), i.recursoId()))
                .toList();
        return profissionais.definirGrade(ator, id, grade, r.versao());
    }

    @PostMapping("/{id}/inativar")
    @PreAuthorize("hasRole('ADMIN')")
    ProfissionalView inativar(UsuarioAutenticado ator, @PathVariable UUID id,
                              @RequestBody(required = false) VersaoRequest r) {
        return profissionais.inativar(ator, id, r == null ? null : r.versao());
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    ProfissionalView reativar(UsuarioAutenticado ator, @PathVariable UUID id,
                              @RequestBody(required = false) VersaoRequest r) {
        return profissionais.reativar(ator, id, r == null ? null : r.versao());
    }

    private static DadosProfissional dados(ProfissionalRequest r) {
        return new DadosProfissional(r.nome(), r.registroCrfa(), r.subareas(), r.duracaoPadraoMin(), r.usuarioId());
    }
}
