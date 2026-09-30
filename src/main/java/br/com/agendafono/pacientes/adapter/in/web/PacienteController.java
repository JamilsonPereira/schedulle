package br.com.agendafono.pacientes.adapter.in.web;

import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.compartilhado.web.ClinicaId;
import br.com.agendafono.pacientes.Anexos;
import br.com.agendafono.pacientes.Anexos.NovoAnexo;
import br.com.agendafono.pacientes.AnexoInvalidoException;
import br.com.agendafono.pacientes.CadastroPacientes;
import br.com.agendafono.pacientes.CadastroPacientes.AtualizarPaciente;
import br.com.agendafono.pacientes.CadastroPacientes.CadastroPeloPainel;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.PacienteConsulta;
import br.com.agendafono.pacientes.PacienteConsulta.Pesquisa;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TipoAnexo;
import br.com.agendafono.pacientes.Views.AnexoView;
import br.com.agendafono.pacientes.Views.FichaPaciente;
import br.com.agendafono.pacientes.Views.PacienteResumo;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.adapter.in.web.PacientesDtos.AtualizarPacienteRequest;
import br.com.agendafono.pacientes.adapter.in.web.PacientesDtos.NovoPacienteRequest;
import br.com.agendafono.pacientes.adapter.in.web.PacientesDtos.VersaoRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pacientes")
class PacienteController {

    private final PacienteConsulta consulta;
    private final CadastroPacientes cadastro;
    private final Anexos anexos;

    PacienteController(PacienteConsulta consulta, CadastroPacientes cadastro, Anexos anexos) {
        this.consulta = consulta;
        this.cadastro = cadastro;
        this.anexos = anexos;
    }

    /** Lista do painel. {@code busca}: parte do nome (paciente ou responsável) ou dígitos do telefone. */
    @GetMapping
    Pagina<PacienteResumo> pesquisar(ClinicaId clinica,
                                     @RequestParam(required = false) String busca,
                                     @RequestParam(required = false) Boolean ativo,
                                     @RequestParam(defaultValue = "0") int pagina,
                                     @RequestParam(defaultValue = "25") int tamanho) {
        return consulta.pesquisar(clinica.valor(), new Pesquisa(busca, ativo, pagina, tamanho));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    ResponseEntity<PacienteView> cadastrar(ClinicaId clinica, @Valid @RequestBody NovoPacienteRequest r) {
        PacienteView criado = cadastro.cadastrarPeloPainel(new CadastroPeloPainel(clinica.valor(),
                Telefone.digitado(r.telefoneResponsavel()), r.nomeResponsavel(), r.nome(), r.dataNascimento(),
                r.demanda(), r.consentimentoColetado(), null /* usuário logado a partir do Passo 2 */));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @GetMapping("/{id}")
    FichaPaciente ficha(ClinicaId clinica, @PathVariable UUID id) {
        return consulta.ficha(clinica.valor(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    PacienteView atualizar(ClinicaId clinica, @PathVariable UUID id, @Valid @RequestBody AtualizarPacienteRequest r) {
        return cadastro.atualizarPaciente(new AtualizarPaciente(clinica.valor(), id, r.nome(), r.dataNascimento(),
                r.demanda(), r.versao()));
    }

    @PostMapping("/{id}/inativar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    PacienteView inativar(ClinicaId clinica, @PathVariable UUID id, @RequestBody(required = false) VersaoRequest r) {
        return cadastro.inativarPaciente(clinica.valor(), id, r != null ? r.versao() : null);
    }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO')")
    PacienteView reativar(ClinicaId clinica, @PathVariable UUID id, @RequestBody(required = false) VersaoRequest r) {
        return cadastro.reativarPaciente(clinica.valor(), id, r != null ? r.versao() : null);
    }

    @GetMapping("/{id}/anexos")
    List<AnexoView> listarAnexos(ClinicaId clinica, @PathVariable UUID id) {
        return anexos.listar(clinica.valor(), id);
    }

    @PostMapping(path = "/{id}/anexos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCAO', 'FONO')")
    ResponseEntity<AnexoView> anexar(ClinicaId clinica, @PathVariable UUID id,
                                     @RequestPart("arquivo") MultipartFile arquivo,
                                     @RequestParam(defaultValue = "DOCUMENTO") TipoAnexo tipo) {
        byte[] conteudo;
        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new AnexoInvalidoException("Não foi possível ler o arquivo enviado");
        }
        AnexoView criado = anexos.anexar(new NovoAnexo(clinica.valor(), id, tipo, arquivo.getOriginalFilename(),
                Canal.PAINEL, conteudo));
        var location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/anexos/{id}/conteudo").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }
}
