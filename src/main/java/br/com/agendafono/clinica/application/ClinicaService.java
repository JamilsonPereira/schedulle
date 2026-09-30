package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.ClinicaConsulta;
import br.com.agendafono.clinica.RegistroNaoEncontradoException;
import br.com.agendafono.clinica.TipoRecurso;
import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.RecursoView;
import br.com.agendafono.clinica.application.port.ClinicaRepository;
import br.com.agendafono.clinica.application.port.RecursoRepository;
import br.com.agendafono.clinica.domain.Clinica;
import br.com.agendafono.clinica.domain.PoliticaClinica;
import br.com.agendafono.clinica.domain.Recurso;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dados da clínica, política de agendamento e salas. */
@Service
public class ClinicaService implements ClinicaConsulta {

    private final ClinicaRepository clinicas;
    private final RecursoRepository recursos;
    private final Auditoria auditoria;

    public ClinicaService(ClinicaRepository clinicas, RecursoRepository recursos, Auditoria auditoria) {
        this.clinicas = clinicas;
        this.recursos = recursos;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClinicaView> clinica(UUID clinicaId) {
        return clinicas.buscar(clinicaId).map(ClinicaMapper::view);
    }

    @Transactional
    public ClinicaView atualizar(UsuarioAutenticado ator, String nome, String fuso, Integer versaoEsperada) {
        Clinica c = carregar(ator.clinicaId());
        c.exigirVersao(versaoEsperada);
        c.atualizar(nome, fuso);
        clinicas.atualizar(c);
        auditoria.registrar(c.id(), ator.usuarioId(), "CLINICA_ALTERADA", "clinica", c.id());
        return ClinicaMapper.view(c);
    }

    @Transactional
    public ClinicaView definirPolitica(UsuarioAutenticado ator, PoliticaClinica politica, Integer versaoEsperada) {
        Clinica c = carregar(ator.clinicaId());
        c.exigirVersao(versaoEsperada);
        c.definirPolitica(politica);
        clinicas.atualizar(c);
        auditoria.registrar(c.id(), ator.usuarioId(), "POLITICA_ALTERADA", "clinica", c.id());
        return ClinicaMapper.view(c);
    }

    // ------------------------------------------------------------------ salas

    @Transactional(readOnly = true)
    public List<RecursoView> recursos(UUID clinicaId) {
        return recursos.listar(clinicaId).stream().map(ClinicaMapper::view).toList();
    }

    @Transactional
    public RecursoView criarRecurso(UsuarioAutenticado ator, String nome, TipoRecurso tipo) {
        Recurso r = Recurso.novo(UUID.randomUUID(), ator.clinicaId(), nome, tipo);
        recursos.inserir(r);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "SALA_CRIADA", "recurso", r.id());
        return ClinicaMapper.view(r);
    }

    @Transactional
    public RecursoView atualizarRecurso(UsuarioAutenticado ator, UUID recursoId, String nome, TipoRecurso tipo,
                                        boolean ativo, Integer versaoEsperada) {
        Recurso r = recursos.buscar(ator.clinicaId(), recursoId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Sala", recursoId));
        r.exigirVersao(versaoEsperada);
        r.atualizar(nome, tipo, ativo);
        recursos.atualizar(r);
        auditoria.registrar(ator.clinicaId(), ator.usuarioId(), "SALA_ALTERADA", "recurso", r.id());
        return ClinicaMapper.view(r);
    }

    private Clinica carregar(UUID clinicaId) {
        return clinicas.buscar(clinicaId)
                .orElseThrow(() -> new RegistroNaoEncontradoException("Clínica", clinicaId));
    }
}
