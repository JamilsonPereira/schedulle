package br.com.agendafono.bot.application.etapas;

import br.com.agendafono.bot.TransbordoSolicitado.Motivo;
import br.com.agendafono.bot.application.port.ServicosDaClinica.Reserva;
import br.com.agendafono.bot.domain.ContextoConversa;
import br.com.agendafono.bot.domain.Entrada;
import br.com.agendafono.bot.domain.EstadoConversa;
import br.com.agendafono.bot.domain.HorarioOcupadoException;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.MensagensBot;
import br.com.agendafono.bot.domain.Opcao;
import br.com.agendafono.bot.domain.PacienteDoContato;
import br.com.agendafono.bot.domain.ReservaVencidaException;
import br.com.agendafono.bot.domain.Transicao;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Todas as etapas da conversa (Especificação do MVP, seção 6), registradas num mapa por estado.
 * Sem convênio no MVP: depois dos dados do paciente vai direto para a escolha de horário.
 */
public final class Etapas {

    private final Map<EstadoConversa, Etapa> porEstado = new EnumMap<>(EstadoConversa.class);

    public static final String URL_POLITICA_PADRAO = "https://agendafono.com.br/privacidade";

    public Etapas(Fluxos fluxos) {
        this(fluxos, URL_POLITICA_PADRAO);
    }

    /** @param urlPolitica endereço da política de privacidade mostrado no consentimento */
    public Etapas(Fluxos fluxos, String urlPolitica) {
        List.of(new Inicio(fluxos, urlPolitica), new Consentimento(fluxos, urlPolitica), new Menu(fluxos), new ParaQuem(fluxos),
                        new NovoNome(), new NovoNascimento(), new NovaDemanda(fluxos), new EscolherHorario(fluxos),
                        new Confirmar(fluxos), new Humano())
                .forEach(e -> porEstado.put(e.estado(), e));
    }

    public Etapa de(EstadoConversa estado) {
        return porEstado.get(estado);
    }

    // ------------------------------------------------------------------ INICIO

    static final class Inicio implements Etapa {
        private final Fluxos fluxos;
        private final String urlPolitica;

        Inicio(Fluxos fluxos, String urlPolitica) {
            this.fluxos = fluxos;
            this.urlPolitica = urlPolitica;
        }

        public EstadoConversa estado() {
            return EstadoConversa.INICIO;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            if (!s.consentido()) {
                return Transicao.para(EstadoConversa.CONSENTIMENTO, ContextoConversa.vazio(),
                        MensagensBot.consentimento(s.clinica().nome(), urlPolitica));
            }
            return fluxos.menu(ContextoConversa.vazio(), s,
                    new MensagemSaida.Texto("Olá! Aqui é o atendimento da %s.".formatted(s.clinica().nome())));
        }
    }

    // ------------------------------------------------------------------ CONSENTIMENTO

    static final class Consentimento implements Etapa {
        private static final Set<String> SIM = Set.of("sim", "aceito", "concordo", "ok", "pode", "s");
        private static final Set<String> NAO = Set.of("nao", "nao aceito", "n", "nao concordo");
        private static final List<Opcao> OPCOES = List.of(new Opcao(MensagensBot.ACEITO, "Aceito"),
                new Opcao(MensagensBot.NAO_ACEITO, "Não aceito"), new Opcao(MensagensBot.RECEPCAO, "Falar com a recepção"));
        private final Fluxos fluxos;
        private final String urlPolitica;

        Consentimento(Fluxos fluxos, String urlPolitica) {
            this.fluxos = fluxos;
            this.urlPolitica = urlPolitica;
        }

        public EstadoConversa estado() {
            return EstadoConversa.CONSENTIMENTO;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            String escolha = e.escolha(OPCOES).orElseGet(() -> {
                String t = e.normalizado();
                if (SIM.contains(t)) return MensagensBot.ACEITO;
                if (NAO.contains(t)) return MensagensBot.NAO_ACEITO;
                return null;
            });
            if (MensagensBot.ACEITO.equals(escolha)) {
                fluxos.servicos().registrarConsentimento(s.clinicaId(), s.responsavelId(), e.wamid());
                return fluxos.menu(ctx, s, new MensagemSaida.Texto("Obrigado! Seus dados estão protegidos."));
            }
            if (MensagensBot.NAO_ACEITO.equals(escolha)) {
                return Transicao.para(EstadoConversa.CONSENTIMENTO, ctx, MensagensBot.semConsentimento());
            }
            if (MensagensBot.RECEPCAO.equals(escolha)) {
                return Transicao.transbordar(ctx, Motivo.SEM_CONSENTIMENTO.name());
            }
            return Transicao.naoEntendi(estado(), ctx, MensagensBot.consentimento(s.clinica().nome(), urlPolitica));
        }
    }

    // ------------------------------------------------------------------ MENU

    static final class Menu implements Etapa {
        private static final List<Opcao> OPCOES = List.of(new Opcao(MensagensBot.AGENDAR, "Agendar avaliação"),
                new Opcao(MensagensBot.RECEPCAO, "Falar com a recepção"));
        private final Fluxos fluxos;

        Menu(Fluxos fluxos) {
            this.fluxos = fluxos;
        }

        public EstadoConversa estado() {
            return EstadoConversa.MENU;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            Optional<String> escolha = e.escolha(OPCOES);
            if (escolha.isEmpty() && e.normalizado().contains("agendar")) {
                escolha = Optional.of(MensagensBot.AGENDAR);
            }
            if (escolha.filter(MensagensBot.AGENDAR::equals).isPresent()) {
                return fluxos.iniciarAgendamento(s);
            }
            if (escolha.filter(MensagensBot.RECEPCAO::equals).isPresent()) {
                return Transicao.transbordar(ctx, Motivo.PEDIU_ATENDENTE.name());
            }
            if (e.cumprimento()) {
                return fluxos.menu(ctx, s);
            }
            return Transicao.naoEntendi(estado(), ctx, MensagensBot.menu(s.clinica().nome()));
        }
    }

    // ------------------------------------------------------------------ PARA_QUEM

    static final class ParaQuem implements Etapa {
        private final Fluxos fluxos;

        ParaQuem(Fluxos fluxos) {
            this.fluxos = fluxos;
        }

        public EstadoConversa estado() {
            return EstadoConversa.PARA_QUEM;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            List<Opcao> opcoes = new ArrayList<>();
            ctx.pacientesOferecidos().forEach(p -> opcoes.add(new Opcao(p.id(), p.nome())));
            opcoes.add(new Opcao(MensagensBot.NOVO_PACIENTE, "Outra pessoa"));
            Optional<String> escolha = e.escolha(opcoes);
            if (escolha.filter(MensagensBot.NOVO_PACIENTE::equals).isPresent()) {
                return Transicao.para(EstadoConversa.NOVO_NOME, ctx.comPacientesOferecidos(List.of()),
                        MensagensBot.perguntarNome());
            }
            Optional<PacienteDoContato> paciente = escolha.flatMap(id ->
                    ctx.pacientesOferecidos().stream().filter(p -> p.id().equals(id)).findFirst());
            if (paciente.isPresent()) {
                PacienteDoContato p = paciente.get();
                return fluxos.ofertarHorarios(ctx.comPaciente(p.pacienteId(), p.nome(), p.demanda()), s, s.hoje());
            }
            return Transicao.naoEntendi(estado(), ctx, MensagensBot.paraQuem(ctx.pacientesOferecidos()));
        }
    }

    // ------------------------------------------------------------------ NOVO_NOME

    static final class NovoNome implements Etapa {
        private static final Pattern NOME = Pattern.compile("^[\\p{L}][\\p{L} .'-]{1,119}$");

        public EstadoConversa estado() {
            return EstadoConversa.NOVO_NOME;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            String nome = e.texto() == null ? "" : e.texto().trim().replaceAll("\\s+", " ");
            if (e.tipo() != Entrada.Tipo.TEXTO || !NOME.matcher(nome).matches()) {
                return Transicao.naoEntendi(estado(), ctx, MensagensBot.nomeInvalido());
            }
            return Transicao.para(EstadoConversa.NOVO_NASCIMENTO, ctx.comNovoNome(nome),
                    MensagensBot.perguntarNascimento(nome));
        }
    }

    // ------------------------------------------------------------------ NOVO_NASCIMENTO

    static final class NovoNascimento implements Etapa {
        private static final Pattern DATA = Pattern.compile("^(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{2}|\\d{4})$");

        public EstadoConversa estado() {
            return EstadoConversa.NOVO_NASCIMENTO;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            Optional<LocalDate> data = interpretar(e.texto(), s.hoje());
            if (data.isEmpty()) {
                return Transicao.naoEntendi(estado(), ctx, MensagensBot.nascimentoInvalido());
            }
            return Transicao.para(EstadoConversa.NOVA_DEMANDA, ctx.comNovoNascimento(data.get()),
                    MensagensBot.perguntarDemanda(ctx.novoNome()));
        }

        /** "10/03/2019", "10-3-19", "10.03.2019". Ano com 2 dígitos vira o mais recente que não está no futuro. */
        static Optional<LocalDate> interpretar(String texto, LocalDate hoje) {
            if (texto == null) {
                return Optional.empty();
            }
            Matcher m = DATA.matcher(texto.trim());
            if (!m.matches()) {
                return Optional.empty();
            }
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            int ano = Integer.parseInt(m.group(3));
            if (m.group(3).length() == 2) {
                ano += 2000;
                if (ano > hoje.getYear()) {
                    ano -= 100;
                }
            }
            try {
                LocalDate d = LocalDate.of(ano, mes, dia);
                if (d.isAfter(hoje) || d.isBefore(hoje.minusYears(120))) {
                    return Optional.empty();
                }
                return Optional.of(d);
            } catch (java.time.DateTimeException ex) {
                return Optional.empty();
            }
        }
    }

    // ------------------------------------------------------------------ NOVA_DEMANDA

    static final class NovaDemanda implements Etapa {
        private final Fluxos fluxos;

        NovaDemanda(Fluxos fluxos) {
            this.fluxos = fluxos;
        }

        public EstadoConversa estado() {
            return EstadoConversa.NOVA_DEMANDA;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            List<Opcao> opcoes = MensagensBot.DEMANDAS.entrySet().stream()
                    .map(d -> new Opcao("demanda:" + d.getKey(), d.getValue())).toList();
            Optional<String> escolha = e.escolha(opcoes);
            if (escolha.isEmpty()) {
                return Transicao.naoEntendi(estado(), ctx, MensagensBot.perguntarDemanda(ctx.novoNome()));
            }
            String demanda = escolha.get().substring("demanda:".length());
            var pacienteId = fluxos.servicos().cadastrarPaciente(s.clinicaId(), s.responsavelId(), ctx.novoNome(),
                    ctx.novoNascimento(), demanda);
            ContextoConversa novo = ctx.comPaciente(pacienteId, ctx.novoNome(), demanda);
            return fluxos.ofertarHorarios(novo, s, s.hoje());
        }
    }

    // ------------------------------------------------------------------ ESCOLHER_HORARIO

    static final class EscolherHorario implements Etapa {
        private final Fluxos fluxos;

        EscolherHorario(Fluxos fluxos) {
            this.fluxos = fluxos;
        }

        public EstadoConversa estado() {
            return EstadoConversa.ESCOLHER_HORARIO;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            List<Opcao> opcoes = new ArrayList<>();
            ctx.horariosOferecidos().forEach(h -> opcoes.add(new Opcao(h.id(), h.id())));
            if (ctx.buscarAPartirDe() != null) {
                opcoes.add(new Opcao(MensagensBot.VER_MAIS, "Ver mais datas"));
            }
            opcoes.add(new Opcao(MensagensBot.RECEPCAO, "Falar com a recepção"));
            Optional<String> escolha = e.escolha(opcoes);
            if (escolha.isEmpty()) {
                return Transicao.naoEntendi(estado(), ctx, fluxos.listaDeHorarios(ctx, s));
            }
            String id = escolha.get();
            if (MensagensBot.RECEPCAO.equals(id)) {
                return Transicao.transbordar(ctx, Motivo.PEDIU_ATENDENTE.name());
            }
            if (MensagensBot.VER_MAIS.equals(id)) {
                return fluxos.ofertarHorarios(ctx, s, ctx.buscarAPartirDe());
            }
            HorarioOferta horario = ctx.horariosOferecidos().stream().filter(h -> h.id().equals(id)).findFirst()
                    .orElseThrow();
            try {
                Reserva r = fluxos.servicos().reservar(s.clinicaId(), ctx.pacienteId(), horario);
                long minutos = Math.max(1, Duration.between(s.agora(), r.expiraEm()).toMinutes());
                return Transicao.para(EstadoConversa.CONFIRMAR, ctx.comReserva(r.sessaoId(), horario),
                        MensagensBot.confirmar(ctx.pacienteNome(), horario, s.clinica().fuso(), (int) minutos));
            } catch (HorarioOcupadoException ocupado) {
                return fluxos.ofertarHorarios(ctx, s, Fluxos.primeiroDia(ctx, s), MensagensBot.horarioOcupado());
            }
        }
    }

    // ------------------------------------------------------------------ CONFIRMAR

    static final class Confirmar implements Etapa {
        private static final List<Opcao> OPCOES = List.of(new Opcao(MensagensBot.CONFIRMAR, "Confirmar"),
                new Opcao(MensagensBot.TROCAR, "Trocar horário"));
        private final Fluxos fluxos;

        Confirmar(Fluxos fluxos) {
            this.fluxos = fluxos;
        }

        public EstadoConversa estado() {
            return EstadoConversa.CONFIRMAR;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            Optional<String> escolha = e.escolha(OPCOES);
            if (escolha.isEmpty() && Set.of("sim", "confirmo", "confirma", "pode").contains(e.normalizado())) {
                escolha = Optional.of(MensagensBot.CONFIRMAR);
            }
            if (escolha.filter(MensagensBot.CONFIRMAR::equals).isPresent()) {
                try {
                    fluxos.servicos().confirmar(s.clinicaId(), ctx.reservaId());
                    return Transicao.para(EstadoConversa.MENU, ContextoConversa.vazio(),
                            MensagensBot.agendado(ctx.pacienteNome(), ctx.horarioReservado(), s.clinica().fuso()));
                } catch (ReservaVencidaException vencida) {
                    return fluxos.ofertarHorarios(ctx.semReserva(), s, Fluxos.primeiroDia(ctx, s),
                            MensagensBot.reservaVencida());
                }
            }
            if (escolha.filter(MensagensBot.TROCAR::equals).isPresent()) {
                if (ctx.reservaId() != null) {
                    fluxos.servicos().cancelarReserva(s.clinicaId(), ctx.reservaId());
                }
                return fluxos.ofertarHorarios(ctx.semReserva(), s, Fluxos.primeiroDia(ctx, s));
            }
            return Transicao.naoEntendi(estado(), ctx, MensagensBot.confirmar(ctx.pacienteNome(),
                    ctx.horarioReservado(), s.clinica().fuso(), 5));
        }
    }

    // ------------------------------------------------------------------ HUMANO

    /** Em atendimento humano o bot não responde (o serviço nem chega a chamar esta etapa). */
    static final class Humano implements Etapa {
        public EstadoConversa estado() {
            return EstadoConversa.HUMANO;
        }

        public Transicao tratar(Entrada e, ContextoConversa ctx, Situacao s) {
            return Transicao.para(EstadoConversa.HUMANO, ctx);
        }
    }
}
