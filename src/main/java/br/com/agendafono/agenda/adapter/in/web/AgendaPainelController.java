package br.com.agendafono.agenda.adapter.in.web;

import br.com.agendafono.agenda.Agendamento;
import br.com.agendafono.agenda.SessaoView;
import br.com.agendafono.agenda.StatusSessao;
import br.com.agendafono.agenda.TipoSessao;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import br.com.agendafono.pacientes.PacienteConsulta;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Leitura da agenda para o painel web: as sessões já com o nome do paciente, para a tela não precisar de uma
 * chamada por paciente. Mesmas regras de perfil de {@code GET /api/v1/sessoes}: o perfil FONO só enxerga a
 * própria agenda.
 */
@RestController
@RequestMapping("/api/v1/agenda/sessoes")
class AgendaPainelController {

    private static final long MAXIMO_DIAS = 400;

    private final Agendamento agendamento;
    private final PacienteConsulta pacientes;

    AgendaPainelController(Agendamento agendamento, PacienteConsulta pacientes) {
        this.agendamento = agendamento;
        this.pacientes = pacientes;
    }

    /** Campos de {@link SessaoView} mais o nome do paciente. Horários em UTC. */
    record SessaoDoPainel(UUID id, UUID pacienteId, String pacienteNome, UUID profissionalId, UUID recursoId,
                          UUID serieId, TipoSessao tipo, Instant inicio, Instant fim, StatusSessao status,
                          Instant expiraEm, int versao) {

        static SessaoDoPainel de(SessaoView s, String pacienteNome) {
            return new SessaoDoPainel(s.id(), s.pacienteId(), pacienteNome, s.profissionalId(), s.recursoId(),
                    s.serieId(), s.tipo(), s.inicio(), s.fim(), s.status(), s.expiraEm(), s.versao());
        }
    }

    /**
     * Sessões que começam em {@code [de, ate)} (no máximo 400 dias). Sem {@code profissionalId}, todos os fonos.
     * Com {@code pacienteId}, o histórico daquele paciente, mais recentes primeiro.
     */
    @GetMapping
    List<SessaoDoPainel> listar(
            UsuarioAutenticado usuario,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            @RequestParam(required = false) UUID profissionalId,
            @RequestParam(required = false) UUID pacienteId) {
        if (!ate.isAfter(de) || Duration.between(de, ate).toDays() > MAXIMO_DIAS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intervalo inválido (máximo de 400 dias)");
        }
        if (usuario.somenteFono()) {
            if (usuario.profissionalId() == null) {
                return List.of();
            }
            profissionalId = usuario.profissionalId();
        }

        List<SessaoView> sessoes;
        if (pacienteId != null) {
            UUID filtro = profissionalId;
            sessoes = agendamento.doPaciente(usuario.clinicaId(), pacienteId, de, ate).stream()
                    .filter(s -> filtro == null || filtro.equals(s.profissionalId()))
                    .toList();
        } else {
            sessoes = agendamento.listar(usuario.clinicaId(), profissionalId, de, ate);
        }

        Map<UUID, String> nomes = pacientes.nomes(usuario.clinicaId(),
                sessoes.stream().map(SessaoView::pacienteId).distinct().toList());
        return sessoes.stream().map(s -> SessaoDoPainel.de(s, nomes.get(s.pacienteId()))).toList();
    }
}
