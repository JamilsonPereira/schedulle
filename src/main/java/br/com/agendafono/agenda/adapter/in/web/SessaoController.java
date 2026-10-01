package br.com.agendafono.agenda.adapter.in.web;

import br.com.agendafono.agenda.Agendamento;
import br.com.agendafono.agenda.Agendamento.Agendar;
import br.com.agendafono.agenda.Agendamento.Remarcar;
import br.com.agendafono.agenda.Presenca;
import br.com.agendafono.agenda.RecursoNaoEncontradoException;
import br.com.agendafono.agenda.SessaoView;
import br.com.agendafono.agenda.adapter.in.web.AgendaDtos.AlterarStatusRequest;

import br.com.agendafono.agenda.adapter.in.web.AgendaDtos.RemarcarRequest;
import br.com.agendafono.agenda.adapter.in.web.AgendaDtos.AcaoStatus;
import br.com.agendafono.agenda.adapter.in.web.request.NovaSessaoRequest;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import br.com.agendafono.compartilhado.web.ClinicaId;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sessões. ADMIN e RECEPCAO fazem tudo; o perfil FONO só enxerga a própria agenda e só registra
 * atendimento ou falta nas próprias sessões.
 */
@RestController
@RequestMapping("/api/v1/sessoes")
class SessaoController {

    private static final Set<AcaoStatus> ACOES_DO_FONO =
            Set.of(AcaoStatus.REGISTRAR_ATENDIMENTO, AcaoStatus.REGISTRAR_FALTA_SEM_AVISO);

    private final Agendamento agendamento;
    private final Presenca presenca;

    SessaoController(Agendamento agendamento, Presenca presenca) {
        this.agendamento = agendamento;
        this.presenca = presenca;
    }

    /** Sessões que começam em {@code [de, ate)}; sem {@code profissionalId} traz todos os fonos. */
    @GetMapping
    List<SessaoView> listar(
            UsuarioAutenticado usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(required = false) UUID profissionalId) {
        if (usuario.somenteFono()) {
            if (usuario.profissionalId() == null) {
                return List.of();
            }
            profissionalId = usuario.profissionalId();
        }
        return agendamento.listar(usuario.clinicaId(), profissionalId, de, ate);
    }

    @GetMapping("/{id}")
    SessaoView buscar(UsuarioAutenticado usuario, @PathVariable UUID id) {
        return visivel(usuario, id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    ResponseEntity<SessaoView> agendar(ClinicaId clinica, @Valid @RequestBody NovaSessaoRequest r) {
        SessaoView criada = agendamento.agendar(new Agendar(clinica.valor(), r.pacienteId(), r.profissionalId(),
                r.tipo(), r.inicio(), r.duracaoMin(), r.recursoId(), r.permitirForaDaGrade()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @PostMapping("/{id}/remarcar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    SessaoView remarcar(ClinicaId clinica, @PathVariable UUID id, @Valid @RequestBody RemarcarRequest r) {
        return agendamento.remarcar(new Remarcar(clinica.valor(), id, r.novoInicio(), r.recursoId(),
                r.permitirForaDaGrade(), r.versao()));
    }

    @PatchMapping("/{id}/status")
    SessaoView alterarStatus(UsuarioAutenticado usuario, @PathVariable UUID id,
                             @Valid @RequestBody AlterarStatusRequest r) {
        if (usuario.somenteFono()) {
            if (!ACOES_DO_FONO.contains(r.acao())) {
                throw new AccessDeniedException("O perfil FONO só registra atendimento ou falta");
            }
            visivel(usuario, id);
        }
        UUID c = usuario.clinicaId();
        return switch (r.acao()) {
            case CONFIRMAR_PRESENCA -> agendamento.confirmarPresenca(c, id, r.versao());
            case CANCELAR -> agendamento.cancelar(c, id, r.versao());
            case AVISAR_FALTA -> agendamento.avisarFalta(c, id, r.versao());
            case REGISTRAR_ATENDIMENTO -> presenca.registrar(c, id, Presenca.Resultado.ATENDIDA, r.versao());
            case REGISTRAR_FALTA_SEM_AVISO ->
                    presenca.registrar(c, id, Presenca.Resultado.FALTA_SEM_AVISO, r.versao());
        };
    }

    /** Para o perfil FONO, sessão de outro profissional é tratada como inexistente (não revela a agenda alheia). */
    private SessaoView visivel(UsuarioAutenticado usuario, UUID id) {
        return agendamento.buscar(usuario.clinicaId(), id)
                .filter(s -> !usuario.somenteFono() || Objects.equals(s.profissionalId(), usuario.profissionalId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão", id));
    }
}
