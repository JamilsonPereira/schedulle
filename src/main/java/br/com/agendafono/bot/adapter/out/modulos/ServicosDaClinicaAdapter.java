package br.com.agendafono.bot.adapter.out.modulos;

import br.com.agendafono.agenda.AgendaException;
import br.com.agendafono.agenda.Agendamento;
import br.com.agendafono.agenda.Disponibilidade;
import br.com.agendafono.agenda.HorarioForaDaAgendaException;
import br.com.agendafono.agenda.HorarioIndisponivelException;
import br.com.agendafono.agenda.HorarioLivre;
import br.com.agendafono.agenda.ReservaExpiradaException;
import br.com.agendafono.agenda.SessaoView;
import br.com.agendafono.agenda.TipoSessao;
import br.com.agendafono.agenda.TransicaoInvalidaException;
import br.com.agendafono.bot.application.port.ServicosDaClinica;
import br.com.agendafono.bot.domain.HorarioOcupadoException;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.PacienteDoContato;
import br.com.agendafono.bot.domain.ReservaVencidaException;
import br.com.agendafono.clinica.ClinicaConsulta;
import br.com.agendafono.clinica.ProfissionalConsulta;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.ProfissionalView;
import br.com.agendafono.pacientes.CadastroPacientes;
import br.com.agendafono.pacientes.CadastroPacientes.CadastrarPaciente;
import br.com.agendafono.pacientes.Canal;
import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.PacienteConsulta;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Liga o bot às APIs públicas de clínica, pacientes e agenda.
 *
 * <p>Toda escrita roda com {@code PROPAGATION_NOT_SUPPORTED}: a transação do bot fica suspensa e o outro módulo
 * abre e fecha a sua. Assim uma recusa da agenda (horário tomado, reserva vencida) não marca a transação do bot
 * para rollback, e o bot ainda consegue gravar a conversa e responder ao contato.
 */
@Component
class ServicosDaClinicaAdapter implements ServicosDaClinica {

    private static final Logger log = LoggerFactory.getLogger(ServicosDaClinicaAdapter.class);

    /** No máximo dois horários por dia, para a lista mostrar mais de um dia. */
    static final int POR_DIA = 2;
    static final int JANELA_PADRAO_DIAS = 30;
    static final int MAX_PACIENTES = 9;

    private final ClinicaConsulta clinicas;
    private final ProfissionalConsulta profissionais;
    private final CadastroPacientes cadastro;
    private final PacienteConsulta pacientes;
    private final Agendamento agendamento;
    private final Disponibilidade disponibilidade;
    private final TransactionTemplate isolada;
    private final String versaoConsentimento;
    private final Clock relogio;

    ServicosDaClinicaAdapter(ClinicaConsulta clinicas, ProfissionalConsulta profissionais,
                             CadastroPacientes cadastro, PacienteConsulta pacientes, Agendamento agendamento,
                             Disponibilidade disponibilidade, PlatformTransactionManager transacoes,
                             @Value("${pacientes.versao-consentimento-atual}") String versaoConsentimento) {
        this(clinicas, profissionais, cadastro, pacientes, agendamento, disponibilidade, transacoes,
                versaoConsentimento, Clock.systemUTC());
    }

    ServicosDaClinicaAdapter(ClinicaConsulta clinicas, ProfissionalConsulta profissionais,
                             CadastroPacientes cadastro, PacienteConsulta pacientes, Agendamento agendamento,
                             Disponibilidade disponibilidade, PlatformTransactionManager transacoes,
                             String versaoConsentimento, Clock relogio) {
        this.clinicas = clinicas;
        this.profissionais = profissionais;
        this.cadastro = cadastro;
        this.pacientes = pacientes;
        this.agendamento = agendamento;
        this.disponibilidade = disponibilidade;
        this.isolada = new TransactionTemplate(transacoes);
        this.isolada.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        this.versaoConsentimento = versaoConsentimento;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ clínica e contato

    @Override
    public DadosDaClinica clinica(UUID clinicaId) {
        ClinicaView c = buscarClinica(clinicaId);
        return new DadosDaClinica(c.nome(), ZoneId.of(c.fuso()));
    }

    @Override
    public Contato identificar(UUID clinicaId, String telefoneDigitos) {
        ResponsavelView r = fora(() -> cadastro.identificarResponsavel(clinicaId, Telefone.doWhatsApp(telefoneDigitos)));
        return new Contato(r.id(), r.possuiConsentimento());
    }

    @Override
    public void registrarConsentimento(UUID clinicaId, UUID responsavelId, String wamid) {
        fora(() -> cadastro.registrarConsentimento(clinicaId, responsavelId, versaoConsentimento, Canal.WHATSAPP,
                "wamid:" + wamid));
    }

    @Override
    public DadosDoResponsavel responsavel(UUID clinicaId, UUID responsavelId) {
        return pacientes.responsavel(clinicaId, responsavelId)
                .map(r -> new DadosDoResponsavel(r.nome(), r.telefoneMascarado()))
                .orElse(new DadosDoResponsavel(null, null));
    }

    // ------------------------------------------------------------------ pacientes

    @Override
    public List<PacienteDoContato> pacientes(UUID clinicaId, UUID responsavelId) {
        List<PacienteView> ativos = pacientes.pacientesDoResponsavel(clinicaId, responsavelId).stream()
                .filter(p -> p.ativo() && !p.anonimizado())
                .limit(MAX_PACIENTES)
                .toList();
        List<PacienteDoContato> lista = new ArrayList<>();
        for (int i = 0; i < ativos.size(); i++) {
            PacienteView p = ativos.get(i);
            lista.add(new PacienteDoContato("p" + (i + 1), p.id(), p.nome(),
                    p.demanda() == null ? null : p.demanda().name()));
        }
        return lista;
    }

    @Override
    public UUID cadastrarPaciente(UUID clinicaId, UUID responsavelId, String nome, LocalDate nascimento,
                                  String demanda) {
        Demanda d = demanda == null ? null : Demanda.valueOf(demanda);
        return fora(() -> cadastro.cadastrarPaciente(new CadastrarPaciente(clinicaId, responsavelId, nome, nascimento,
                d))).id();
    }

    // ------------------------------------------------------------------ horários

    /**
     * Busca entre os fonos ativos que atendem a demanda (sem nenhum, entre todos), em blocos de até 30 dias,
     * até juntar a quantidade pedida ou chegar ao fim da janela de agendamento da clínica.
     */
    @Override
    public BuscaDeHorarios horarios(UUID clinicaId, String demanda, LocalDate aPartirDe, int quantidade) {
        ClinicaView clinica = buscarClinica(clinicaId);
        ZoneId fuso = ZoneId.of(clinica.fuso());
        Integer janela = clinica.politica() == null ? null : clinica.politica().janelaMaximaDias();
        LocalDate limite = LocalDate.now(relogio.withZone(fuso)).plusDays(janela == null ? JANELA_PADRAO_DIAS : janela);

        List<ProfissionalView> fonos = fonos(clinicaId, demanda);
        if (fonos.isEmpty()) {
            return new BuscaDeHorarios(List.of(), null);
        }

        List<HorarioOferta> escolhidos = new ArrayList<>();
        Map<LocalDate, Integer> porDia = new HashMap<>();
        LocalDate de = aPartirDe;
        while (!de.isAfter(limite) && escolhidos.size() < quantidade) {
            LocalDate ate = de.plusDays(Disponibilidade.Consulta.MAX_DIAS - 1);
            if (ate.isAfter(limite)) {
                ate = limite;
            }
            List<Candidato> candidatos = new ArrayList<>();
            for (ProfissionalView p : fonos) {
                for (HorarioLivre h : disponibilidade.consultar(new Disponibilidade.Consulta(clinicaId, p.id(), de,
                        ate, null, Disponibilidade.Origem.BOT))) {
                    candidatos.add(new Candidato(p, h));
                }
            }
            candidatos.sort(Comparator.comparing((Candidato c) -> c.horario().periodo().inicio())
                    .thenComparing(c -> c.profissional().nome()));
            for (Candidato c : candidatos) {
                if (escolhidos.size() == quantidade) {
                    break;
                }
                LocalDate dia = c.horario().periodo().inicio().atZone(fuso).toLocalDate();
                if (porDia.merge(dia, 1, Integer::sum) > POR_DIA) {
                    continue;
                }
                escolhidos.add(new HorarioOferta("h" + (escolhidos.size() + 1), c.profissional().id(),
                        c.profissional().nome(), c.horario().periodo().inicio(), c.horario().periodo().fim()));
            }
            de = ate.plusDays(1);
        }

        LocalDate proxima = null;
        if (escolhidos.size() == quantidade) {
            LocalDate ultimo = escolhidos.getLast().inicio().atZone(fuso).toLocalDate();
            proxima = ultimo.plusDays(1).isAfter(limite) ? null : ultimo.plusDays(1);
        }
        return new BuscaDeHorarios(escolhidos, proxima);
    }

    private List<ProfissionalView> fonos(UUID clinicaId, String demanda) {
        Subarea subarea = subarea(demanda);
        List<ProfissionalView> daSubarea = subarea == null ? List.of() : profissionais.ativos(clinicaId, subarea);
        return daSubarea.isEmpty() ? profissionais.ativos(clinicaId, null) : daSubarea;
    }

    private static Subarea subarea(String demanda) {
        if (demanda == null) {
            return null;
        }
        try {
            return Subarea.valueOf(demanda);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private record Candidato(ProfissionalView profissional, HorarioLivre horario) {
    }

    // ------------------------------------------------------------------ reserva

    @Override
    public Reserva reservar(UUID clinicaId, UUID pacienteId, HorarioOferta horario) {
        try {
            SessaoView s = fora(() -> agendamento.reservar(new Agendamento.Reservar(clinicaId, pacienteId,
                    horario.profissionalId(), TipoSessao.AVALIACAO, horario.inicio(), null)));
            return new Reserva(s.id(), s.expiraEm());
        } catch (HorarioIndisponivelException | HorarioForaDaAgendaException e) {
            throw new HorarioOcupadoException();
        }
    }

    @Override
    public void confirmar(UUID clinicaId, UUID reservaId) {
        if (reservaId == null) {
            throw new ReservaVencidaException();
        }
        try {
            fora(() -> agendamento.confirmarReserva(clinicaId, reservaId));
        } catch (ReservaExpiradaException | TransicaoInvalidaException e) {
            // TransicaoInvalida: o job de expiração já cancelou a reserva
            throw new ReservaVencidaException();
        }
    }

    @Override
    public void cancelarReserva(UUID clinicaId, UUID reservaId) {
        try {
            fora(() -> agendamento.cancelar(clinicaId, reservaId, null));
        } catch (AgendaException e) {
            log.debug("Reserva {} não cancelada ({}); vence sozinha", reservaId, e.getClass().getSimpleName());
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private ClinicaView buscarClinica(UUID clinicaId) {
        return clinicas.clinica(clinicaId)
                .orElseThrow(() -> new IllegalStateException("Clínica " + clinicaId + " não encontrada"));
    }

    private <T> T fora(Supplier<T> operacao) {
        return isolada.execute(s -> operacao.get());
    }
}
