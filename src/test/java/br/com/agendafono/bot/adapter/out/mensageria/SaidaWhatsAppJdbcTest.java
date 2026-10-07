package br.com.agendafono.bot.adapter.out.mensageria;

import br.com.agendafono.bot.domain.MensagemSaida;
import br.com.agendafono.bot.domain.Opcao;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Formato do objeto {@code interactive} que vai para a Cloud API. */
class SaidaWhatsAppJdbcTest {

    private final SaidaWhatsAppJdbc saida = new SaidaWhatsAppJdbc(null, JsonMapper.builder().build());

    @Test
    void botoesDeResposta() {
        var json = saida.interactive(new MensagemSaida.Botoes("Você concorda?",
                List.of(new Opcao("consentimento:aceito", "Aceito"), new Opcao("consentimento:recusado", "Não aceito"))));
        assertThat(json.toString()).isEqualTo("{\"type\":\"button\",\"body\":{\"text\":\"Você concorda?\"},"
                + "\"action\":{\"buttons\":[{\"type\":\"reply\",\"reply\":{\"id\":\"consentimento:aceito\",\"title\":\"Aceito\"}},"
                + "{\"type\":\"reply\",\"reply\":{\"id\":\"consentimento:recusado\",\"title\":\"Não aceito\"}}]}}");
    }

    @Test
    void listaComDescricaoOpcional() {
        var json = saida.interactive(new MensagemSaida.Lista("Escolha um horário", "Ver horários",
                List.of(new Opcao("h1", "qui, 08/10 às 09:00", "com Dra. Ana"), new Opcao("menu:recepcao", "Falar com a recepção"))));
        assertThat(json.toString()).isEqualTo("{\"type\":\"list\",\"body\":{\"text\":\"Escolha um horário\"},"
                + "\"action\":{\"button\":\"Ver horários\",\"sections\":[{\"title\":\"Opções\",\"rows\":["
                + "{\"id\":\"h1\",\"title\":\"qui, 08/10 às 09:00\",\"description\":\"com Dra. Ana\"},"
                + "{\"id\":\"menu:recepcao\",\"title\":\"Falar com a recepção\"}]}]}}");
    }

    @Test
    void cortaTitulosNosLimitesDaMeta() {
        var lista = new MensagemSaida.Lista("x", "Um botão com texto grande demais",
                List.of(new Opcao("p1", "Maria Aparecida dos Santos Silva", "d".repeat(100))));
        assertThat(lista.botao()).hasLength(20);
        assertThat(lista.itens().get(0).titulo()).hasLength(24);
        assertThat(lista.itens().get(0).titulo()).endsWith("…");
        assertThat(lista.itens().get(0).descricao()).hasLength(72);
    }
}
