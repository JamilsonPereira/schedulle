package br.com.agendafono.bot.domain;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * O que o contato mandou, já traduzido do formato da Meta.
 *
 * @param opcaoId id do botão ou item de lista clicado; nulo quando digitou texto
 */
public record Entrada(Tipo tipo, String texto, String opcaoId, String wamid, Instant recebidaEm) {

    public enum Tipo { TEXTO, OPCAO, OUTRO }

    private static final Set<String> PEDIDOS_DE_ATENDENTE = Set.of("atendente", "recepcao", "humano",
            "falar com a recepcao", "falar com alguem", "pessoa");
    private static final Set<String> CUMPRIMENTOS = Set.of("oi", "ola", "bom dia", "boa tarde", "boa noite", "ok",
            "obrigado", "obrigada", "valeu", "menu", "inicio", "voltar");

    public static Entrada texto(String texto, String wamid, Instant em) {
        return new Entrada(Tipo.TEXTO, texto, null, wamid, em);
    }

    public static Entrada opcao(String id, String titulo, String wamid, Instant em) {
        return new Entrada(Tipo.OPCAO, titulo, id, wamid, em);
    }

    /** Texto em minúsculas, sem acento, pontuação nem espaços repetidos. */
    public String normalizado() {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/ ]", " ").replaceAll("\\s+", " ").trim();
    }

    public boolean pedeAtendente() {
        String t = normalizado();
        return PEDIDOS_DE_ATENDENTE.contains(t) || t.contains("atendente") || t.contains("falar com a recepcao");
    }

    public boolean cumprimento() {
        return CUMPRIMENTOS.contains(normalizado());
    }

    /**
     * Qual opção foi escolhida: pelo clique (id), pelo texto igual ao título ou pelo número ("1", "2"...).
     * Permite que o contato responda digitando quando o WhatsApp dele não mostra botões.
     */
    public Optional<String> escolha(List<Opcao> opcoes) {
        if (opcaoId != null) {
            return opcoes.stream().map(Opcao::id).filter(opcaoId::equals).findFirst();
        }
        String t = normalizado();
        if (t.isEmpty()) {
            return Optional.empty();
        }
        if (t.matches("\\d{1,2}")) {
            int i = Integer.parseInt(t) - 1;
            return i >= 0 && i < opcoes.size() ? Optional.of(opcoes.get(i).id()) : Optional.empty();
        }
        for (Opcao o : opcoes) {
            String titulo = Entrada.texto(o.titulo(), null, null).normalizado();
            if (t.equals(titulo)) {
                return Optional.of(o.id());
            }
        }
        return Optional.empty();
    }
}
