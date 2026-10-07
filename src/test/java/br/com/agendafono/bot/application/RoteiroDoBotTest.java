package br.com.agendafono.bot.application;

import br.com.agendafono.bot.MensagemDoContato;
import br.com.agendafono.bot.TransbordoSolicitado;
import br.com.agendafono.bot.application.etapas.Etapas;
import br.com.agendafono.bot.application.etapas.Fluxos;
import br.com.agendafono.bot.application.port.ConversaRepository;
import br.com.agendafono.bot.application.port.SaidaWhatsApp;
import br.com.agendafono.bot.application.port.ServicosDaClinica;
import br.com.agendafono.bot.domain.Conversa;
import br.com.agendafono.bot.domain.EstadoConversa;
import br.com.agendafono.bot.domain.HorarioOcupadoException;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.MensagensBot;
import br.com.agendafono.bot.domain.Opcao;
import br.com.agendafono.bot.domain.PacienteDoContato;
import br.com.agendafono.bot.domain.ReservaVencidaException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roteiros de conversa de ponta a ponta no motor do bot, sem Spring nem banco: clínica, agenda e fila de saída
 * em memória, relógio controlado.
 */
class RoteiroDoBotTest {

    static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    static final UUID CLINICA = UUID.randomUUID();
    static final String TELEFONE = "5511999990000";

    Relogio relogio;
    ClinicaFake clinica;
    ConversasEmMemoria conversas;
    List<MensagemSaida> enviadas;
    List<Object> eventos;
    ServicoBot bot;
    int wamids;

    @BeforeEach
    void montar() {
        relogio = new Relogio(Instant.parse("2026-10-07T13:00:00Z")); // quarta, 10:00 em São Paulo
        clinica = new ClinicaFake(relogio);
        conversas = new ConversasEmMemoria();
        enviadas = new ArrayList<>();
        eventos = new ArrayList<>();
        SaidaWhatsApp saida = (c, phone, tel, msgs) -> enviadas.addAll(msgs);
        bot = new ServicoBot(conversas, clinica, saida, new Etapas(new Fluxos(clinica), "https://exemplo/priv"),
                new TradutorDeEntrada(JsonMapper.builder().build()), eventos::add, relogio);
    }

    // ------------------------------------------------------------------ roteiros

    @Test
    void contatoNovoAgendaAvaliacaoDoComecoAoFim() {
        texto("oi");
        assertThat(ultima()).isInstanceOf(MensagemSaida.Botoes.class);
        assertThat(ultima().corpo()).contains("Política de privacidade: https://exemplo/priv");
        assertThat(estado()).isEqualTo(EstadoConversa.CONSENTIMENTO);

        clicar(MensagensBot.ACEITO);
        assertThat(clinica.consentidos).contains(clinica.responsavelId(TELEFONE));
        assertThat(estado()).isEqualTo(EstadoConversa.MENU);

        clicar(MensagensBot.AGENDAR);
        assertThat(estado()).isEqualTo(EstadoConversa.NOVO_NOME);
        assertThat(ultima().corpo()).contains("nome completo");

        texto("  Maria   Souza ");
        assertThat(estado()).isEqualTo(EstadoConversa.NOVO_NASCIMENTO);
        assertThat(ultima().corpo()).contains("Maria");

        texto("10/03/19");
        assertThat(estado()).isEqualTo(EstadoConversa.NOVA_DEMANDA);
        assertThat(ultima()).isInstanceOf(MensagemSaida.Lista.class);

        clicar("demanda:GAGUEIRA");
        assertThat(clinica.cadastrados).hasSize(1);
        assertThat(clinica.cadastrados.get(0)).isEqualTo("Maria Souza|2019-03-10|GAGUEIRA");
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
        MensagemSaida.Lista lista = (MensagemSaida.Lista) ultima();
        assertThat(ids(lista.itens())).containsExactly("h1", "h2", "h3", "h4", "h5", MensagensBot.VER_MAIS,
                MensagensBot.RECEPCAO);
        assertThat(lista.itens().get(0).titulo()).isEqualTo("qui, 08/10 às 09:00");
        assertThat(lista.itens().get(0).descricao()).isEqualTo("com Dra. Ana");

        clicar("h2");
        assertThat(estado()).isEqualTo(EstadoConversa.CONFIRMAR);
        assertThat(ultima().corpo()).contains("Paciente: Maria Souza").contains("reservado por 5 minutos");

        clicar(MensagensBot.CONFIRMAR);
        assertThat(clinica.confirmadas).hasSize(1);
        assertThat(ultima().corpo()).contains("Pronto! A avaliação de Maria está agendada para qui, 08/10 às 10:00");
        assertThat(estado()).isEqualTo(EstadoConversa.MENU);
        assertThat(conversa().contexto().reservaId()).isNull();
        assertThat(eventos).isEmpty();
    }

    @Test
    void contatoComPacienteEscolhePorNumeroDigitado() {
        UUID resp = clinica.responsavelId(TELEFONE);
        clinica.consentidos.add(resp);
        clinica.pacientes.add(new PacienteDoContato("p1", UUID.randomUUID(), "João Pedro", "VOZ"));

        texto("bom dia");
        assertThat(estado()).isEqualTo(EstadoConversa.MENU);
        texto("1"); // "Agendar avaliação"
        assertThat(estado()).isEqualTo(EstadoConversa.PARA_QUEM);
        assertThat(ids(((MensagemSaida.Lista) ultima()).itens())).containsExactly("p1", MensagensBot.NOVO_PACIENTE);
        texto("1");
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
        assertThat(clinica.ultimaDemandaBuscada).isEqualTo("VOZ");
        assertThat(ultima().corpo()).contains("avaliação de João");
    }

    @Test
    void duasRespostasNaoEntendidasPassamParaARecepcaoEDepoisOBotSeCala() {
        consentido();
        texto("oi");
        texto("quanto custa?");
        assertThat(enviadas.get(enviadas.size() - 2).corpo()).startsWith("Não entendi");
        assertThat(estado()).isEqualTo(EstadoConversa.MENU);

        texto("e o valor?");
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO);
        assertThat(ultima().corpo()).contains("recepção vai continuar");
        assertThat(eventos).hasSize(1);
        assertThat(((TransbordoSolicitado) eventos.get(0)).motivo()).isEqualTo(TransbordoSolicitado.Motivo.NAO_ENTENDEU);

        int antes = enviadas.size();
        texto("alô?");
        assertThat(enviadas).hasSize(antes);
    }

    @Test
    void pedirAtendenteEmQualquerEtapaTransborda() {
        consentido();
        texto("oi");
        clicar(MensagensBot.AGENDAR);
        texto("quero falar com um atendente");
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO);
        assertThat(((TransbordoSolicitado) eventos.get(0)).motivo()).isEqualTo(TransbordoSolicitado.Motivo.PEDIU_ATENDENTE);
    }

    @Test
    void recepcaoDevolveAoBotQueRecomecaDoMenu() {
        consentido();
        texto("atendente");
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO);
        assertThat(bot.emAtendimento(CLINICA)).hasSize(1);
        assertThat(bot.emAtendimento(CLINICA).get(0).telefoneMascarado()).isEqualTo("+55 11 *****-0000");

        assertThat(bot.devolverAoBot(CLINICA, conversa().id())).isTrue();
        assertThat(bot.devolverAoBot(CLINICA, UUID.randomUUID())).isFalse();
        texto("oi");
        assertThat(estado()).isEqualTo(EstadoConversa.MENU);
        assertThat(bot.emAtendimento(CLINICA)).isEmpty();
    }

    @Test
    void depoisDe30MinutosParadaRecomecaDoInicio() {
        consentido();
        texto("oi");
        clicar(MensagensBot.AGENDAR);
        assertThat(estado()).isEqualTo(EstadoConversa.NOVO_NOME);

        relogio.avancar(Duration.ofMinutes(31));
        texto("Maria");
        assertThat(estado()).isEqualTo(EstadoConversa.MENU); // INICIO respondeu com o menu
        assertThat(conversa().contexto().novoNome()).isNull();
    }

    @Test
    void horarioTomadoPorOutroReofereceAsOpcoes() {
        chegarNosHorarios();
        clinica.ocupados.add("h1");
        clicar("h1");
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
        assertThat(enviadas.get(enviadas.size() - 2).corpo()).contains("acabou de ser ocupado");
        assertThat(ultima()).isInstanceOf(MensagemSaida.Lista.class);
    }

    @Test
    void reservaVencidaAoConfirmarReofereceAsOpcoes() {
        chegarNosHorarios();
        clicar("h1");
        clinica.vencerReservas = true;
        texto("sim");
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
        assertThat(enviadas.get(enviadas.size() - 2).corpo()).contains("O tempo para confirmar acabou");
        assertThat(clinica.confirmadas).isEmpty();
    }

    @Test
    void trocarHorarioCancelaAReservaEMostraAListaDeNovo() {
        chegarNosHorarios();
        clicar("h3");
        clicar(MensagensBot.TROCAR);
        assertThat(clinica.canceladas).hasSize(1);
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
    }

    @Test
    void verMaisDatasBuscaAPartirDoDiaSeguinteAoUltimoOferecido() {
        chegarNosHorarios();
        LocalDate ultimoDia = conversa().contexto().horariosOferecidos().get(4).inicio().atZone(SP).toLocalDate();
        clicar(MensagensBot.VER_MAIS);
        LocalDate primeiro = conversa().contexto().horariosOferecidos().get(0).inicio().atZone(SP).toLocalDate();
        assertThat(primeiro).isEqualTo(ultimoDia.plusDays(1));
    }

    @Test
    void semHorariosPassaParaARecepcao() {
        clinica.semHorarios = true;
        consentido();
        texto("oi");
        clicar(MensagensBot.AGENDAR);
        texto("Ana Lima");
        texto("01/02/2020");
        clicar("demanda:VOZ");
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO);
        assertThat(((TransbordoSolicitado) eventos.get(0)).motivo()).isEqualTo(TransbordoSolicitado.Motivo.SEM_HORARIOS);
    }

    @Test
    void naoAceitarOConsentimentoNaoAvancaENaoGravaNada() {
        texto("oi");
        clicar(MensagensBot.NAO_ACEITO);
        assertThat(estado()).isEqualTo(EstadoConversa.CONSENTIMENTO);
        assertThat(clinica.consentidos).isEmpty();
        clicar(MensagensBot.RECEPCAO);
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO);
    }

    @Test
    void dataDeNascimentoInvalidaPedeDeNovo() {
        consentido();
        texto("oi");
        clicar(MensagensBot.AGENDAR);
        texto("Ana Lima");
        texto("31/02/2020");
        assertThat(estado()).isEqualTo(EstadoConversa.NOVO_NASCIMENTO);
        assertThat(ultima().corpo()).contains("dia/mês/ano");
        texto("10/10/2030");
        assertThat(conversa().modo()).isEqualTo(Conversa.Modo.HUMANO); // segunda falha seguida
    }

    @Test
    void audioOuImagemContaComoNaoEntendido() {
        consentido();
        texto("oi");
        bot.processar(mensagem("AUDIO", null, "{\"type\":\"audio\",\"audio\":{\"id\":\"1\"}}"));
        assertThat(enviadas.get(enviadas.size() - 2).corpo()).startsWith("Não entendi");
    }

    // ------------------------------------------------------------------ auxiliares

    void consentido() {
        clinica.consentidos.add(clinica.responsavelId(TELEFONE));
    }

    void chegarNosHorarios() {
        consentido();
        texto("oi");
        clicar(MensagensBot.AGENDAR);
        texto("Ana Lima");
        texto("01/02/2020");
        clicar("demanda:LINGUAGEM");
        assertThat(estado()).isEqualTo(EstadoConversa.ESCOLHER_HORARIO);
    }

    void texto(String texto) {
        bot.processar(mensagem("TEXT", texto, "{\"type\":\"text\",\"text\":{\"body\":\"" + texto + "\"}}"));
    }

    void clicar(String id) {
        bot.processar(mensagem("INTERACTIVE", null,
                "{\"type\":\"interactive\",\"interactive\":{\"type\":\"list_reply\",\"list_reply\":{\"id\":\"" + id
                        + "\",\"title\":\"x\"}}}"));
    }

    MensagemDoContato mensagem(String tipo, String texto, String payload) {
        relogio.avancar(Duration.ofSeconds(20));
        return new MensagemDoContato(CLINICA, "PNID", TELEFONE, "wamid." + (++wamids), tipo, texto, payload,
                relogio.instant());
    }

    MensagemSaida ultima() {
        return enviadas.get(enviadas.size() - 1);
    }

    Conversa conversa() {
        return conversas.doResponsavel(CLINICA, clinica.responsavelId(TELEFONE)).orElseThrow();
    }

    EstadoConversa estado() {
        return conversa().estado();
    }

    static List<String> ids(List<Opcao> opcoes) {
        return opcoes.stream().map(Opcao::id).toList();
    }

    // ------------------------------------------------------------------ dublês

    static final class Relogio extends Clock {
        private Instant agora;

        Relogio(Instant agora) {
            this.agora = agora;
        }

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    static final class ConversasEmMemoria implements ConversaRepository {
        final Map<UUID, Conversa> porId = new HashMap<>();

        public Optional<Conversa> doResponsavel(UUID clinicaId, UUID responsavelId) {
            return porId.values().stream().filter(c -> c.responsavelId().equals(responsavelId)).findFirst();
        }

        public Optional<Conversa> buscar(UUID clinicaId, UUID conversaId) {
            return Optional.ofNullable(porId.get(conversaId)).filter(c -> c.clinicaId().equals(clinicaId));
        }

        public void salvar(Conversa c) {
            if (c.nova()) {
                c.marcarPersistida();
            } else {
                c.incrementarVersao();
            }
            porId.put(c.id(), c);
        }

        public void apagarDoResponsavel(UUID clinicaId, UUID responsavelId) {
            porId.values().removeIf(c -> c.responsavelId().equals(responsavelId));
        }

        public List<Conversa> emModoHumano(UUID clinicaId) {
            return porId.values().stream().filter(c -> c.modo() == Conversa.Modo.HUMANO).toList();
        }
    }

    /** Clínica com uma fono que atende de segunda a sexta às 09:00, 10:00 e 14:00. */
    static final class ClinicaFake implements ServicosDaClinica {
        final Relogio relogio;
        final Map<String, UUID> responsaveis = new HashMap<>();
        final Set<UUID> consentidos = new HashSet<>();
        final List<PacienteDoContato> pacientes = new ArrayList<>();
        final List<String> cadastrados = new ArrayList<>();
        final Set<String> ocupados = new HashSet<>();
        final List<UUID> confirmadas = new ArrayList<>();
        final List<UUID> canceladas = new ArrayList<>();
        final UUID fono = UUID.randomUUID();
        boolean semHorarios;
        boolean vencerReservas;
        String ultimaDemandaBuscada;

        ClinicaFake(Relogio relogio) {
            this.relogio = relogio;
        }

        UUID responsavelId(String telefone) {
            return responsaveis.computeIfAbsent(telefone, t -> UUID.randomUUID());
        }

        public DadosDaClinica clinica(UUID clinicaId) {
            return new DadosDaClinica("Clínica Fala Bem", SP);
        }

        public Contato identificar(UUID clinicaId, String telefoneDigitos) {
            UUID id = responsavelId(telefoneDigitos);
            return new Contato(id, consentidos.contains(id));
        }

        public void registrarConsentimento(UUID clinicaId, UUID responsavelId, String wamid) {
            consentidos.add(responsavelId);
        }

        public List<PacienteDoContato> pacientes(UUID clinicaId, UUID responsavelId) {
            return List.copyOf(pacientes);
        }

        public UUID cadastrarPaciente(UUID clinicaId, UUID responsavelId, String nome, LocalDate nascimento,
                                      String demanda) {
            cadastrados.add(nome + "|" + nascimento + "|" + demanda);
            return UUID.randomUUID();
        }

        public BuscaDeHorarios horarios(UUID clinicaId, String demanda, LocalDate aPartirDe, int quantidade) {
            ultimaDemandaBuscada = demanda;
            if (semHorarios) {
                return new BuscaDeHorarios(List.of(), null);
            }
            List<HorarioOferta> lista = new ArrayList<>();
            LocalDate dia = aPartirDe.plusDays(aPartirDe.equals(LocalDate.ofInstant(relogio.instant(), SP)) ? 1 : 0);
            while (lista.size() < quantidade) {
                if (dia.getDayOfWeek() != DayOfWeek.SATURDAY && dia.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    for (int hora : new int[] {9, 10}) {
                        if (lista.size() < quantidade) {
                            Instant inicio = dia.atTime(LocalTime.of(hora, 0)).atZone(SP).toInstant();
                            lista.add(new HorarioOferta("h" + (lista.size() + 1), fono, "Dra. Ana", inicio,
                                    inicio.plus(Duration.ofMinutes(40))));
                        }
                    }
                }
                dia = dia.plusDays(1);
            }
            LocalDate ultimo = lista.get(lista.size() - 1).inicio().atZone(SP).toLocalDate();
            return new BuscaDeHorarios(lista, ultimo.plusDays(1));
        }

        public Reserva reservar(UUID clinicaId, UUID pacienteId, HorarioOferta horario) {
            if (ocupados.remove(horario.id())) {
                throw new HorarioOcupadoException();
            }
            return new Reserva(UUID.randomUUID(), relogio.instant().plus(Duration.ofMinutes(5)));
        }

        public void confirmar(UUID clinicaId, UUID reservaId) {
            if (vencerReservas) {
                throw new ReservaVencidaException();
            }
            confirmadas.add(reservaId);
        }

        public void cancelarReserva(UUID clinicaId, UUID reservaId) {
            canceladas.add(reservaId);
        }

        public DadosDoResponsavel responsavel(UUID clinicaId, UUID responsavelId) {
            return new DadosDoResponsavel(null, "+55 11 *****-0000");
        }
    }
}
