package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.Eventos.BloqueioCriado;
import br.com.agendafono.clinica.Eventos.BloqueioRemovido;
import br.com.agendafono.clinica.OperacaoNaoPermitidaException;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.clinica.Views.BloqueioView;
import br.com.agendafono.clinica.application.port.BloqueioRepository;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.domain.Bloqueio;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bloqueios de agenda. O perfil FONO só cria e remove bloqueios da própria agenda. */
@Service
public class BloqueiosService {

    private final BloqueioRepository bloqueios;
    private final ProfissionalRepository profissionais;
    private final ApplicationEventPublisher eventos;
    private final Auditoria auditoria;

    public BloqueiosService(BloqueioRepository bloqueios, ProfissionalRepository profissionais,
                            ApplicationEventPublisher eventos, Auditoria auditoria) {
        this.bloqueios = bloqueios;
        this.profissionais = profissionais;
        this.eventos = eventos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<BloqueioView> listar(UUID clinicaId, UUID profissionalId, Instant de, Instant ate) {
        if (!ate.isAfter(de) || Duration.between(de, ate).toDays() > 400) {
            throw new ValidacaoException("Intervalo inválido (máximo de 400 dias)");
        }
        return bloqueios.listar(clinicaId, profissionalId, de, ate).stream().map(ClinicaMapper::view).toList();
    }

    @Transactional
    public BloqueioView criar(UsuarioAutenticado ator, UUID profissionalId, Instant inicio, Instant fim,
                              String motivo) {
        exigirPermissao(ator, profissionalId);
        if (profissionalId != null && profissionais.buscar(ator.clinicaId(), profissionalId).isEmpty()) {
            throw new RegistroNaoEncontradoException("Profissional", profissionalId);
        }
        Bloqueio b = new Bloqueio(UUID.randomUUID(), ator.clinicaId(), profissionalId, inicio, fim, motivo);
        bloqueios.inserir(b);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "BLOQUEIO_CRIADO", "bloqueio", b.id());
        eventos.publishEvent(new BloqueioCriado(b.clinicaId(), b.id(), b.profissionalId(), b.inicio(), b.fim()));
        return ClinicaMapper.view(b);
    }

    @Transactional
    public void remover(UsuarioAutenticado ator, UUID bloqueioId) {
        Bloqueio b = bloqueios.buscar(ator.clinicaId(), bloqueioId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Bloqueio", bloqueioId));
        exigirPermissao(ator, b.profissionalId());
        bloqueios.remover(ator.clinicaId(), bloqueioId);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "BLOQUEIO_REMOVIDO", "bloqueio", b.id());
        eventos.publishEvent(new BloqueioRemovido(b.clinicaId(), b.id()));
    }

    private static void exigirPermissao(UsuarioAutenticado ator, UUID profissionalId) {
        if (ator.somenteFono() && (profissionalId == null || !Objects.equals(profissionalId, ator.profissionalId()))) {
            throw new OperacaoNaoPermitidaException("O perfil FONO só gerencia bloqueios da própria agenda");
        }
    }
}
