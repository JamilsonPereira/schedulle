package br.com.agendafono.pacientes;

import br.com.agendafono.compartilhado.Pagina;
import br.com.agendafono.pacientes.Views.FichaPaciente;
import br.com.agendafono.pacientes.Views.PacienteResumo;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Porta de entrada pública de leitura. Tudo é filtrado pela clínica. */
public interface PacienteConsulta {

    Optional<PacienteView> paciente(UUID clinicaId, UUID pacienteId);

    List<PacienteView> pacientesDoResponsavel(UUID clinicaId, UUID responsavelId);

    Optional<ResponsavelView> responsavel(UUID clinicaId, UUID responsavelId);

    /** Busca pelo telefone, tolerando o nono dígito ausente dos celulares brasileiros. */
    Optional<ResponsavelView> responsavelPorTelefone(UUID clinicaId, Telefone telefone);

    FichaPaciente ficha(UUID clinicaId, UUID pacienteId);

    Pagina<PacienteResumo> pesquisar(UUID clinicaId, Pesquisa pesquisa);

    /** Nomes dos pacientes, para telas como a agenda. Ids de outra clínica são ignorados. */
    Map<UUID, String> nomes(UUID clinicaId, Collection<UUID> pacienteIds);

    /**
     * @param termo   parte do nome do paciente ou do responsável, ou dígitos do telefone; nulo = todos
     * @param ativo   nulo = ativos e inativos
     * @param pagina  a partir de 0
     * @param tamanho de 1 a 100
     */
    record Pesquisa(String termo, Boolean ativo, int pagina, int tamanho) {

        public Pesquisa {
            if (pagina < 0) {
                throw new DadosInvalidosException("A página deve ser 0 ou maior");
            }
            if (tamanho < 1 || tamanho > 100) {
                throw new DadosInvalidosException("O tamanho da página deve estar entre 1 e 100");
            }
            termo = termo == null || termo.isBlank() ? null : termo.trim();
        }
    }
}
