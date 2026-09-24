package br.com.agendafono.mensageria.fila;

import java.util.UUID;

public record EventoSaida(
        UUID id,
        UUID clinicaId,
        String phoneNumberId,
        String telefone,
        String texto) {
}
