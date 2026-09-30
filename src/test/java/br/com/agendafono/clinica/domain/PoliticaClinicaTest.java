package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ValidacaoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoliticaClinicaTest {

    @Test
    void jsonSoTemOsCamposInformados() {
        assertThat(PoliticaClinica.PADRAO.comoJson()).isEqualTo("{}");
        assertThat(new PoliticaClinica(120, null, 20, null, 24).comoJson())
                .isEqualTo("{\"antecedenciaMinimaMin\":120,\"passoMin\":20,\"antecedenciaAvisoFaltaHoras\":24}");
    }

    @Test
    void validaFaixas() {
        assertThatThrownBy(() -> new PoliticaClinica(null, 0, null, null, null))
                .isInstanceOf(ValidacaoException.class);
        assertThatThrownBy(() -> new PoliticaClinica(null, null, null, 60, null))
                .isInstanceOf(ValidacaoException.class);
    }
}
