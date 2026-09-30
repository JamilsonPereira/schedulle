package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.SenhaFracaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SenhasTest {

    @Test
    void aceitaSenhaRazoavel() {
        assertThatCode(() -> Senhas.validar("Caneca-Azul-42", "ana@clinica.com")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta", "1234567890", "aaaaaaaaaaaa", "ababababab"})
    void recusaSenhasFracas(String senha) {
        assertThatThrownBy(() -> Senhas.validar(senha, "ana@clinica.com")).isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void recusaSenhaQueContemOEmail() {
        assertThatThrownBy(() -> Senhas.validar("marina2026!", "Marina@clinica.com"))
                .isInstanceOf(SenhaFracaException.class);
    }

    @Test
    void senhaTemporariaTem14CaracteresSemAmbiguidadeEPassaNaValidacao() {
        Set<String> geradas = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String s = Senhas.temporaria();
            assertThat(s).hasSize(14).doesNotContainPattern("[0O1lI]");
            geradas.add(s);
        }
        assertThat(geradas).hasSize(200);
        assertThatCode(() -> Senhas.validar(Senhas.temporaria(), "ana@clinica.com")).doesNotThrowAnyException();
    }
}
