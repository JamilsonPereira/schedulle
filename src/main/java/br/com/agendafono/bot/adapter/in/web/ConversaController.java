package br.com.agendafono.bot.adapter.in.web;

import br.com.agendafono.bot.AtendimentoHumano;
import br.com.agendafono.bot.ConversaEmAtendimento;
import br.com.agendafono.compartilhado.web.ClinicaId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Fila de atendimento humano da recepção (conversas que o bot transbordou). */
@RestController
@RequestMapping("/api/v1/conversas")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
class ConversaController {

    private final AtendimentoHumano atendimento;

    ConversaController(AtendimentoHumano atendimento) {
        this.atendimento = atendimento;
    }

    @GetMapping("/em-atendimento")
    List<ConversaEmAtendimento> emAtendimento(ClinicaId clinica) {
        return atendimento.emAtendimento(clinica.valor());
    }

    /** Encerra o atendimento humano: a próxima mensagem do contato volta para o bot, no menu. */
    @PostMapping("/{id}/devolver-ao-bot")
    ResponseEntity<Void> devolverAoBot(ClinicaId clinica, @PathVariable UUID id) {
        if (!atendimento.devolverAoBot(clinica.valor(), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada");
        }
        return ResponseEntity.noContent().build();
    }
}
