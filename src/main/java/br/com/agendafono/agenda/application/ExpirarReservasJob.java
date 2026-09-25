package br.com.agendafono.agenda.application;

import br.com.agendafono.agenda.application.port.SessaoRepository;
import br.com.agendafono.compartilhado.Relogio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-04: reservas do bot não confirmadas a tempo viram CANCELADA e liberam o horário.
 * Idempotente. Com mais de uma instância, entra ShedLock (SDD, seção 9).
 */
@Component
class ExpirarReservasJob {

    private static final Logger log = LoggerFactory.getLogger(ExpirarReservasJob.class);

    private final SessaoRepository sessoes;
    private final Relogio relogio;

    ExpirarReservasJob(SessaoRepository sessoes, Relogio relogio) {
        this.sessoes = sessoes;
        this.relogio = relogio;
    }

    @Scheduled(fixedDelayString = "${agenda.jobs.expirar-reservas:PT1M}")
    @Transactional
    public void executar() {
        int canceladas = sessoes.cancelarTodasReservasExpiradas(relogio.agora());
        if (canceladas > 0) {
            log.info("Reservas expiradas canceladas: {}", canceladas);
        }
    }
}
