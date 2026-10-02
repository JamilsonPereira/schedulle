package br.com.agendafono.agenda.application;

import br.com.agendafono.agenda.Agendamento;
import br.com.agendafono.agenda.Disponibilidade.Origem;
import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.Presenca;
import br.com.agendafono.agenda.RecursoNaoEncontradoException;
import br.com.agendafono.agenda.SessaoView;
import br.com.agendafono.agenda.StatusSessao;
import br.com.agendafono.agenda.application.port.ConfiguracaoAgendaPort.ConfiguracaoProfissional;
import br.com.agendafono.agenda.application.port.SessaoRepository;
import br.com.agendafono.agenda.domain.Sessao;
import br.com.agendafono.agenda.TransicaoInvalidaException;
import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.pacientes.PacienteConsulta;
import br.com.agendafono.pacientes.Views.PacienteView;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Casos de uso de criação e alteração de sessões (SDD, seções 6.2 e 6.4).
 *
 * <p>Inserções e remarcações rodam num {@link TransactionTemplate} próprio: quando a constraint do banco
 * recusa o horário, a transação é desfeita por inteiro e só então as alternativas são calculadas,
 * numa leitura nova. No Postgres, depois de um erro, a transação corrente não aceita mais comandos.
 */
@Service
class AgendamentoService implements Agendamento, Presenca {

    private final SessaoRepository sessoes;
    private final AgendaDoProfissional agenda;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;
    private final Relogio relogio;
    private final PacienteConsulta pacientes;

    AgendamentoService(SessaoRepository sessoes, AgendaDoProfissional agenda, ApplicationEventPublisher eventos,
                       TransactionTemplate transacao, Relogio relogio, PacienteConsulta pacientes) {
        this.sessoes = sessoes;
        this.agenda = agenda;
        this.eventos = eventos;
        this.transacao = transacao;
        this.relogio = relogio;
        this.pacientes = pacientes;
    }

    // ------------------------------------------------------------------ criação

    @Override
    public SessaoView reservar(Reservar c) {
        exigirPacienteAtivo(c.clinicaId(), c.pacienteId());
        Instant agora = relogio.agora();
        ConfiguracaoProfissional cfg = agenda.configuracao(c.clinicaId(), c.profissionalId());
        Periodo periodo = Periodo.de(c.inicio(), agenda.duracao(c.duracaoMin(), cfg));

        UUID recurso = agenda.exigirDisponivel(c.clinicaId(), cfg, periodo, Origem.BOT, agora, null, false, null);

        Sessao sessao = Sessao.reservar(UUID.randomUUID(), c.clinicaId(), c.pacienteId(), c.profissionalId(),
                recurso, c.tipo(), periodo, agora, cfg.politica().ttlReserva());
        return inserir(sessao, cfg, Origem.BOT, agora);
    }

    @Override
    public SessaoView agendar(Agendar c) {
        exigirPacienteAtivo(c.clinicaId(), c.pacienteId());
        Instant agora = relogio.agora();
        ConfiguracaoProfissional cfg = agenda.configuracao(c.clinicaId(), c.profissionalId());
        Periodo periodo = Periodo.de(c.inicio(), agenda.duracao(c.duracaoMin(), cfg));

        UUID recurso = agenda.exigirDisponivel(c.clinicaId(), cfg, periodo, Origem.PAINEL, agora, c.recursoId(),
                c.permitirForaDaGrade(), null);

        Sessao sessao = Sessao.agendar(UUID.randomUUID(), c.clinicaId(), c.pacienteId(), c.profissionalId(),
                recurso, null, c.tipo(), periodo, agora);
        return inserir(sessao, cfg, Origem.PAINEL, agora);
    }

    private SessaoView inserir(Sessao sessao, ConfiguracaoProfissional cfg, Origem origem, Instant agora) {
        try {
            return transacao.execute(status -> {
                // Reservas vencidas ainda seguram o horário até o job rodar; libera antes de inserir.
                sessoes.cancelarReservasExpiradas(sessao.clinicaId(), sessao.profissionalId(), sessao.recursoId(),
                        agora);
                sessoes.inserir(sessao);
                publicar(sessao);
                return SessaoMapper.view(sessao);
            });
        } catch (HorarioIndisponivelException e) {
            throw new HorarioIndisponivelException(
                    agenda.alternativas(sessao.clinicaId(), cfg, sessao.periodo(), origem, agora, null));
        }
    }

    // ------------------------------------------------------------------ alterações

    @Override
    @Transactional
    public SessaoView confirmarReserva(UUID clinicaId, UUID sessaoId) {
        return alterar(clinicaId, sessaoId, null, s -> s.confirmarReserva(relogio.agora()));
    }

    @Override
    @Transactional
    public SessaoView confirmarPresenca(UUID clinicaId, UUID sessaoId, Integer versaoEsperada) {
        return alterar(clinicaId, sessaoId, versaoEsperada, Sessao::confirmarPresenca);
    }

    @Override
    @Transactional
    public SessaoView cancelar(UUID clinicaId, UUID sessaoId, Integer versaoEsperada) {
        return alterar(clinicaId, sessaoId, versaoEsperada, s -> s.cancelar(relogio.agora()));
    }

    @Override
    @Transactional
    public SessaoView avisarFalta(UUID clinicaId, UUID sessaoId, Integer versaoEsperada) {
        return alterar(clinicaId, sessaoId, versaoEsperada, s -> {
            ConfiguracaoProfissional cfg = agenda.configuracao(clinicaId, s.profissionalId());
            s.avisarFalta(relogio.agora(), cfg.politica().antecedenciaAvisoFalta());
        });
    }

    @Override
    @Transactional
    public SessaoView registrar(UUID clinicaId, UUID sessaoId, Resultado resultado, Integer versaoEsperada) {
        StatusSessao status = switch (resultado) {
            case ATENDIDA -> StatusSessao.ATENDIDA;
            case FALTA_SEM_AVISO -> StatusSessao.FALTA_SEM_AVISO;
        };
        return alterar(clinicaId, sessaoId, versaoEsperada, s -> s.registrarPresenca(status, relogio.agora()));
    }

    @Override
    public SessaoView remarcar(Remarcar c) {
        Instant agora = relogio.agora();
        Sessao sessao = carregar(c.clinicaId(), c.sessaoId());
        sessao.exigirVersao(c.versaoEsperada());
        ConfiguracaoProfissional cfg = agenda.configuracao(c.clinicaId(), sessao.profissionalId());
        Periodo novo = Periodo.de(c.novoInicio(), sessao.periodo().duracao());

        UUID recurso = agenda.exigirDisponivel(c.clinicaId(), cfg, novo, Origem.PAINEL, agora, c.recursoId(),
                c.permitirForaDaGrade(), sessao.id());
        sessao.remarcar(c.novoInicio(), recurso, agora);

        try {
            return transacao.execute(status -> {
                sessoes.atualizar(sessao);
                publicar(sessao);
                return SessaoMapper.view(sessao);
            });
        } catch (HorarioIndisponivelException e) {
            throw new HorarioIndisponivelException(
                    agenda.alternativas(c.clinicaId(), cfg, novo, Origem.PAINEL, agora, sessao.id()));
        }
    }

    // ------------------------------------------------------------------ leitura

    @Override
    @Transactional(readOnly = true)
    public Optional<SessaoView> buscar(UUID clinicaId, UUID sessaoId) {
        return sessoes.buscar(clinicaId, sessaoId).map(SessaoMapper::view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessaoView> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate) {
        if (!ate.isAfter(de)) {
            throw new IllegalArgumentException("'ate' deve ser posterior a 'de'");
        }
        return sessoes.listar(clinicaId, profissionalId, de, ate).stream().map(SessaoMapper::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessaoView> doPaciente(UUID clinicaId, UUID pacienteId, Instant de, Instant ate) {
        if (!ate.isAfter(de) || java.time.Duration.between(de, ate).toDays() > 400) {
            throw new IllegalArgumentException("Intervalo inválido (máximo de 400 dias)");
        }
        return sessoes.doPaciente(clinicaId, pacienteId, de, ate).stream().map(SessaoMapper::view).toList();
    }

    // ------------------------------------------------------------------ auxiliares

    private SessaoView alterar(UUID clinicaId, UUID sessaoId, Integer versaoEsperada, Consumer<Sessao> acao) {
        Sessao sessao = carregar(clinicaId, sessaoId);
        sessao.exigirVersao(versaoEsperada);
        acao.accept(sessao);
        sessoes.atualizar(sessao);
        publicar(sessao);
        return SessaoMapper.view(sessao);
    }

    /** O banco também garante (FK composta) que o paciente é da mesma clínica; aqui a mensagem fica clara. */
    private void exigirPacienteAtivo(UUID clinicaId, UUID pacienteId) {
        PacienteView paciente = pacientes.paciente(clinicaId, pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente", pacienteId));
        if (!paciente.ativo()) {
            throw new TransicaoInvalidaException("Paciente inativo não pode ser agendado");
        }
    }

    private Sessao carregar(UUID clinicaId, UUID sessaoId) {
        return sessoes.buscar(clinicaId, sessaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Sessão", sessaoId));
    }

    private void publicar(Sessao sessao) {
        sessao.extrairEventos().forEach(eventos::publishEvent);
    }
}
