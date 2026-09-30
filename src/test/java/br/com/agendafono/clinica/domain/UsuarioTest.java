package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ConflitoDeVersaoException;
import br.com.agendafono.clinica.ValidacaoException;
import br.com.agendafono.compartilhado.seguranca.Papel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioTest {

    private static final Instant AGORA = Instant.parse("2026-10-01T12:00:00Z");

    private Usuario novo() {
        return Usuario.novo(UUID.randomUUID(), UUID.randomUUID(), "Ana", "  Ana@Clinica.COM ", Set.of(Papel.RECEPCAO),
                "{bcrypt}hash");
    }

    @Test
    void novoUsuarioTemEmailNormalizadoEPrecisaTrocarSenha() {
        Usuario u = novo();
        assertThat(u.email()).isEqualTo("ana@clinica.com");
        assertThat(u.precisaTrocarSenha()).isTrue();
        assertThat(u.podeEntrar(AGORA)).isTrue();
    }

    @Test
    void recusaEmailInvalidoESemPerfil() {
        assertThatThrownBy(() -> Usuario.novo(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana", Set.of(Papel.FONO),
                "h")).isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> Usuario.novo(UUID.randomUUID(), UUID.randomUUID(), "Ana", "ana@x.com", Set.of(),
                "h")).isInstanceOf(ValidacaoException.class);
    }

    @Test
    void bloqueiaAposCincoFalhasEDesbloqueiaDepoisDoTempo() {
        Usuario u = novo();
        for (int i = 0; i < 4; i++) {
            u.registrarFalhaDeLogin(AGORA, 5, Duration.ofMinutes(15));
        }
        assertThat(u.podeEntrar(AGORA)).isTrue();

        u.registrarFalhaDeLogin(AGORA, 5, Duration.ofMinutes(15));
        assertThat(u.podeEntrar(AGORA)).isFalse();
        assertThat(u.podeEntrar(AGORA.plus(Duration.ofMinutes(14)))).isFalse();
        assertThat(u.podeEntrar(AGORA.plus(Duration.ofMinutes(15)))).isTrue();
    }

    @Test
    void loginComSucessoZeraAsFalhas() {
        Usuario u = novo();
        for (int i = 0; i < 4; i++) {
            u.registrarFalhaDeLogin(AGORA, 5, Duration.ofMinutes(15));
        }
        u.registrarLogin(AGORA);
        u.registrarFalhaDeLogin(AGORA, 5, Duration.ofMinutes(15));
        assertThat(u.podeEntrar(AGORA)).isTrue();
    }

    @Test
    void inativoNaoEntra() {
        Usuario u = novo();
        u.atualizar("Ana", Set.of(Papel.RECEPCAO), false);
        assertThat(u.podeEntrar(AGORA)).isFalse();
    }

    @Test
    void trocarSenhaLimpaObrigacaoESenhaTemporariaVoltaAExigir() {
        Usuario u = novo();
        u.trocarSenha("{bcrypt}nova");
        assertThat(u.precisaTrocarSenha()).isFalse();
        u.definirSenhaTemporaria("{bcrypt}temp");
        assertThat(u.precisaTrocarSenha()).isTrue();
    }

    @Test
    void versaoDesatualizadaGeraConflito() {
        Usuario u = novo();
        assertThatThrownBy(() -> u.exigirVersao(3)).isInstanceOf(ConflitoDeVersaoException.class);
        u.exigirVersao(0);
        u.exigirVersao(null);
    }
}
