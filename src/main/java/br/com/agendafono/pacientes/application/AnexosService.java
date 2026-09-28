package br.com.agendafono.pacientes.application;

import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.pacientes.Anexos;
import br.com.agendafono.pacientes.NaoEncontradoException;
import br.com.agendafono.pacientes.TitularAnonimizadoException;
import br.com.agendafono.pacientes.Views.AnexoView;
import br.com.agendafono.pacientes.Views.ConteudoAnexo;
import br.com.agendafono.pacientes.application.port.AnexoRepository;
import br.com.agendafono.pacientes.application.port.ArmazenamentoArquivos;
import br.com.agendafono.pacientes.application.port.PacienteRepository;
import br.com.agendafono.pacientes.domain.Anexo;
import br.com.agendafono.pacientes.domain.Paciente;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

@Service
class AnexosService implements Anexos {

    private static final Logger log = LoggerFactory.getLogger(AnexosService.class);

    private final AnexoRepository anexos;
    private final PacienteRepository pacientes;
    private final ArmazenamentoArquivos armazenamento;
    private final TransactionTemplate transacao;
    private final Relogio relogio;

    AnexosService(AnexoRepository anexos, PacienteRepository pacientes, ArmazenamentoArquivos armazenamento,
                  TransactionTemplate transacao, Relogio relogio) {
        this.anexos = anexos;
        this.pacientes = pacientes;
        this.armazenamento = armazenamento;
        this.transacao = transacao;
        this.relogio = relogio;
    }

    @Override
    public AnexoView anexar(NovoAnexo c) {
        Paciente paciente = pacientes.buscar(c.clinicaId(), c.pacienteId())
                .orElseThrow(() -> new NaoEncontradoException("Paciente", c.pacienteId()));
        if (paciente.anonimizado()) {
            throw new TitularAnonimizadoException();
        }
        Anexo anexo = Anexo.novo(UUID.randomUUID(), c.clinicaId(), c.pacienteId(), c.tipo(), c.nomeArquivo(),
                c.origem(), c.conteudo(), relogio.agora());

        armazenamento.salvar(anexo.chave(), c.conteudo(), anexo.contentType());
        try {
            transacao.executeWithoutResult(s -> anexos.inserir(anexo));
        } catch (RuntimeException e) {
            armazenamento.remover(anexo.chave());
            throw e;
        }
        return PacientesMapper.view(anexo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnexoView> listar(UUID clinicaId, UUID pacienteId) {
        return anexos.doPaciente(clinicaId, pacienteId).stream().map(PacientesMapper::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ConteudoAnexo abrir(UUID clinicaId, UUID anexoId) {
        Anexo anexo = carregar(clinicaId, anexoId);
        return new ConteudoAnexo(PacientesMapper.view(anexo), armazenamento.ler(anexo.chave()));
    }

    @Override
    public void remover(UUID clinicaId, UUID anexoId) {
        Anexo anexo = transacao.execute(s -> {
            Anexo a = carregar(clinicaId, anexoId);
            anexos.remover(clinicaId, anexoId);
            return a;
        });
        try {
            armazenamento.remover(anexo.chave());
        } catch (RuntimeException e) {
            log.error("Registro do anexo {} removido, mas o arquivo não; remover manualmente", anexoId, e);
        }
    }

    private Anexo carregar(UUID clinicaId, UUID anexoId) {
        return anexos.buscar(clinicaId, anexoId)
                .orElseThrow(() -> new NaoEncontradoException("Anexo", anexoId));
    }
}
