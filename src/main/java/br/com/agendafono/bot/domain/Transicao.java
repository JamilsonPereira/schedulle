package br.com.agendafono.bot.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resultado de uma etapa: próximo estado, novo contexto e respostas a enviar.
 * {@link Resultado#NAO_ENTENDI} conta como tentativa falha; {@link Resultado#TRANSBORDAR} passa para a recepção.
 */
public record Transicao(EstadoConversa proximo, ContextoConversa contexto, List<MensagemSaida> respostas,
                        Resultado resultado, String motivoTransbordo) {

    public enum Resultado { OK, NAO_ENTENDI, TRANSBORDAR }

    public Transicao {
        Objects.requireNonNull(proximo, "proximo");
        Objects.requireNonNull(contexto, "contexto");
        respostas = List.copyOf(respostas);
    }

    public static Transicao para(EstadoConversa proximo, ContextoConversa contexto, MensagemSaida... respostas) {
        return new Transicao(proximo, contexto, List.of(respostas), Resultado.OK, null);
    }

    /** Fica no mesmo estado e repete a pergunta (com um aviso de que não entendeu). */
    public static Transicao naoEntendi(EstadoConversa atual, ContextoConversa contexto, MensagemSaida... repergunta) {
        return new Transicao(atual, contexto, List.of(repergunta), Resultado.NAO_ENTENDI, null);
    }

    /** @param motivo nome de {@code TransbordoSolicitado.Motivo} */
    public static Transicao transbordar(ContextoConversa contexto, String motivo, MensagemSaida... antes) {
        return new Transicao(EstadoConversa.HUMANO, contexto, List.of(antes), Resultado.TRANSBORDAR, motivo);
    }
}
