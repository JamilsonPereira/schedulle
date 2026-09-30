package br.com.agendafono.clinica.application.port;

import br.com.agendafono.clinica.domain.Usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository {

    Optional<Usuario> buscar(UUID clinicaId, UUID usuarioId);

    /** O e-mail é único no sistema inteiro (é o login). */
    Optional<Usuario> porEmail(String emailNormalizado);

    List<Usuario> listar(UUID clinicaId);

    /** @throws br.com.agendafono.clinica.EmailJaCadastradoException */
    void inserir(Usuario usuario);

    /** @throws br.com.agendafono.clinica.ConflitoDeVersaoException */
    void atualizar(Usuario usuario);

    /** Grava só os campos de login (tentativas, bloqueio, último acesso) sem mexer na versão. */
    void atualizarEstadoDeLogin(Usuario usuario);
}
