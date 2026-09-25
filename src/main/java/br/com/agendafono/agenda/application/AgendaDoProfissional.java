package br.com.agendafono.agenda.application;

import br.com.agendafono.agenda.Disponibilidade.Origem;
import br.com.agendafono.agenda.HorarioForaDaAgendaException;
import br.com.agendafono.agenda.HorarioForaDaAgendaException.Motivo;
import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.RecursoNaoEncontradoException;
import br.com.agendafono.agenda.application.port.ConfiguracaoAgendaPort;
import br.com.agendafono.agenda.application.port.ConfiguracaoAgendaPort.ConfiguracaoProfissional;
import br.com.agendafono.agenda.application.port.SessaoRepository;
import br.com.agendafono.agenda.domain.BlocoGrade;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade.Entrada;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade.Verificacao;
import br.com.agendafono.agenda.domain.CalculadoraDisponibilidade.Veredito;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Monta a entrada da {@link CalculadoraDisponibilidade} a partir do banco e traduz os vereditos
 * em exceções da API. Compartilhado entre consulta de disponibilidade e agendamento.
 */
@Component
class AgendaDoProfissional {

    /** O painel não tem janela máxima; na prática, 10 anos. */
    private static final Duration SEM_LIMITE = Duration.ofDays(3650);
    private static final int DIAS_BUSCA_ALTERNATIVAS = 7;
    private static final int QUANTIDADE_ALTERNATIVAS = 3;

    private final ConfiguracaoAgendaPort configuracoes;
    private final SessaoRepository sessoes;
    private final CalculadoraDisponibilidade calculadora = new CalculadoraDisponibilidade();

    AgendaDoProfissional(ConfiguracaoAgendaPort configuracoes, SessaoRepository sessoes) {
        this.configuracoes = configuracoes;
        this.sessoes = sessoes;
    }

    ConfiguracaoProfissional configuracao(UUID clinicaId, UUID profissionalId) {
        return configuracoes.carregar(clinicaId, profissionalId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Profissional", profissionalId));
    }

    Duration duracao(Integer duracaoMin, ConfiguracaoProfissional cfg) {
        return duracaoMin != null ? Duration.ofMinutes(duracaoMin) : cfg.duracaoPadrao();
    }

    List<HorarioLivre> livres(UUID clinicaId, ConfiguracaoProfissional cfg, LocalDate de, LocalDate ate,
                              Duration duracao, Origem origem, Instant agora) {
        return calculadora.calcular(entrada(clinicaId, cfg, de, ate, duracao, origem, agora, null, null));
    }

    /**
     * Confere se {@code pedido} pode ser agendado e devolve o recurso (sala) a usar.
     *
     * @param recursoPedido       sala escolhida explicitamente; nula = a da grade
     * @param permitirForaDaGrade encaixe da recepção: ignora a grade, mas não bloqueios nem ocupação
     * @param ignorarSessaoId     a própria sessão, na remarcação
     * @throws HorarioForaDaAgendaException se o horário não é permitido
     * @throws HorarioIndisponivelException se já está ocupado (com alternativas)
     */
    UUID exigirDisponivel(UUID clinicaId, ConfiguracaoProfissional cfg, Periodo pedido, Origem origem,
                          Instant agora, UUID recursoPedido, boolean permitirForaDaGrade, UUID ignorarSessaoId) {
        LocalDate dia = pedido.inicio().atZone(cfg.zona()).toLocalDate();
        Entrada e = entrada(clinicaId, cfg, dia, dia, pedido.duracao(), origem, agora, recursoPedido, ignorarSessaoId);

        Verificacao v;
        if (permitirForaDaGrade) {
            v = calculadora.verificarSemGrade(e, pedido, recursoPedido);
        } else {
            v = calculadora.verificar(e, pedido);
            // Sala escolhida explicitamente: a grade só valida o horário; a ocupação é a da sala escolhida.
            boolean dentroDaGrade = v.veredito() == Veredito.DISPONIVEL || v.veredito() == Veredito.OCUPADO;
            if (recursoPedido != null && dentroDaGrade) {
                v = calculadora.verificarSemGrade(e, pedido, recursoPedido);
            }
        }
        UUID recurso = recursoPedido != null ? recursoPedido : v.recursoDaGrade();

        switch (v.veredito()) {
            case DISPONIVEL -> {
                return recurso;
            }
            case OCUPADO -> throw new HorarioIndisponivelException(
                    alternativas(clinicaId, cfg, pedido, origem, agora, ignorarSessaoId));
            case BLOQUEADO -> throw new HorarioForaDaAgendaException(Motivo.BLOQUEADO);
            case FORA_DA_GRADE -> throw new HorarioForaDaAgendaException(Motivo.FORA_DA_GRADE);
            case ANTES_DO_PERMITIDO -> throw new HorarioForaDaAgendaException(
                    origem == Origem.BOT ? Motivo.ANTECEDENCIA_MINIMA : Motivo.NO_PASSADO);
            case DEPOIS_DO_PERMITIDO -> throw new HorarioForaDaAgendaException(Motivo.ALEM_DA_JANELA);
            default -> throw new IllegalStateException("Veredito inesperado: " + v.veredito());
        }
    }

    /** Até 3 horários livres mais próximos do pedido, nos 7 dias a partir do dia pedido, em ordem cronológica. */
    List<HorarioLivre> alternativas(UUID clinicaId, ConfiguracaoProfissional cfg, Periodo pedido, Origem origem,
                                    Instant agora, UUID ignorarSessaoId) {
        LocalDate dia = pedido.inicio().atZone(cfg.zona()).toLocalDate();
        Entrada e = entrada(clinicaId, cfg, dia, dia.plusDays(DIAS_BUSCA_ALTERNATIVAS - 1), pedido.duracao(),
                origem, agora, null, ignorarSessaoId);
        Instant alvo = pedido.inicio();
        return calculadora.calcular(e).stream()
                .sorted(Comparator.comparing((HorarioLivre h) ->
                        Duration.between(alvo, h.periodo().inicio()).abs()))
                .limit(QUANTIDADE_ALTERNATIVAS)
                .sorted(Comparator.comparing((HorarioLivre h) -> h.periodo().inicio()))
                .toList();
    }

    private Entrada entrada(UUID clinicaId, ConfiguracaoProfissional cfg, LocalDate de, LocalDate ate,
                            Duration duracao, Origem origem, Instant agora, UUID recursoExtra,
                            UUID ignorarSessaoId) {
        Periodo intervalo = new Periodo(
                de.atStartOfDay(cfg.zona()).toInstant(),
                ate.plusDays(1).atStartOfDay(cfg.zona()).toInstant());

        Set<UUID> recursos = new LinkedHashSet<>();
        cfg.grade().stream().map(BlocoGrade::recursoId).filter(Objects::nonNull).forEach(recursos::add);
        if (recursoExtra != null) {
            recursos.add(recursoExtra);
        }

        Instant naoAntesDe = origem == Origem.BOT ? agora.plus(cfg.politica().antecedenciaMinima()) : agora;
        Instant naoDepoisDe = agora.plus(origem == Origem.BOT ? cfg.politica().janelaMaxima() : SEM_LIMITE);

        return new Entrada(
                cfg.zona(), de, ate, cfg.grade(),
                configuracoes.bloqueios(clinicaId, cfg.profissionalId(), intervalo),
                sessoes.ocupadosDoProfissional(clinicaId, cfg.profissionalId(), intervalo, agora, ignorarSessaoId),
                sessoes.ocupadosPorRecurso(clinicaId, recursos, intervalo, agora, ignorarSessaoId),
                duracao, cfg.politica().passoPara(duracao), naoAntesDe, naoDepoisDe);
    }
}
