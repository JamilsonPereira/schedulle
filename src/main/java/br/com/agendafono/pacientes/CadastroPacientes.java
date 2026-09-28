package br.com.agendafono.pacientes;

import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.ResponsavelView;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Porta de entrada pública para cadastro de responsáveis, pacientes e consentimento.
 *
 * <p>Regra central (LGPD arts. 11 e 14): nenhum paciente é cadastrado sem consentimento registrado do
 * responsável. Parâmetros {@code versaoEsperada} são opcionais; quando informados, a operação falha com
 * {@link RegistroDesatualizadoException} se o registro mudou desde a leitura.
 */
public interface CadastroPacientes {

    /** Bot, primeiro contato: encontra o responsável pelo telefone ou cria um novo (ainda sem consentimento). */
    ResponsavelView identificarResponsavel(UUID clinicaId, Telefone telefone);

    /** @param evidencia prova do aceite: {@code wamid} da resposta no WhatsApp ou identificação no painel */
    ResponsavelView registrarConsentimento(UUID clinicaId, UUID responsavelId, String versaoTexto, Canal canal,
                                           String evidencia);

    ResponsavelView revogarConsentimento(UUID clinicaId, UUID responsavelId, Canal canal, String evidencia);

    ResponsavelView atualizarResponsavel(AtualizarResponsavel comando);

    PacienteView cadastrarPaciente(CadastrarPaciente comando);

    /**
     * Painel: cadastra o paciente e, se necessário, o responsável (pelo telefone) e o consentimento
     * coletado presencialmente, tudo numa transação.
     */
    PacienteView cadastrarPeloPainel(CadastroPeloPainel comando);

    PacienteView atualizarPaciente(AtualizarPaciente comando);

    PacienteView inativarPaciente(UUID clinicaId, UUID pacienteId, Integer versaoEsperada);

    PacienteView reativarPaciente(UUID clinicaId, UUID pacienteId, Integer versaoEsperada);

    record AtualizarResponsavel(UUID clinicaId, UUID responsavelId, String nome, Telefone telefone,
                                Integer versaoEsperada) {
        public AtualizarResponsavel {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(responsavelId, "responsavelId");
            Objects.requireNonNull(telefone, "telefone");
        }
    }

    record CadastrarPaciente(UUID clinicaId, UUID responsavelId, String nome, LocalDate dataNascimento,
                             Demanda demanda) {
        public CadastrarPaciente {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(responsavelId, "responsavelId");
        }
    }

    /**
     * @param consentimentoColetado a recepção confirma que o responsável assinou/aceitou o termo presencialmente
     * @param registradoPor         quem registrou no painel (evidência do consentimento)
     */
    record CadastroPeloPainel(UUID clinicaId, Telefone telefoneResponsavel, String nomeResponsavel,
                              String nomePaciente, LocalDate dataNascimento, Demanda demanda,
                              boolean consentimentoColetado, String registradoPor) {
        public CadastroPeloPainel {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(telefoneResponsavel, "telefoneResponsavel");
        }
    }

    record AtualizarPaciente(UUID clinicaId, UUID pacienteId, String nome, LocalDate dataNascimento,
                             Demanda demanda, Integer versaoEsperada) {
        public AtualizarPaciente {
            Objects.requireNonNull(clinicaId, "clinicaId");
            Objects.requireNonNull(pacienteId, "pacienteId");
        }
    }
}
