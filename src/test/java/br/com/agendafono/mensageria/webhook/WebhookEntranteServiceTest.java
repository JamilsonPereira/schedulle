package br.com.agendafono.mensageria.webhook;

import br.com.agendafono.mensageria.fila.MensageriaFilaRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class WebhookEntranteServiceTest {

    private final WhatsAppWebhookParser parser = mock(WhatsAppWebhookParser.class);
    private final MensageriaFilaRepository repository = mock(MensageriaFilaRepository.class);
    private final WebhookEntranteService service = new WebhookEntranteService(parser, repository);

    @Test
    void contaInseridasDuplicadasEDesconhecidas() throws Exception {
        byte[] rawBody = "{}".getBytes(StandardCharsets.UTF_8);
        UUID clinicaId = UUID.randomUUID();
        MensagemWebhook nova = new MensagemWebhook("wamid.1", "phone-1", "5511999999999", "TEXT", "Oi");
        MensagemWebhook duplicada = new MensagemWebhook("wamid.2", "phone-1", "5511888888888", "TEXT", "Oi");
        MensagemWebhook desconhecida = new MensagemWebhook("wamid.3", "phone-x", "5511777777777", "TEXT", "Oi");

        when(parser.extrairMensagens(rawBody)).thenReturn(List.of(nova, duplicada, desconhecida));
        when(repository.buscarClinicaPorPhoneNumberId("phone-1")).thenReturn(Optional.of(clinicaId));
        when(repository.buscarClinicaPorPhoneNumberId("phone-x")).thenReturn(Optional.empty());
        when(repository.inserirEntrada(clinicaId, nova, rawBody)).thenReturn(true);
        when(repository.inserirEntrada(clinicaId, duplicada, rawBody)).thenReturn(false);

        WebhookEntranteService.ResultadoEnfileiramento resultado = service.enfileirar(rawBody);

        assertThat(resultado.inseridas()).isEqualTo(1);
        assertThat(resultado.duplicadas()).isEqualTo(1);
        assertThat(resultado.desconhecidas()).isEqualTo(1);
        verify(repository, times(2)).buscarClinicaPorPhoneNumberId("phone-1");
        verify(repository).buscarClinicaPorPhoneNumberId("phone-x");
        verify(repository).inserirEntrada(clinicaId, nova, rawBody);
        verify(repository).inserirEntrada(clinicaId, duplicada, rawBody);
        verifyNoMoreInteractions(repository);
    }
}
