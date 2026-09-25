package br.com.agendafono.compartilhado;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class RelogioSistema implements Relogio {

    @Override
    public Instant agora() {
        return Instant.now();
    }
}
