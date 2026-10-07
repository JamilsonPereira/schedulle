package br.com.agendafono.bot.adapter.out.mensageria;

import br.com.agendafono.bot.application.port.SaidaWhatsApp;
import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.Opcao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Grava as respostas em {@code evento_saida}, na transação do bot (outbox); o worker da mensageria envia.
 *
 * <ul>
 *   <li>{@code tipo = TEXT}: só {@code texto}; {@code payload = {}}.</li>
 *   <li>{@code tipo = INTERACTIVE}: {@code payload} é o objeto {@code interactive} da Cloud API, pronto para ir em
 *       {@code {"messaging_product":"whatsapp","to":...,"type":"interactive","interactive": payload}}.
 *       {@code texto} repete o corpo (histórico e fallback).</li>
 * </ul>
 * {@code criado_em = clock_timestamp()}: mensagens da mesma transação ficam em ordem crescente (o {@code now()}
 * padrão seria igual para todas), e o worker envia por {@code criado_em}.
 */
@Component
class SaidaWhatsAppJdbc implements SaidaWhatsApp {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    SaidaWhatsAppJdbc(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void enfileirar(UUID clinicaId, String phoneNumberId, String telefone, List<MensagemSaida> mensagens) {
        for (MensagemSaida m : mensagens) {
            boolean interativa = !(m instanceof MensagemSaida.Texto);
            jdbc.sql("""
                            INSERT INTO evento_saida (clinica_id, phone_number_id, telefone, tipo, texto, payload,
                                                      criado_em)
                            VALUES (:clinica, :phone, :telefone, :tipo, :texto, CAST(:payload AS jsonb),
                                    clock_timestamp())
                            """)
                    .param("clinica", clinicaId)
                    .param("phone", phoneNumberId)
                    .param("telefone", telefone)
                    .param("tipo", interativa ? "INTERACTIVE" : "TEXT")
                    .param("texto", m.corpo())
                    .param("payload", interativa ? texto(interactive(m)) : "{}")
                    .update();
        }
    }

    /** Objeto {@code interactive} da Cloud API para botões de resposta ou lista. */
    ObjectNode interactive(MensagemSaida m) {
        ObjectNode raiz = json.createObjectNode();
        switch (m) {
            case MensagemSaida.Botoes b -> {
                raiz.put("type", "button");
                raiz.putObject("body").put("text", b.corpo());
                ArrayNode botoes = raiz.putObject("action").putArray("buttons");
                for (Opcao o : b.botoes()) {
                    ObjectNode botao = botoes.addObject().put("type", "reply");
                    botao.putObject("reply").put("id", o.id()).put("title", o.titulo());
                }
            }
            case MensagemSaida.Lista l -> {
                raiz.put("type", "list");
                raiz.putObject("body").put("text", l.corpo());
                ObjectNode acao = raiz.putObject("action");
                acao.put("button", l.botao());
                ObjectNode secao = acao.putArray("sections").addObject();
                secao.put("title", "Opções");
                ArrayNode linhas = secao.putArray("rows");
                for (Opcao o : l.itens()) {
                    ObjectNode linha = linhas.addObject().put("id", o.id()).put("title", o.titulo());
                    if (o.descricao() != null) {
                        linha.put("description", o.descricao());
                    }
                }
            }
            case MensagemSaida.Texto t -> throw new IllegalArgumentException("Texto não é interativo");
        }
        return raiz;
    }

    private String texto(ObjectNode no) {
        try {
            return json.writeValueAsString(no);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
