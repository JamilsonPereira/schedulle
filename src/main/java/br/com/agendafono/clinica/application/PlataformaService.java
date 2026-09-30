package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.Views.ClinicaView;
import br.com.agendafono.clinica.Views.UsuarioView;
import br.com.agendafono.clinica.application.port.ClinicaRepository;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.Clinica;
import br.com.agendafono.clinica.domain.Senhas;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.Relogio;
import br.com.agendafono.compartilhado.auditoria.Auditoria;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

/** Onboarding de uma clínica nova com o primeiro administrador (uso interno da plataforma). */
@Service
public class PlataformaService {

    private final ClinicaRepository clinicas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder senhas;
    private final Auditoria auditoria;
    private final Relogio relogio;

    public PlataformaService(ClinicaRepository clinicas, UsuarioRepository usuarios, PasswordEncoder senhas,
                             Auditoria auditoria, Relogio relogio) {
        this.clinicas = clinicas;
        this.usuarios = usuarios;
        this.senhas = senhas;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    public record ClinicaCriada(ClinicaView clinica, UsuarioView administrador, String senhaTemporaria) {
    }

    @Transactional
    public ClinicaCriada criarClinica(String nomeClinica, String fuso, String nomeAdmin, String emailAdmin) {
        Clinica clinica = Clinica.nova(UUID.randomUUID(), nomeClinica, fuso);
        clinicas.inserir(clinica);
        String temporaria = Senhas.temporaria();
        Usuario admin = Usuario.novo(UUID.randomUUID(), clinica.id(), nomeAdmin, emailAdmin,
                EnumSet.of(Papel.ADMIN), senhas.encode(temporaria));
        usuarios.inserir(admin);
        auditoria.registrar(clinica.id(), null, "CLINICA_CRIADA", "clinica", clinica.id());
        return new ClinicaCriada(ClinicaMapper.view(clinica), ClinicaMapper.view(admin, null, relogio.agora()),
                temporaria);
    }
}
