package br.com.agendafono.pacientes.application;

import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.pacientes.CadastroPacientes;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.ConsentimentoAusenteException;
import br.com.agendafono.pacientes.NaoEncontradoException;
import br.com.agendafono.pacientes.PacienteConsulta;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TelefoneJaCadastradoException;
import br.com.agendafono.pacientes.Views.FichaPaciente;
import br.com.agendafono.pacientes.Views.PacienteResumo;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;
import br.com.agendafono.pacientes.application.port.AnexoRepository;
import br.com.agendafono.pacientes.application.port.PacienteRepository;
import br.com.agendafono.pacientes.application.port.ResponsavelRepository;
import br.com.agendafono.pacientes.domain.Paciente;
import br.com.agendafono.pacientes.domain.Responsavel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/** Casos de uso de cadastro e consulta de responsáveis e pacientes. */
@Service
class PacientesService implements CadastroPacientes, PacienteConsulta {

    private final ResponsavelRepository responsaveis;
    private final PacienteRepository pacientes;
    private final AnexoRepository anexos;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;
    private final Relogio relogio;
    private final PacientesProperties propriedades;
    private final ZoneId fuso;

    PacientesService(ResponsavelRepository responsaveis, PacienteRepository pacientes, AnexoRepository anexos,
                     ApplicationEventPublisher eventos, TransactionTemplate transacao, Relogio relogio,
                     PacientesProperties propriedades) {
        this.responsaveis = responsaveis;
        this.pacientes = pacientes;
        this.anexos = anexos;
        this.eventos = eventos;
        this.transacao = transacao;
        this.relogio = relogio;
        this.propriedades = propriedades;
        this.fuso = ZoneId.of(propriedades.fuso());
    }

    // ------------------------------------------------------------------ responsável

    @Override
    public ResponsavelView identificarResponsavel(UUID clinicaId, Telefone telefone) {
        try {
            return transacao.execute(s -> responsaveis.buscarPorTelefone(clinicaId, telefone.variantes())
                    .map(PacientesMapper::view)
                    .orElseGet(() -> {
                        Responsavel novo = Responsavel.novo(UUID.randomUUID(), clinicaId, telefone, null);
                        responsaveis.inserir(novo);
                        return PacientesMapper.view(novo);
                    }));
        } catch (TelefoneJaCadastradoException corrida) {
            // Outra requisição criou o mesmo responsável ao mesmo tempo: basta ler o que ela gravou.
            return transacao.execute(s -> responsaveis.buscarPorTelefone(clinicaId, telefone.variantes())
                    .map(PacientesMapper::view)
                    .orElseThrow(() -> corrida));
        }
    }

    @Override
    @Transactional
    public ResponsavelView registrarConsentimento(UUID clinicaId, UUID responsavelId, String versaoTexto,
                                                  Canal canal, String evidencia) {
        return alterarResponsavel(clinicaId, responsavelId, null,
                r -> r.registrarConsentimento(versaoTexto, canal, evidencia, relogio.agora()));
    }

    @Override
    @Transactional
    public ResponsavelView revogarConsentimento(UUID clinicaId, UUID responsavelId, Canal canal, String evidencia) {
        return alterarResponsavel(clinicaId, responsavelId, null,
                r -> r.revogarConsentimento(canal, evidencia, relogio.agora()));
    }

    @Override
    @Transactional
    public ResponsavelView atualizarResponsavel(AtualizarResponsavel c) {
        responsaveis.buscarPorTelefone(c.clinicaId(), c.telefone().variantes())
                .filter(outro -> !outro.id().equals(c.responsavelId()))
                .ifPresent(outro -> {
                    throw new TelefoneJaCadastradoException();
                });
        return alterarResponsavel(c.clinicaId(), c.responsavelId(), c.versaoEsperada(),
                r -> r.atualizar(c.nome(), c.telefone()));
    }

    // ------------------------------------------------------------------ paciente

    @Override
    @Transactional
    public PacienteView cadastrarPaciente(CadastrarPaciente c) {
        Responsavel responsavel = carregarResponsavel(c.clinicaId(), c.responsavelId());
        Paciente paciente = responsavel.cadastrarPaciente(UUID.randomUUID(), c.nome(), c.dataNascimento(),
                c.demanda(), hoje());
        pacientes.inserir(paciente);
        publicar(responsavel.extrairEventos());
        return PacientesMapper.view(paciente, hoje());
    }

    @Override
    @Transactional
    public PacienteView cadastrarPeloPainel(CadastroPeloPainel c) {
        Optional<Responsavel> existente =
                responsaveis.buscarPorTelefone(c.clinicaId(), c.telefoneResponsavel().variantes());
        Responsavel responsavel = existente.orElseGet(() ->
                Responsavel.novo(UUID.randomUUID(), c.clinicaId(), c.telefoneResponsavel(), c.nomeResponsavel()));
        responsavel.completarNome(c.nomeResponsavel());

        if (!responsavel.possuiConsentimento()) {
            if (!c.consentimentoColetado()) {
                throw new ConsentimentoAusenteException();
            }
            String quem = c.registradoPor() == null || c.registradoPor().isBlank() ? "recepcao" : c.registradoPor();
            responsavel.registrarConsentimento(propriedades.versaoConsentimentoAtual(), Canal.PAINEL,
                    "painel:" + quem, relogio.agora());
        }

        Paciente paciente = responsavel.cadastrarPaciente(UUID.randomUUID(), c.nomePaciente(), c.dataNascimento(),
                c.demanda(), hoje());

        if (existente.isPresent()) {
            responsaveis.atualizar(responsavel);
        } else {
            responsaveis.inserir(responsavel);
        }
        pacientes.inserir(paciente);
        publicar(responsavel.extrairEventos());
        return PacientesMapper.view(paciente, hoje());
    }

    @Override
    @Transactional
    public PacienteView atualizarPaciente(AtualizarPaciente c) {
        return alterarPaciente(c.clinicaId(), c.pacienteId(), c.versaoEsperada(),
                p -> p.atualizar(c.nome(), c.dataNascimento(), c.demanda(), hoje()));
    }

    @Override
    @Transactional
    public PacienteView inativarPaciente(UUID clinicaId, UUID pacienteId, Integer versaoEsperada) {
        return alterarPaciente(clinicaId, pacienteId, versaoEsperada, Paciente::inativar);
    }

    @Override
    @Transactional
    public PacienteView reativarPaciente(UUID clinicaId, UUID pacienteId, Integer versaoEsperada) {
        return alterarPaciente(clinicaId, pacienteId, versaoEsperada, Paciente::reativar);
    }

    // ------------------------------------------------------------------ consultas

    @Override
    @Transactional(readOnly = true)
    public Optional<PacienteView> paciente(UUID clinicaId, UUID pacienteId) {
        return pacientes.buscar(clinicaId, pacienteId).map(p -> PacientesMapper.view(p, hoje()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PacienteView> pacientesDoResponsavel(UUID clinicaId, UUID responsavelId) {
        LocalDate hoje = hoje();
        return pacientes.doResponsavel(clinicaId, responsavelId).stream()
                .map(p -> PacientesMapper.view(p, hoje)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> nomes(UUID clinicaId, Collection<UUID> pacienteIds) {
        return pacientes.nomes(clinicaId, pacienteIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResponsavelView> responsavel(UUID clinicaId, UUID responsavelId) {
        return responsaveis.buscar(clinicaId, responsavelId).map(PacientesMapper::view);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResponsavelView> responsavelPorTelefone(UUID clinicaId, Telefone telefone) {
        return responsaveis.buscarPorTelefone(clinicaId, telefone.variantes()).map(PacientesMapper::view);
    }

    @Override
    @Transactional(readOnly = true)
    public FichaPaciente ficha(UUID clinicaId, UUID pacienteId) {
        LocalDate hoje = hoje();
        Paciente paciente = carregarPaciente(clinicaId, pacienteId);
        Responsavel responsavel = carregarResponsavel(clinicaId, paciente.responsavelId());
        List<PacienteView> outros = pacientes.doResponsavel(clinicaId, responsavel.id()).stream()
                .filter(p -> !p.id().equals(pacienteId))
                .map(p -> PacientesMapper.view(p, hoje))
                .toList();
        return new FichaPaciente(PacientesMapper.view(paciente, hoje), PacientesMapper.view(responsavel), outros,
                anexos.doPaciente(clinicaId, pacienteId).stream().map(PacientesMapper::view).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<PacienteResumo> pesquisar(UUID clinicaId, Pesquisa q) {
        LocalDate hoje = hoje();
        return pacientes.pesquisar(clinicaId, q.termo(), q.ativo(), q.pagina(), q.tamanho())
                .map(linha -> PacientesMapper.resumo(linha, hoje));
    }

    // ------------------------------------------------------------------ auxiliares

    private ResponsavelView alterarResponsavel(UUID clinicaId, UUID responsavelId, Integer versaoEsperada,
                                               Consumer<Responsavel> acao) {
        Responsavel r = carregarResponsavel(clinicaId, responsavelId);
        r.exigirVersao(versaoEsperada);
        acao.accept(r);
        responsaveis.atualizar(r);
        publicar(r.extrairEventos());
        return PacientesMapper.view(r);
    }

    private PacienteView alterarPaciente(UUID clinicaId, UUID pacienteId, Integer versaoEsperada,
                                         Consumer<Paciente> acao) {
        Paciente p = carregarPaciente(clinicaId, pacienteId);
        p.exigirVersao(versaoEsperada);
        acao.accept(p);
        pacientes.atualizar(p);
        return PacientesMapper.view(p, hoje());
    }

    private Responsavel carregarResponsavel(UUID clinicaId, UUID responsavelId) {
        return responsaveis.buscar(clinicaId, responsavelId)
                .orElseThrow(() -> new NaoEncontradoException("Responsável", responsavelId));
    }

    private Paciente carregarPaciente(UUID clinicaId, UUID pacienteId) {
        return pacientes.buscar(clinicaId, pacienteId)
                .orElseThrow(() -> new NaoEncontradoException("Paciente", pacienteId));
    }

    private LocalDate hoje() {
        return LocalDate.ofInstant(relogio.agora(), fuso);
    }

    private void publicar(List<Object> lista) {
        lista.forEach(eventos::publishEvent);
    }
}
