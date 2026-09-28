package br.com.agendafono.pacientes.domain;

import br.com.agendafono.pacientes.Telefone;
import br.com.agendafono.pacientes.TelefoneInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelefoneTest {

    @ParameterizedTest
    @CsvSource({
            "'(11) 99999-0000',   +5511999990000",
            "'11 3333-4444',      +551133334444",
            "'5511999990000',     +5511999990000",
            "'+55 11 99999-0000', +5511999990000",
            "'+1 (555) 123-4567', +15551234567"
    })
    void normalizaNumeroDigitado(String digitado, String e164) {
        assertThat(Telefone.digitado(digitado).e164()).isEqualTo(e164);
    }

    @Test
    void recusaNumerosIncompletos() {
        assertThatThrownBy(() -> Telefone.digitado("99999-0000")).isInstanceOf(TelefoneInvalidoException.class);
        assertThatThrownBy(() -> Telefone.digitado("123")).isInstanceOf(TelefoneInvalidoException.class);
        assertThatThrownBy(() -> Telefone.digitado(" ")).isInstanceOf(TelefoneInvalidoException.class);
    }

    @Test
    void waIdDoWhatsAppViraE164() {
        assertThat(Telefone.doWhatsApp("5511999990000").e164()).isEqualTo("+5511999990000");
        assertThat(Telefone.doWhatsApp("5511999990000").somenteDigitos()).isEqualTo("5511999990000");
    }

    @Test
    void celularBrasileiroTemVarianteComESemNonoDigito() {
        assertThat(Telefone.doWhatsApp("551199990000").variantes()).extracting(Telefone::e164)
                .containsExactly("+551199990000", "+5511999990000");
        assertThat(Telefone.digitado("(11) 99999-0000").variantes()).extracting(Telefone::e164)
                .containsExactly("+5511999990000", "+551199990000");
    }

    @Test
    void fixoEInternacionalNaoTemVariante() {
        assertThat(Telefone.digitado("11 3333-4444").variantes()).hasSize(1);
        assertThat(Telefone.digitado("+1 555 123 4567").variantes()).hasSize(1);
    }

    @Test
    void mascaraSemExporONumero() {
        assertThat(Telefone.digitado("(11) 99999-0000").mascarado()).isEqualTo("(11) 9****-0000");
        assertThat(Telefone.digitado("11 3333-4444").mascarado()).isEqualTo("(11) ****-4444");
        assertThat(Telefone.digitado("+1 555 123 4567").mascarado()).isEqualTo("+*******4567");
        assertThat(Telefone.digitado("(11) 99999-0000").toString()).doesNotContain("99999");
    }
}
