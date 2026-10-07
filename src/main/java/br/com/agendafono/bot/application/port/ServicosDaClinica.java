package br.com.agendafono.bot.application.port;

import br.com.agendafono.bot.domain.HorarioOcupadoException;
import br.com.agendafono.bot.domain.HorarioOferta;
import br.com.agendafono.bot.domain.PacienteDoContato;
import br.com.agendafono.bot.domain.ReservaVencidaException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * O que o bot precisa da clínica, dos pacientes e da agenda, numa porta estreita. O adaptador usa só as APIs
 * públicas desses módulos; nos testes, uma implementação em memória.
 */
public interface ServicosDaClinica {

    DadosDaClinica clinica(UUID clinicaId);

    /** Encontra o responsável pelo telefone ou cria um novo, ainda sem consentimento. */
    Contato identificar(UUID clinicaId, String telefoneDigitos);

    /** @param wamid prova do aceite (id da mensagem no WhatsApp) */
    void registrarConsentimento(UUID clinicaId, UUID responsavelId, String wamid);

    /** Pacientes ativos do responsável, com ids curtos ("p1"...), no máximo 9. */
    List<PacienteDoContato> pacientes(UUID clinicaId, UUID responsavelId);

    UUID cadastrarPaciente(UUID clinicaId, UUID responsavelId, String nome, LocalDate nascimento, String demanda);

    /** Até {@code quantidade} horários de avaliação a partir do dia, entre os fonos que atendem a demanda. */
    BuscaDeHorarios horarios(UUID clinicaId, String demanda, LocalDate aPartirDe, int quantidade);

    /** Pré-reserva (vence em alguns minutos). @throws HorarioOcupadoException */
    Reserva reservar(UUID clinicaId, UUID pacienteId, HorarioOferta horario);

    /** @throws ReservaVencidaException */
    void confirmar(UUID clinicaId, UUID reservaId);

    void cancelarReserva(UUID clinicaId, UUID reservaId);

    /** Nome e telefone mascarado do responsável, para a fila da recepção. */
    DadosDoResponsavel responsavel(UUID clinicaId, UUID responsavelId);

    record DadosDaClinica(String nome, ZoneId fuso) {
    }

    record Contato(UUID responsavelId, boolean consentido) {
    }

    /** @param proximaBusca primeiro dia para "Ver mais datas"; nulo quando passou da janela de agendamento */
    record BuscaDeHorarios(List<HorarioOferta> horarios, LocalDate proximaBusca) {
    }

    record Reserva(UUID sessaoId, Instant expiraEm) {
    }

    record DadosDoResponsavel(String nome, String telefoneMascarado) {
    }
}
