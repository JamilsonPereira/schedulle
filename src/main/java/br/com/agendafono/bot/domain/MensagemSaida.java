package br.com.agendafono.bot.domain;

import java.util.List;
import java.util.Objects;

/**
 * Resposta do bot. Respeita os limites da Cloud API da Meta: até 3 botões de resposta (título até 20 caracteres)
 * e listas de até 10 itens (título até 24, descrição até 72). Textos maiores são cortados com reticências.
 */
public sealed interface MensagemSaida permits MensagemSaida.Texto, MensagemSaida.Botoes, MensagemSaida.Lista {

    String corpo();

    record Texto(String corpo) implements MensagemSaida {
        public Texto {
            corpo = cortar(Objects.requireNonNull(corpo), 4096);
        }
    }

    record Botoes(String corpo, List<Opcao> botoes) implements MensagemSaida {
        public Botoes {
            corpo = cortar(Objects.requireNonNull(corpo), 1024);
            if (botoes.isEmpty() || botoes.size() > 3) {
                throw new IllegalArgumentException("Botões de resposta: de 1 a 3");
            }
            botoes = botoes.stream().map(b -> new Opcao(b.id(), cortar(b.titulo(), 20), null)).toList();
        }
    }

    /** @param botao texto do botão que abre a lista (até 20 caracteres) */
    record Lista(String corpo, String botao, List<Opcao> itens) implements MensagemSaida {
        public Lista {
            corpo = cortar(Objects.requireNonNull(corpo), 4096);
            botao = cortar(Objects.requireNonNull(botao), 20);
            if (itens.isEmpty() || itens.size() > 10) {
                throw new IllegalArgumentException("Lista: de 1 a 10 itens");
            }
            itens = itens.stream()
                    .map(i -> new Opcao(i.id(), cortar(i.titulo(), 24), i.descricao() == null ? null : cortar(i.descricao(), 72)))
                    .toList();
        }
    }

    static String cortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 1) + "…";
    }
}
