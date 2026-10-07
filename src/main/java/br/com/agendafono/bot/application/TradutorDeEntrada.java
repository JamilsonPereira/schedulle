package br.com.agendafono.bot.application;

import br.com.agendafono.bot.MensagemDoContato;
import br.com.agendafono.bot.domain.Entrada;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Traduz a mensagem da Cloud API para uma {@link Entrada}. O id do botão ou do item de lista vem no JSON da
 * mensagem ({@code interactive.button_reply}, {@code interactive.list_reply} ou {@code button.payload}).
 */
public final class TradutorDeEntrada {

    private final ObjectMapper json;

    public TradutorDeEntrada(ObjectMapper json) {
        this.json = json;
    }

    public Entrada traduzir(MensagemDoContato m) {
        JsonNode raiz = ler(m.payload());
        JsonNode interactive = raiz.path("interactive");
        for (String tipo : new String[] {"button_reply", "list_reply"}) {
            JsonNode resposta = interactive.path(tipo);
            if (resposta.hasNonNull("id")) {
                return Entrada.opcao(resposta.get("id").asText(), resposta.path("title").asText(null), m.wamid(),
                        m.recebidaEm());
            }
        }
        JsonNode botao = raiz.path("button");
        if (botao.hasNonNull("payload")) {
            return Entrada.opcao(botao.get("payload").asText(), botao.path("text").asText(null), m.wamid(),
                    m.recebidaEm());
        }
        String texto = m.texto();
        if ((texto == null || texto.isBlank()) && raiz.path("text").hasNonNull("body")) {
            texto = raiz.path("text").get("body").asText();
        }
        if (texto != null && !texto.isBlank()) {
            return Entrada.texto(texto, m.wamid(), m.recebidaEm());
        }
        // áudio, imagem, figurinha...: o bot pede para tocar numa opção
        return new Entrada(Entrada.Tipo.OUTRO, null, null, m.wamid(), m.recebidaEm());
    }

    private JsonNode ler(String payload) {
        if (payload == null || payload.isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(payload);
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }
}
