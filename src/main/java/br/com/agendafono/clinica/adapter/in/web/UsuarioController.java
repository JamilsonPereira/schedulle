package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.Views.UsuarioView;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.AtualizarUsuarioRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.NovoUsuarioRequest;
import br.com.agendafono.clinica.application.UsuariosService;
import br.com.agendafono.clinica.application.UsuariosService.UsuarioComSenhaTemporaria;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import br.com.agendafono.compartilhado.web.ClinicaId;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
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

/** Usuários do painel. Só ADMIN. A senha temporária aparece uma única vez, na resposta. */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasRole('ADMIN')")
class UsuarioController {

    private final UsuariosService usuarios;

    UsuarioController(UsuariosService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    List<UsuarioView> listar(ClinicaId clinica) {
        return usuarios.listar(clinica.valor());
    }

    @PostMapping
    ResponseEntity<UsuarioComSenhaTemporaria> criar(UsuarioAutenticado ator,
                                                    @Valid @RequestBody NovoUsuarioRequest r) {
        UsuarioComSenhaTemporaria criado = usuarios.criar(ator, r.nome(), r.email(), r.papeis());
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.usuario().id()).toUri();
        return ResponseEntity.created(location).cacheControl(CacheControl.noStore()).body(criado);
    }

    @PutMapping("/{id}")
    UsuarioView atualizar(UsuarioAutenticado ator, @PathVariable UUID id,
                          @Valid @RequestBody AtualizarUsuarioRequest r) {
        return usuarios.atualizar(ator, id, r.nome(), r.papeis(), r.ativo(), r.versao());
    }

    @PostMapping("/{id}/senha-temporaria")
    ResponseEntity<UsuarioComSenhaTemporaria> redefinirSenha(UsuarioAutenticado ator, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(usuarios.redefinirSenha(ator, id));
    }
}
