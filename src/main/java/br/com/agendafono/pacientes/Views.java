package br.com.agendafono.pacientes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Modelos de leitura expostos pelo módulo pacientes (API pública). */
public final class Views {

    private Views() {
    }

    public record ConsentimentoView(String versaoTexto, Canal canal, Instant registradoEm) {
    }

    public record RegistroConsentimentoView(String acao, String versaoTexto, Canal canal, String evidencia,
                                            Instant registradoEm) {
    }

    /**
     * @param telefone e164 completo; nulo depois de anonimizado. Exibir só em telas de detalhe.
     */
    public record ResponsavelView(UUID id, UUID clinicaId, String nome, String telefone, String telefoneMascarado,
                                  ConsentimentoView consentimento, boolean anonimizado, int versao) {

        public boolean possuiConsentimento() {
            return consentimento != null;
        }
    }

    public record PacienteView(UUID id, UUID clinicaId, UUID responsavelId, String nome, LocalDate dataNascimento,
                               Integer idade, Demanda demanda, boolean ativo, boolean anonimizado, int versao) {
    }

    /** Linha da lista de pacientes do painel: telefone sempre mascarado. */
    public record PacienteResumo(UUID id, String nome, Integer idade, Demanda demanda, boolean ativo,
                                 UUID responsavelId, String responsavelNome, String telefoneMascarado) {
    }

    public record AnexoView(UUID id, UUID pacienteId, TipoAnexo tipo, String nomeArquivo, String contentType,
                            long tamanhoBytes, Canal origem, Instant criadoEm) {
    }

    public record FichaPaciente(PacienteView paciente, ResponsavelView responsavel,
                                List<PacienteView> outrosPacientesDoResponsavel, List<AnexoView> anexos) {
    }

    /** Conteúdo entregue ao titular num pedido de acesso (LGPD art. 18, II). */
    public record ExportacaoDados(Instant geradoEm, ResponsavelView responsavel, List<PacienteView> pacientes,
                                  List<RegistroConsentimentoView> historicoConsentimento, List<AnexoView> anexos) {
    }

    public record ConteudoAnexo(AnexoView anexo, byte[] bytes) {
    }
}
