package br.com.agendafono.pacientes;

import br.com.agendafono.pacientes.Views.AnexoView;
import br.com.agendafono.pacientes.Views.ConteudoAnexo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Porta de entrada pública para anexos (pedido médico, documentos): JPEG, PNG ou PDF de até 10 MB. */
public interface Anexos {

    long TAMANHO_MAXIMO_BYTES = 10L * 1024 * 1024;

    AnexoView anexar(NovoAnexo comando);

    List<AnexoView> listar(UUID clinicaId, UUID pacienteId);

    ConteudoAnexo abrir(UUID clinicaId, UUID anexoId);

    void remover(UUID clinicaId, UUID anexoId);

    record NovoAnexo(UUID clinicaId, UUID pacienteId, TipoAnexo tipo, String nomeArquivo, Canal origem,
                     byte[] conteudo) {
        public NovoAnexo {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(pacienteId, "pacienteId");
            Objects.requireNonNull(tipo, "tipo");
            Objects.requireNonNull(origem, "origem");
            Objects.requireNonNull(conteudo, "conteudo");
        }
    }
}
