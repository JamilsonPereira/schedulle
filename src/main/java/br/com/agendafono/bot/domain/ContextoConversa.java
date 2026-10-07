package br.com.agendafono.bot.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dados da conversa em andamento, guardados em {@code conversa.contexto} (jsonb). Imutável: cada passo gera uma
 * cópia. Sem histórico de mensagens e sem texto livre do contato além do nome do paciente.
 *
 * @param buscarAPartirDe primeiro dia da próxima busca de horários ("Ver mais datas")
 */
public record ContextoConversa(
        int schemaVersao,
        UUID pacienteId,
        String pacienteNome,
        String demanda,
        String novoNome,
        LocalDate novoNascimento,
        List<PacienteDoContato> pacientesOferecidos,
        List<HorarioOferta> horariosOferecidos,
        LocalDate buscarAPartirDe,
        UUID reservaId,
        HorarioOferta horarioReservado) {

    public static final int VERSAO = 1;

    public static ContextoConversa vazio() {
        return new ContextoConversa(VERSAO, null, null, null, null, null, List.of(), List.of(), null, null, null);
    }

    public ContextoConversa {
        pacientesOferecidos = pacientesOferecidos == null ? List.of() : List.copyOf(pacientesOferecidos);
        horariosOferecidos = horariosOferecidos == null ? List.of() : List.copyOf(horariosOferecidos);
    }

    public ContextoConversa comPacientesOferecidos(List<PacienteDoContato> lista) {
        return new ContextoConversa(schemaVersao, pacienteId, pacienteNome, demanda, novoNome, novoNascimento, lista,
                horariosOferecidos, buscarAPartirDe, reservaId, horarioReservado);
    }

    public ContextoConversa comPaciente(UUID id, String nome, String demandaDoPaciente) {
        return new ContextoConversa(schemaVersao, id, nome, demandaDoPaciente, null, null, List.of(),
                horariosOferecidos, buscarAPartirDe, reservaId, horarioReservado);
    }

    public ContextoConversa comNovoNome(String nome) {
        return new ContextoConversa(schemaVersao, null, null, null, nome, null, List.of(), horariosOferecidos,
                buscarAPartirDe, reservaId, horarioReservado);
    }

    public ContextoConversa comNovoNascimento(LocalDate data) {
        return new ContextoConversa(schemaVersao, pacienteId, pacienteNome, demanda, novoNome, data,
                pacientesOferecidos, horariosOferecidos, buscarAPartirDe, reservaId, horarioReservado);
    }

    public ContextoConversa comHorarios(List<HorarioOferta> horarios, LocalDate proximaBusca) {
        return new ContextoConversa(schemaVersao, pacienteId, pacienteNome, demanda, novoNome, novoNascimento,
                pacientesOferecidos, horarios, proximaBusca, null, null);
    }

    public ContextoConversa comReserva(UUID id, HorarioOferta horario) {
        return new ContextoConversa(schemaVersao, pacienteId, pacienteNome, demanda, novoNome, novoNascimento,
                pacientesOferecidos, horariosOferecidos, buscarAPartirDe, id, horario);
    }

    public ContextoConversa semReserva() {
        return comReserva(null, null);
    }
}
