package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.ProfissionalConsulta;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.clinica.Views.ProfissionalView;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.application.port.RecursoRepository;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.IntervaloGrade;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.Papel;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Profissionais e grade semanal. */
@Service
public class ProfissionaisService implements ProfissionalConsulta {

    private final ProfissionalRepository profissionais;
    private final UsuarioRepository usuarios;
    private final RecursoRepository recursos;
    private final Auditoria auditoria;

    public ProfissionaisService(ProfissionalRepository profissionais, UsuarioRepository usuarios,
                                RecursoRepository recursos, Auditoria auditoria) {
        this.profissionais = profissionais;
        this.usuarios = usuarios;
        this.recursos = recursos;
        this.auditoria = auditoria;
    }

    public record DadosProfissional(String nome, String registroCrfa, Set<Subarea> subareas,
                                    Integer duracaoPadraoMin, UUID usuarioId) {
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProfissionalView> profissional(UUID clinicaId, UUID profissionalId) {
        return profissionais.buscar(clinicaId, profissionalId).map(ClinicaMapper::view);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProfissionalView> ativos(UUID clinicaId, Subarea subarea) {
        return profissionais.listar(clinicaId, true, subarea).stream().map(ClinicaMapper::view).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfissionalView> listar(UUID clinicaId, boolean apenasAtivos) {
        return profissionais.listar(clinicaId, apenasAtivos, null).stream().map(ClinicaMapper::view).toList();
    }

    @Transactional
    public ProfissionalView criar(UsuarioAutenticado ator, DadosProfissional d) {
        validarUsuarioFono(ator.clinicaId(), d.usuarioId());
        Profissional p = Profissional.novo(UUID.randomUUID(), ator.clinicaId(), d.nome(), d.registroCrfa(),
                d.subareas(), d.duracaoPadraoMin(), d.usuarioId());
        profissionais.inserir(p);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "PROFISSIONAL_CRIADO", "profissional", p.id());
        return ClinicaMapper.view(p);
    }

    @Transactional
    public ProfissionalView atualizar(UsuarioAutenticado ator, UUID profissionalId, DadosProfissional d,
                                      Integer versaoEsperada) {
        validarUsuarioFono(ator.clinicaId(), d.usuarioId());
        return alterar(ator, profissionalId, versaoEsperada, "PROFISSIONAL_ALTERADO",
                p -> p.atualizar(d.nome(), d.registroCrfa(), d.subareas(), d.duracaoPadraoMin(), d.usuarioId()));
    }

    /** Substitui a grade. Salas precisam ser desta clínica e estar ativas. */
    @Transactional
    public ProfissionalView definirGrade(UsuarioAutenticado ator, UUID profissionalId, List<IntervaloGrade> grade,
                                         Integer versaoEsperada) {
        List<UUID> salas = grade.stream().map(IntervaloGrade::recursoId).filter(Objects::nonNull).distinct()
                .toList();
        if (!salas.isEmpty() && recursos.contarAtivos(ator.clinicaId(), salas) != salas.size()) {
            throw new ValidacaoException("A grade usa uma sala inexistente ou inativa");
        }
        return alterar(ator, profissionalId, versaoEsperada, "GRADE_ALTERADA", p -> p.definirGrade(grade));
    }

    @Transactional
    public ProfissionalView inativar(UsuarioAutenticado ator, UUID profissionalId, Integer versaoEsperada) {
        return alterar(ator, profissionalId, versaoEsperada, "PROFISSIONAL_INATIVADO", Profissional::inativar);
    }

    @Transactional
    public ProfissionalView reativar(UsuarioAutenticado ator, UUID profissionalId, Integer versaoEsperada) {
        return alterar(ator, profissionalId, versaoEsperada, "PROFISSIONAL_REATIVADO", Profissional::reativar);
    }

    private ProfissionalView alterar(UsuarioAutenticado ator, UUID profissionalId, Integer versaoEsperada,
                                     String acao, Consumer<Profissional> mudanca) {
        Profissional p = profissionais.buscar(ator.clinicaId(), profissionalId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Profissional", profissionalId));
        p.exigirVersao(versaoEsperada);
        mudanca.accept(p);
        profissionais.atualizar(p);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), acao, "profissional", p.id());
        return ClinicaMapper.view(p);
    }

    /** O login vinculado a um profissional precisa ser desta clínica e ter o perfil FONO. */
    private void validarUsuarioFono(UUID clinicaId, UUID usuarioId) {
        if (usuarioId == null) {
            return;
        }
        boolean ok = usuarios.buscar(clinicaId, usuarioId).map(u -> u.papeis().contains(Papel.FONO)).orElse(false);
        if (!ok) {
            throw new ValidacaoException("O usuário vinculado precisa existir nesta clínica e ter o perfil FONO");
        }
    }
}
