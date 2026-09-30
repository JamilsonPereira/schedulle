package br.com.agendafono.clinica.domain;

import br.com.agendafono.clinica.ValidacaoException;

/**
 * Política de agendamento da clínica. Campos nulos usam o padrão do sistema.
 * Gravada em {@code clinica.politica} (jsonb) com estas mesmas chaves, lidas pela agenda.
 */
public record PoliticaClinica(Integer antecedenciaMinimaMin, Integer janelaMaximaDias, Integer passoMin,
                              Integer ttlReservaMin, Integer antecedenciaAvisoFaltaHoras) {

    public static final PoliticaClinica PADRAO = new PoliticaClinica(null, null, null, null, null);

    public PoliticaClinica {
        faixa(antecedenciaMinimaMin, 0, 10_080, "Antecedência mínima (min)");
        faixa(janelaMaximaDias, 1, 180, "Janela máxima (dias)");
        faixa(passoMin, 5, 240, "Passo entre horários (min)");
        faixa(ttlReservaMin, 1, 30, "Validade da reserva (min)");
        faixa(antecedenciaAvisoFaltaHoras, 0, 168, "Antecedência do aviso de falta (h)");
    }

    private static void faixa(Integer valor, int min, int max, String campo) {
        if (valor != null && (valor < min || valor > max)) {
            throw new ValidacaoException(campo + " deve estar entre " + min + " e " + max);
        }
    }

    /** JSON gravado no banco. Só números: montado à mão para não depender de biblioteca de JSON aqui. */
    public String comoJson() {
        StringBuilder json = new StringBuilder("{");
        adicionar(json, "antecedenciaMinimaMin", antecedenciaMinimaMin);
        adicionar(json, "janelaMaximaDias", janelaMaximaDias);
        adicionar(json, "passoMin", passoMin);
        adicionar(json, "ttlReservaMin", ttlReservaMin);
        adicionar(json, "antecedenciaAvisoFaltaHoras", antecedenciaAvisoFaltaHoras);
        return json.append('}').toString();
    }

    private static void adicionar(StringBuilder json, String chave, Integer valor) {
        if (valor == null) {
            return;
        }
        if (json.length() > 1) {
            json.append(',');
        }
        json.append('"').append(chave).append("\":").append(valor);
    }
}
