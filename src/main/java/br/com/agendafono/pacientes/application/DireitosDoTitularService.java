package br.com.agendafono.pacientes.application;

import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.pacientes.DireitosDoTitular;
import br.com.agendafono.pacientes.NaoEncontradoException;
import br.com.agendafono.pacientes.Views.ExportacaoDados;
import br.com.agendafono.pacientes.application.port.AnexoRepository;
import br.com.agendafono.pacientes.application.port.ArmazenamentoArquivos;
import br.com.agendafono.pacientes.application.port.PacienteRepository;
import br.com.agendafono.pacientes.application.port.ResponsavelRepository;
import br.com.agendafono.pacientes.domain.Anexo;
import br.com.agendafono.pacientes.domain.Paciente;
import br.com.agendafono.pacientes.domain.Responsavel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** LGPD art. 18: acesso (exportação) e eliminação (anonimização) a pedido do titular, via clínica. */
@Service
class DireitosDoTitularService implements DireitosDoTitular {

    private static final Logger log = LoggerFactory.getLogger(DireitosDoTitularService.class);

    private final ResponsavelRepository responsaveis;
    private final PacienteRepository pacientes;
    private final AnexoRepository anexos;
    private final ArmazenamentoArquivos armazenamento;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transacao;
    private final Relogio relogio;
    private final ZoneId fuso;

    DireitosDoTitularService(ResponsavelRepository responsaveis, PacienteRepository pacientes,
                             AnexoRepository anexos, ArmazenamentoArquivos armazenamento,
                             ApplicationEventPublisher eventos, TransactionTemplate transacao, Relogio relogio,
                             PacientesProperties propriedades) {
        this.responsaveis = responsaveis;
        this.pacientes = pacientes;
        this.anexos = anexos;
        this.armazenamento = armazenamento;
        this.eventos = eventos;
        this.transacao = transacao;
        this.relogio = relogio;
        this.fuso = ZoneId.of(propriedades.fuso());
    }

    @Override
    @Transactional(readOnly = true)
    public ExportacaoDados exportar(UUID clinicaId, UUID responsavelId) {
        Instant agora = relogio.agora();
        LocalDate hoje = LocalDate.ofInstant(agora, fuso);
        Responsavel r = carregar(clinicaId, responsavelId);
        return new ExportacaoDados(
                agora,
                PacientesMapper.view(r),
                pacientes.doResponsavel(clinicaId, responsavelId).stream()
                        .map(p -> PacientesMapper.view(p, hoje)).toList(),
                responsaveis.historicoConsentimento(clinicaId, responsavelId).stream()
                        .map(PacientesMapper::view).toList(),
                anexos.doResponsavel(clinicaId, responsavelId).stream().map(PacientesMapper::view).toList());
    }

    @Override
    public void anonimizar(UUID clinicaId, UUID responsavelId) {
        // Banco primeiro, numa transação; arquivos só depois do commit, para nunca apagar arquivo
        // de um registro que continuou existindo por causa de rollback.
        List<Anexo> removidos = transacao.execute(s -> {
            Instant agora = relogio.agora();
            Responsavel r = carregar(clinicaId, responsavelId);
            List<Anexo> arquivos = anexos.doResponsavel(clinicaId, responsavelId);

            r.anonimizar(agora);
            responsaveis.atualizar(r);
            for (Paciente p : pacientes.doResponsavel(clinicaId, responsavelId)) {
                p.anonimizar(agora);
                pacientes.atualizar(p);
            }
            arquivos.forEach(a -> anexos.remover(clinicaId, a.id()));
            r.extrairEventos().forEach(eventos::publishEvent);
            return arquivos;
        });

        for (Anexo a : removidos) {
            try {
                armazenamento.remover(a.chave());
            } catch (RuntimeException e) {
                log.error("Falha ao apagar arquivo do anexo {} na anonimização; remover manualmente", a.id(), e);
            }
        }
    }

    private Responsavel carregar(UUID clinicaId, UUID responsavelId) {
        return responsaveis.buscar(clinicaId, responsavelId)
                .orElseThrow(() -> new NaoEncontradoException("Responsável", responsavelId));
    }
}
