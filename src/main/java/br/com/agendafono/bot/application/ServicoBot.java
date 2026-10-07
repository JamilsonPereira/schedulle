package br.com.agendafono.bot.application;

import br.com.agendafono.bot.AtendimentoHumano;
import br.com.agendafono.bot.Bot;
import br.com.agendafono.bot.ConversaEmAtendimento;
import br.com.agendafono.bot.MensagemDoContato;
import br.com.agendafono.bot.TransbordoSolicitado;
import br.com.agendafono.bot.TransbordoSolicitado.Motivo;
import br.com.agendafono.bot.application.etapas.Etapas;
import br.com.agendafono.bot.application.etapas.Situacao;
import br.com.agendafono.bot.application.port.ConversaRepository;
import br.com.agendafono.bot.application.port.SaidaWhatsApp;
import br.com.agendafono.bot.application.port.ServicosDaClinica;
import br.com.agendafono.bot.application.port.ServicosDaClinica.Contato;
import br.com.agendafono.bot.application.port.ServicosDaClinica.DadosDoResponsavel;
import br.com.agendafono.bot.domain.Conversa;
import br.com.agendafono.bot.domain.Entrada;
import br.com.agendafono.bot.domain.EstadoConversa;
import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.MensagensBot;
import br.com.agendafono.bot.domain.Transicao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Motor do bot (SDD backend, seção 8): uma mensagem entra, a etapa do estado atual decide a transição, a conversa
 * é gravada e as respostas vão para a fila de saída na mesma transação (outbox).
 *
 * <p>As escritas em outros módulos (cadastro, reserva, confirmação) rodam fora desta transação, cada uma na sua;
 * assim uma recusa da agenda não deixa a transação do bot marcada para rollback. Se a gravação da conversa falhar
 * depois, a pré-reserva vence sozinha em poucos minutos.
 */
@Transactional
public class ServicoBot implements Bot, AtendimentoHumano {

    private static final Logger log = LoggerFactory.getLogger(ServicoBot.class);

    private final ConversaRepository conversas;
    private final ServicosDaClinica servicos;
    private final SaidaWhatsApp saida;
    private final Etapas etapas;
    private final TradutorDeEntrada tradutor;
    private final ApplicationEventPublisher eventos;
    private final Clock relogio;

    public ServicoBot(ConversaRepository conversas, ServicosDaClinica servicos, SaidaWhatsApp saida, Etapas etapas,
                      TradutorDeEntrada tradutor, ApplicationEventPublisher eventos, Clock relogio) {
        this.conversas = conversas;
        this.servicos = servicos;
        this.saida = saida;
        this.etapas = etapas;
        this.tradutor = tradutor;
        this.eventos = eventos;
        this.relogio = relogio;
    }

    @Override
    public void processar(MensagemDoContato mensagem) {
        Entrada entrada = tradutor.traduzir(mensagem);
        UUID clinicaId = mensagem.clinicaId();
        Contato contato = servicos.identificar(clinicaId, mensagem.telefone());
        Conversa conversa = conversas.doResponsavel(clinicaId, contato.responsavelId())
                .orElseGet(() -> Conversa.nova(UUID.randomUUID(), clinicaId, contato.responsavelId()));

        if (conversa.modo() == Conversa.Modo.HUMANO) {
            // A recepção está respondendo pelo painel: o bot fica em silêncio.
            conversa.registrarMensagem(mensagem.recebidaEm());
            conversas.salvar(conversa);
            return;
        }

        conversa.reiniciarSeParada(mensagem.recebidaEm());
        if (!contato.consentido() && conversa.estado() != EstadoConversa.INICIO
                && conversa.estado() != EstadoConversa.CONSENTIMENTO) {
            conversa.recomecar(); // consentimento revogado no meio do caminho
        }

        Situacao situacao = new Situacao(clinicaId, contato.responsavelId(), contato.consentido(),
                servicos.clinica(clinicaId), relogio.instant());
        Transicao transicao = entrada.pedeAtendente()
                ? Transicao.transbordar(conversa.contexto(), Motivo.PEDIU_ATENDENTE.name())
                : etapas.de(conversa.estado()).tratar(entrada, conversa.contexto(), situacao);

        boolean transbordou = conversa.aplicar(transicao);
        List<MensagemSaida> respostas = respostas(transicao, transbordou);

        conversa.registrarMensagem(mensagem.recebidaEm());
        conversas.salvar(conversa);
        saida.enfileirar(clinicaId, mensagem.phoneNumberId(), mensagem.telefone(), respostas);

        if (transbordou) {
            Motivo motivo = transicao.motivoTransbordo() == null ? Motivo.NAO_ENTENDEU
                    : Motivo.valueOf(transicao.motivoTransbordo());
            log.info("Conversa {} passou para a recepção ({})", conversa.id(), motivo);
            eventos.publishEvent(new TransbordoSolicitado(clinicaId, conversa.id(), motivo));
        }
    }

    private static List<MensagemSaida> respostas(Transicao t, boolean transbordou) {
        List<MensagemSaida> respostas = new ArrayList<>();
        if (t.resultado() == Transicao.Resultado.NAO_ENTENDI) {
            if (!transbordou) {
                respostas.add(MensagensBot.naoEntendi());
                respostas.addAll(t.respostas());
            }
        } else {
            respostas.addAll(t.respostas());
        }
        if (transbordou) {
            respostas.add(MensagensBot.transbordo());
        }
        return respostas;
    }

    // ------------------------------------------------------------------ atendimento humano

    @Override
    @Transactional(readOnly = true)
    public List<ConversaEmAtendimento> emAtendimento(UUID clinicaId) {
        return conversas.emModoHumano(clinicaId).stream().map(c -> {
            DadosDoResponsavel r = servicos.responsavel(clinicaId, c.responsavelId());
            return new ConversaEmAtendimento(c.id(), c.responsavelId(), r.nome(), r.telefoneMascarado(),
                    c.ultimaMensagemEm());
        }).toList();
    }

    @Override
    public boolean devolverAoBot(UUID clinicaId, UUID conversaId) {
        return conversas.buscar(clinicaId, conversaId).map(c -> {
            c.devolverAoBot();
            conversas.salvar(c);
            return true;
        }).orElse(false);
    }
}
