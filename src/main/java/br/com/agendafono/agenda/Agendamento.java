package br.com.agendafono.agenda;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de entrada pública para criar e alterar sessões. Usada pelo bot (reserva + confirmação)
 * e pelo painel (agendamento direto, remarcação, cancelamento).
 *
 * <p>Parâmetros {@code versaoEsperada} são opcionais: quando informados, a operação falha com
 * {@link VersaoDesatualizadaException} se a sessão mudou desde a leitura.
 */
public interface Agendamento {

    /** Bot: segura o horário por alguns minutos (RESERVADA) até o responsável confirmar (RN-04). */
    SessaoView reservar(Reservar comando);

    /** Bot: transforma a reserva em AGENDADA, se ainda não expirou. */
    SessaoView confirmarReserva(UUID clinicaId, UUID sessaoId);

    /** Painel: cria a sessão já AGENDADA. */
    SessaoView agendar(Agendar comando);

    SessaoView remarcar(Remarcar comando);

    /** Resposta "Confirmo" ao lembrete (AGENDADA → CONFIRMADA). Idempotente. */
    SessaoView confirmarPresenca(UUID clinicaId, UUID sessaoId, Integer versaoEsperada);

    SessaoView cancelar(UUID clinicaId, UUID sessaoId, Integer versaoEsperada);

    /** "Vou faltar" (RN-20): define direito a reposição conforme a antecedência do aviso. */
    SessaoView avisarFalta(UUID clinicaId, UUID sessaoId, Integer versaoEsperada);

    Optional<SessaoView> buscar(UUID clinicaId, UUID sessaoId);

    /** Sessões que começam em {@code [de, ate)}; {@code profissionalId} nulo = todos. */
    List<SessaoView> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate);

    record Reservar(UUID clinicaId, UUID pacienteId, UUID profissionalId, TipoSessao tipo, Instant inicio,
                    Integer duracaoMin) {
        public Reservar {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(pacienteId, "pacienteId");
            Objects.requireNonNull(profissionalId, "profissionalId");
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(inicio, "inicio");
        }
    }

    /**
     * @param recursoId           sala/cabine; nula = a da grade naquele horário
     * @param permitirForaDaGrade encaixe feito pela recepção fora da grade do profissional
     */
    record Agendar(UUID clinicaId, UUID pacienteId, UUID profissionalId, TipoSessao tipo, Instant inicio,
                   Integer duracaoMin, UUID recursoId, boolean permitirForaDaGrade) {
        public Agendar {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(pacienteId, "pacienteId");
            Objects.requireNonNull(profissionalId, "profissionalId");
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(inicio, "inicio");
        }
    }

    /** Mantém a duração atual da sessão; muda só o início (e a sala, se informada). */
    record Remarcar(UUID clinicaId, UUID sessaoId, Instant novoInicio, UUID recursoId, boolean permitirForaDaGrade,
                    Integer versaoEsperada) {
        public Remarcar {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(sessaoId, "sessaoId");
            Objects.requireNonNull(novoInicio, "novoInicio");
        }
    }
}
