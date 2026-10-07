package br.com.agendafono.bot.domain;

import java.util.UUID;

/** Paciente já cadastrado do responsável, oferecido em "Para quem é a consulta?". */
public record PacienteDoContato(String id, UUID pacienteId, String nome, String demanda) {
}
