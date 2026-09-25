package br.com.agendafono.agenda.application.port;

import br.com.agendafono.agenda.Periodo;
import br.com.agendafono.agenda.domain.BlocoGrade;
import br.com.agendafono.agenda.domain.PoliticaAgendamento;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saída para os dados de configuração que a agenda consulta: grade, bloqueios, fuso e política.
 * Esses dados pertencem ao módulo {@code clinica}; enquanto ele não tem API própria (Passo 2),
 * o adaptador lê as tabelas diretamente.
 */
public interface ConfiguracaoAgendaPort {

    Optional<ConfiguracaoProfissional> carregar(UUID clinicaId, UUID profissionalId);

    /** Bloqueios do profissional e da clínica inteira que tocam o intervalo. */
    List<Periodo> bloqueios(UUID clinicaId, UUID profissionalId, Periodo intervalo);

    record ConfiguracaoProfissional(
            UUID profissionalId,
            ZoneId zona,
            Duration duracaoPadrao,
            List<BlocoGrade> grade,
            PoliticaAgendamento politica) {

        public ConfiguracaoProfissional {
            grade = List.copyOf(grade);
        }
    }
}
