package br.com.agendafono.mensageria.fila;

import java.util.UUID;

public record EventoEntrada(
        UUID id,
        UUID clinicaId,
        String wamid,
        String phoneNumberId,
        String telefone,
        String tipo,
        String texto) {
}
