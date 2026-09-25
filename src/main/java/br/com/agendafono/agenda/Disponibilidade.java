package br.com.agendafono.agenda;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Porta de entrada pública: horários livres de um profissional (RN-01, RN-02). */
public interface Disponibilidade {

    List<HorarioLivre> consultar(Consulta consulta);

    /**
     * Quem pergunta muda as regras: o {@link Origem#BOT} respeita antecedência mínima e janela máxima da
     * clínica; o {@link Origem#PAINEL} só exclui horários que já passaram (a recepção pode encaixar).
     */
    enum Origem { BOT, PAINEL }

    /**
     * @param de          primeiro dia, no fuso da clínica
     * @param ate         último dia (inclusive), no fuso da clínica; no máximo 31 dias após {@code de}
     * @param duracaoMin  duração desejada; nula = duração padrão do profissional
     */
    record Consulta(UUID clinicaId, UUID profissionalId, LocalDate de, LocalDate ate, Integer duracaoMin,
                    Origem origem) {

        public static final int MAX_DIAS = 31;

        public Consulta {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(profissionalId, "profissionalId");
            Objects.requireNonNull(de, "de");
            Objects.requireNonNull(ate, "ate");
            Objects.requireNonNull(origem, "origem");
            if (ate.isBefore(de)) {
                throw new IllegalArgumentException("'ate' deve ser igual ou posterior a 'de'");
            }
            if (de.plusDays(MAX_DIAS).isBefore(ate)) {
                throw new IllegalArgumentException("O intervalo consultado pode ter no máximo " + MAX_DIAS + " dias");
            }
            if (duracaoMin != null && (duracaoMin < 10 || duracaoMin > 240)) {
                throw new IllegalArgumentException("A duração deve estar entre 10 e 240 minutos");
            }
        }
    }
}
