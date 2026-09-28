package br.com.agendafono.pacientes.adapter.in.web;

import br.com.agendafono.compartilhado.web.ClinicaId;
import br.com.agendafono.pacientes.CadastroPacientes;
import br.com.agendafono.pacientes.CadastroPacientes.AtualizarResponsavel;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.DireitosDoTitular;
import br.com.agendafono.pacientes.NaoEncontradoException;
import br.com.agendafono.pacientes.PacienteConsulta;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.Views.ExportacaoDados;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;
import br.com.agendafono.pacientes.adapter.in.web.PacientesDtos.AtualizarResponsavelRequest;
import br.com.agendafono.pacientes.adapter.in.web.PacientesDtos.ConsentimentoRequest;
import br.com.agendafono.pacientes.application.PacientesProperties;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/responsaveis")
class ResponsavelController {

    /** Evidência do registro no painel até existir usuário autenticado (Passo 2). */
    private static final String EVIDENCIA_PAINEL = "painel:recepcao";

    private final PacienteConsulta consulta;
    private final CadastroPacientes cadastro;
    private final DireitosDoTitular direitos;
    private final PacientesProperties propriedades;

    ResponsavelController(PacienteConsulta consulta, CadastroPacientes cadastro, DireitosDoTitular direitos,
                          PacientesProperties propriedades) {
        this.consulta = consulta;
        this.cadastro = cadastro;
        this.direitos = direitos;
        this.propriedades = propriedades;
    }

    @GetMapping("/{id}")
    ResponsavelView buscar(ClinicaId clinica, @PathVariable UUID id) {
        return consulta.responsavel(clinica.valor(), id)
                .orElseThrow(() -> new NaoEncontradoException("Responsável", id));
    }

    @GetMapping("/{id}/pacientes")
    List<PacienteView> pacientes(ClinicaId clinica, @PathVariable UUID id) {
        return consulta.pacientesDoResponsavel(clinica.valor(), id);
    }

    @PutMapping("/{id}")
    ResponsavelView atualizar(ClinicaId clinica, @PathVariable UUID id,
                              @Valid @RequestBody AtualizarResponsavelRequest r) {
        return cadastro.atualizarResponsavel(new AtualizarResponsavel(clinica.valor(), id, r.nome(),
                Telefone.digitado(r.telefone()), r.versao()));
    }

    /** Consentimento coletado presencialmente (termo assinado na recepção). */
    @PostMapping("/{id}/consentimento")
    ResponsavelView registrarConsentimento(ClinicaId clinica, @PathVariable UUID id,
                                           @RequestBody(required = false) ConsentimentoRequest r) {
        String versao = r != null && r.versaoTexto() != null && !r.versaoTexto().isBlank()
                ? r.versaoTexto() : propriedades.versaoConsentimentoAtual();
        return cadastro.registrarConsentimento(clinica.valor(), id, versao, Canal.PAINEL, EVIDENCIA_PAINEL);
    }

    @DeleteMapping("/{id}/consentimento")
    ResponsavelView revogarConsentimento(ClinicaId clinica, @PathVariable UUID id) {
        return cadastro.revogarConsentimento(clinica.valor(), id, Canal.PAINEL, EVIDENCIA_PAINEL);
    }

    /** LGPD art. 18, II: arquivo JSON com os dados do titular. */
    @GetMapping("/{id}/exportacao")
    ResponseEntity<ExportacaoDados> exportar(ClinicaId clinica, @PathVariable UUID id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dados-titular-" + id + ".json\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(direitos.exportar(clinica.valor(), id));
    }

    /** LGPD art. 18, IV e VI: eliminação por anonimização. Irreversível. Restrito a ADMIN no Passo 2. */
    @PostMapping("/{id}/anonimizacao")
    ResponseEntity<Void> anonimizar(ClinicaId clinica, @PathVariable UUID id) {
        direitos.anonimizar(clinica.valor(), id);
        return ResponseEntity.noContent().build();
    }
}
