package br.com.agendafono.pacientes;

import java.util.UUID;

/** Eventos de domínio publicados pelo módulo pacientes. Sem nomes: só IDs e o mínimo necessário. */
public final class Eventos {

    private Eventos() {
    }

    public record ConsentimentoRegistrado(UUID clinicaId, UUID responsavelId, String versaoTexto, Canal canal) {
    }

    public record ConsentimentoRevogado(UUID clinicaId, UUID responsavelId, Canal canal) {
    }

    public record PacienteCadastrado(UUID clinicaId, UUID pacienteId, UUID responsavelId) {
    }

    /**
     * Os dados do responsável e dos seus pacientes foram anonimizados. A mensageria deve apagar o histórico
     * de conversa desse telefone ({@code telefoneAnterior}, só dígitos, formato do WhatsApp).
     */
    public record TitularAnonimizado(UUID clinicaId, UUID responsavelId, String telefoneAnterior) {
    }
}
