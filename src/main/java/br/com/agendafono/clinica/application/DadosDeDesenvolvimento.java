package br.com.agendafono.clinica.application;

import br.com.agendafono.clinica.Subarea;
import br.com.agendafono.clinica.TipoRecurso;
import br.com.agendafono.clinica.application.port.ClinicaRepository;
import br.com.agendafono.clinica.application.port.ProfissionalRepository;
import br.com.agendafono.clinica.application.port.RecursoRepository;
import br.com.agendafono.clinica.application.port.UsuarioRepository;
import br.com.agendafono.clinica.domain.Clinica;
import br.com.agendafono.clinica.domain.IntervaloGrade;
import br.com.agendafono.clinica.domain.Profissional;
import br.com.agendafono.clinica.domain.Recurso;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * SÓ PARA DESENVOLVIMENTO (perfil {@code dev}): cria uma clínica de teste com três logins de senha conhecida,
 * já sem troca obrigatória, uma sala e um profissional com grade de segunda a sexta.
 *
 * <p>Idempotente: se o e-mail do ADMIN já existir, não faz nada. Nunca ative o perfil {@code dev} em produção.
 */
@Component
@Profile("dev")
class DadosDeDesenvolvimento implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DadosDeDesenvolvimento.class);

    static final String EMAIL_ADMIN = "admin@clinica.dev";
    static final String EMAIL_RECEPCAO = "recepcao@clinica.dev";
    static final String EMAIL_FONO = "fono@clinica.dev";

    private final ClinicaRepository clinicas;
    private final UsuarioRepository usuarios;
    private final ProfissionalRepository profissionais;
    private final RecursoRepository recursos;
    private final PasswordEncoder senhas;
    private final TransactionTemplate transacao;
    private final String senhaPadrao;

    DadosDeDesenvolvimento(ClinicaRepository clinicas, UsuarioRepository usuarios,
                           ProfissionalRepository profissionais, RecursoRepository recursos,
                           PasswordEncoder senhas, TransactionTemplate transacao,
                           @Value("${dev.seed.senha:Agenda-Dev-2026}") String senhaPadrao) {
        this.clinicas = clinicas;
        this.usuarios = usuarios;
        this.profissionais = profissionais;
        this.recursos = recursos;
        this.senhas = senhas;
        this.transacao = transacao;
        this.senhaPadrao = senhaPadrao;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarios.porEmail(EMAIL_ADMIN).isPresent()) {
            log.info("Dados de desenvolvimento já existem ({}); nada a criar.", EMAIL_ADMIN);
            return;
        }
        transacao.executeWithoutResult(s -> criar());
        log.warn("Perfil dev: clínica de teste criada. Logins {}, {} e {} com a senha padrão de dev.seed.senha.",
                EMAIL_ADMIN, EMAIL_RECEPCAO, EMAIL_FONO);
    }

    private void criar() {
        Clinica clinica = Clinica.nova(UUID.randomUUID(), "Clínica de Desenvolvimento", "America/Sao_Paulo");
        clinicas.inserir(clinica);

        String hash = senhas.encode(senhaPadrao);
        usuario(clinica, "Administração (dev)", EMAIL_ADMIN, EnumSet.of(Papel.ADMIN), hash);
        usuario(clinica, "Recepção (dev)", EMAIL_RECEPCAO, EnumSet.of(Papel.RECEPCAO), hash);
        Usuario fono = usuario(clinica, "Fono (dev)", EMAIL_FONO, EnumSet.of(Papel.FONO), hash);

        Recurso sala = Recurso.novo(UUID.randomUUID(), clinica.id(), "Sala 1", TipoRecurso.SALA);
        recursos.inserir(sala);

        Profissional p = Profissional.novo(UUID.randomUUID(), clinica.id(), "Dra. Teste", "2-00000",
                EnumSet.of(Subarea.LINGUAGEM, Subarea.GAGUEIRA), 40, fono.id());
        List<IntervaloGrade> grade = new ArrayList<>();
        for (DayOfWeek dia : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY)) {
            grade.add(new IntervaloGrade(dia, LocalTime.of(8, 0), LocalTime.of(12, 0), sala.id()));
            grade.add(new IntervaloGrade(dia, LocalTime.of(13, 0), LocalTime.of(18, 0), null));
        }
        p.definirGrade(grade);
        profissionais.inserir(p);
    }

    /** Usuário com a senha padrão e sem troca obrigatória no primeiro acesso. */
    private Usuario usuario(Clinica clinica, String nome, String email, Set<Papel> papeis, String hash) {
        Usuario u = Usuario.novo(UUID.randomUUID(), clinica.id(), nome, email, papeis, hash);
        u.trocarSenha(hash);
        usuarios.inserir(u);
        return u;
    }
}
