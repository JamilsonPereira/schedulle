package br.com.agendafono.agenda.adapter.in.web;

import br.com.agendafono.agenda.Disponibilidade;
import br.com.agendafono.agenda.Disponibilidade.Consulta;
import br.com.agendafono.agenda.Disponibilidade.Origem;
import br.com.agendafono.agenda.adapter.in.web.AgendaDtos.HorarioLivreResponse;
import br.com.agendafono.compartilhado.web.ClinicaId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disponibilidade")
class DisponibilidadeController {

    private final Disponibilidade disponibilidade;

    DisponibilidadeController(Disponibilidade disponibilidade) {
        this.disponibilidade = disponibilidade;
    }

    /** Horários livres para o painel. {@code de} e {@code ate} são datas no fuso da clínica. */
    @GetMapping
    List<HorarioLivreResponse> consultar(
            ClinicaId clinica,
            @RequestParam UUID profissionalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) Integer duracaoMin) {

        return disponibilidade.consultar(
                        new Consulta(clinica.valor(), profissionalId, de, ate, duracaoMin, Origem.PAINEL))
                .stream()
                .map(HorarioLivreResponse::de)
                .toList();
    }
}
