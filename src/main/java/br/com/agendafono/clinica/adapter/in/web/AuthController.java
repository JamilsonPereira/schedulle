package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.LoginRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.LogoutRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.RefreshRequest;
import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.TrocarSenhaRequest;
import br.com.agendafono.clinica.application.AutenticacaoService;
import br.com.agendafono.clinica.application.AutenticacaoService.Perfil;
import br.com.agendafono.clinica.application.AutenticacaoService.SessaoDeLogin;
import br.com.agendafono.compartilhado.seguranca.UsuarioAutenticado;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login e sessão. Os tokens vão no corpo; respostas com token nunca são guardadas em cache.
 * Login, refresh e logout são públicos (ver {@code ConfiguracaoSeguranca}); o resto exige o JWT.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AutenticacaoService autenticacao;

    AuthController(AutenticacaoService autenticacao) {
        this.autenticacao = autenticacao;
    }

    @PostMapping("/login")
    ResponseEntity<SessaoDeLogin> login(@Valid @RequestBody LoginRequest r) {
        return semCache(autenticacao.login(r.email(), r.senha()));
    }

    @PostMapping("/refresh")
    ResponseEntity<SessaoDeLogin> refresh(@Valid @RequestBody RefreshRequest r) {
        return semCache(autenticacao.renovar(r.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest r) {
        autenticacao.encerrar(r.refreshToken(), r.todas());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    Perfil me(UsuarioAutenticado usuario) {
        return autenticacao.perfil(usuario);
    }

    /** Troca a própria senha; encerra as demais sessões e devolve tokens novos. */
    @PostMapping("/senha")
    ResponseEntity<SessaoDeLogin> trocarSenha(UsuarioAutenticado usuario, @Valid @RequestBody TrocarSenhaRequest r) {
        return semCache(autenticacao.trocarSenha(usuario, r.senhaAtual(), r.novaSenha()));
    }

    private static <T> ResponseEntity<T> semCache(T corpo) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corpo);
    }
}
