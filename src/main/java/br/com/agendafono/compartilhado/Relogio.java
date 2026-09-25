package br.com.agendafono.compartilhado;

import java.time.Instant;

/**
 * Fonte única de "agora" da aplicação. Nunca use {@code Instant.now()} em regra de negócio:
 * injete o relógio para que os testes controlem o tempo.
 */
@FunctionalInterface
public interface Relogio {

    Instant agora();
}
