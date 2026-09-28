package br.com.agendafono.pacientes.application;

import br.com.agendafono.pacientes.Demanda;
import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.Views.AnexoView;
import br.com.agendafono.pacientes.Views.ConsentimentoView;
import br.com.agendafono.pacientes.Views.PacienteResumo;
import br.com.agendafono.pacientes.Views.PacienteView;
import br.com.agendafono.pacientes.Views.RegistroConsentimentoView;
import br.com.agendafono.pacientes.Views.ResponsavelView;
import br.com.agendafono.pacientes.application.port.PacienteRepository.LinhaPesquisa;
import br.com.agendafono.pacientes.domain.Anexo;
import br.com.agendafono.pacientes.domain.Paciente;
import br.com.agendafono.pacientes.domain.RegistroConsentimento;
import br.com.agendafono.pacientes.domain.Responsavel;

import java.time.LocalDate;
import java.time.Period;

final class PacientesMapper {

    private PacientesMapper() {
    }

    static ResponsavelView view(Responsavel r) {
        ConsentimentoView consentimento = r.consentimento() == null ? null
                : new ConsentimentoView(r.consentimento().versaoTexto(), r.consentimento().canal(),
                r.consentimento().registradoEm());
        Telefone telefone = r.telefone();
        return new ResponsavelView(r.id(), r.clinicaId(), r.nome(),
                telefone != null ? telefone.e164() : null,
                telefone != null ? telefone.mascarado() : null,
                consentimento, r.anonimizado(), r.versao());
    }

    static PacienteView view(Paciente p, LocalDate hoje) {
        return new PacienteView(p.id(), p.clinicaId(), p.responsavelId(), p.nome(), p.dataNascimento(),
                p.idade(hoje), p.demanda(), p.ativo(), p.anonimizado(), p.versao());
    }

    static PacienteResumo resumo(LinhaPesquisa l, LocalDate hoje) {
        Integer idade = l.dataNascimento() == null ? null : Period.between(l.dataNascimento(), hoje).getYears();
        String mascarado = l.telefoneE164() == null ? null : new Telefone(l.telefoneE164()).mascarado();
        return new PacienteResumo(l.id(), l.nome(), idade, l.demanda() == null ? null : Demanda.valueOf(l.demanda()),
                l.ativo(), l.responsavelId(), l.responsavelNome(), mascarado);
    }

    static AnexoView view(Anexo a) {
        return new AnexoView(a.id(), a.pacienteId(), a.tipo(), a.nomeArquivo(), a.contentType(), a.tamanhoBytes(),
                a.origem(), a.criadoEm());
    }

    static RegistroConsentimentoView view(RegistroConsentimento r) {
        return new RegistroConsentimentoView(r.acao().name(), r.versaoTexto(), r.canal(), r.evidencia(),
                r.registradoEm());
    }
}
