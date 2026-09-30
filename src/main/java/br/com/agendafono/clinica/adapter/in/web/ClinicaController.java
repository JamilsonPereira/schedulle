package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.RecursoView;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.AtualizarClinicaRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.AtualizarRecursoRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.PoliticaRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.RecursoRequest;
import br.com.agendafono.clinica.application.ClinicaService;
import br.com.agendafono.clinica.domain.PoliticaClinica;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** Dados da clínica do usuário logado, política de agendamento e salas. Leitura para todos; escrita só ADMIN. */
@RestController
@RequestMapping("/api/v1")
class ClinicaController {

    private final ClinicaService clinicas;

    ClinicaController(ClinicaService clinicas) {
        this.clinicas = clinicas;
    }

    @GetMapping("/clinica")
    ClinicaView clinica(ClinicaId clinica) {
        return clinicas.clinica(clinica.valor())
                .orElseThrow(() -> new RegistroNaoEncontradoException("Clínica", clinica.valor()));
    }

    @PutMapping("/clinica")
    @PreAuthorize("hasRole('ADMIN')")
    ClinicaView atualizar(UsuarioAutenticado ator, @Valid @RequestBody AtualizarClinicaRequest r) {
        return clinicas.atualizar(ator, r.nome(), r.fuso(), r.versao());
    }

    @PutMapping("/clinica/politica")
    @PreAuthorize("hasRole('ADMIN')")
    ClinicaView definirPolitica(UsuarioAutenticado ator, @RequestBody PoliticaRequest r) {
        PoliticaClinica politica = new PoliticaClinica(r.antecedenciaMinimaMin(), r.janelaMaximaDias(),
                r.passoMin(), r.ttlReservaMin(), r.antecedenciaAvisoFaltaHoras());
        return clinicas.definirPolitica(ator, politica, r.versao());
    }

    // ------------------------------------------------------------------ salas

    @GetMapping("/recursos")
    List<RecursoView> recursos(ClinicaId clinica) {
        return clinicas.recursos(clinica.valor());
    }

    @PostMapping("/recursos")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<RecursoView> criarRecurso(UsuarioAutenticado ator, @Valid @RequestBody RecursoRequest r) {
        RecursoView criado = clinicas.criarRecurso(ator, r.nome(), r.tipo());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/recursos/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    RecursoView atualizarRecurso(UsuarioAutenticado ator, @PathVariable UUID id,
                                 @Valid @RequestBody AtualizarRecursoRequest r) {
        return clinicas.atualizarRecurso(ator, id, r.nome(), r.tipo(), r.ativo(), r.versao());
    }
}
