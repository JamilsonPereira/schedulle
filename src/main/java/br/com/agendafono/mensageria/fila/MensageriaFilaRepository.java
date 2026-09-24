package br.com.agendafono.mensageria.fila;

import br.com.agendafono.mensageria.webhook.MensagemWebhook;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MensageriaFilaRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public MensageriaFilaRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<UUID> buscarClinicaPorPhoneNumberId(String phoneNumberId) {
        return jdbc.sql("select id from clinica where whatsapp_phone_number_id = :phoneNumberId")
                .param("phoneNumberId", phoneNumberId)
                .query(UUID.class)
                .optional();
    }

    public boolean inserirEntrada(UUID clinicaId, MensagemWebhook mensagem, byte[] rawBody) {
        int inserted = jdbc.sql("""
                insert into evento_entrada (wamid, clinica_id, telefone, phone_number_id, tipo, texto, payload)
                values (:wamid, :clinicaId, :telefone, :phoneNumberId, :tipo, :texto, cast(:payload as jsonb))
                on conflict (wamid) do nothing
                """)
                .param("wamid", mensagem.wamid())
                .param("clinicaId", clinicaId)
                .param("telefone", mensagem.telefone())
                .param("phoneNumberId", mensagem.phoneNumberId())
                .param("tipo", mensagem.tipo())
                .param("texto", mensagem.texto())
                .param("payload", new String(rawBody, StandardCharsets.UTF_8))
                .update();
        return inserted == 1;
    }

    public Optional<EventoEntrada> proximaEntrada() {
        return jdbc.sql("""
                update evento_entrada
                   set status = 'PROCESSANDO',
                       tentativas = tentativas + 1
                 where id = (
                       select id
                         from evento_entrada
                        where status = 'PENDENTE'
                          and proxima_tentativa_em <= now()
                        order by recebido_em
                        for update skip locked
                        limit 1
                 )
                returning id, clinica_id, wamid, phone_number_id, telefone, tipo, texto
                """)
                .query((rs, rowNum) -> new EventoEntrada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("clinica_id", UUID.class),
                        rs.getString("wamid"),
                        rs.getString("phone_number_id"),
                        rs.getString("telefone"),
                        rs.getString("tipo"),
                        rs.getString("texto")))
                .optional();
    }

    public void registrarMensagemEntrada(EventoEntrada evento) {
        jdbc.sql("""
                insert into mensagem (clinica_id, telefone, direcao, tipo, texto, wamid)
                values (:clinicaId, :telefone, 'ENTRADA', :tipo, :texto, :wamid)
                """)
                .param("clinicaId", evento.clinicaId())
                .param("telefone", evento.telefone())
                .param("tipo", evento.tipo())
                .param("texto", evento.texto())
                .param("wamid", evento.wamid())
                .update();
    }

    public void criarSaidaTexto(UUID clinicaId, String phoneNumberId, String telefone, String texto) {
        jdbc.sql("""
                insert into evento_saida (clinica_id, phone_number_id, telefone, tipo, texto, payload)
                values (:clinicaId, :phoneNumberId, :telefone, 'TEXT', :texto, cast(:payload as jsonb))
                """)
                .param("clinicaId", clinicaId)
                .param("phoneNumberId", phoneNumberId)
                .param("telefone", telefone)
                .param("texto", texto)
                .param("payload", jsonPayloadTexto(telefone, texto))
                .update();

        jdbc.sql("""
                insert into mensagem (clinica_id, telefone, direcao, tipo, texto)
                values (:clinicaId, :telefone, 'SAIDA', 'TEXT', :texto)
                """)
                .param("clinicaId", clinicaId)
                .param("telefone", telefone)
                .param("texto", texto)
                .update();
    }

    public void marcarEntradaProcessada(UUID id) {
        jdbc.sql("""
                update evento_entrada
                   set status = 'PROCESSADO',
                       processado_em = now(),
                       erro = null
                 where id = :id
                """)
                .param("id", id)
                .update();
    }

    public void marcarEntradaComErro(UUID id, String erro) {
        jdbc.sql("""
                update evento_entrada
                   set status = case when tentativas >= 5 then 'MORTO' else 'PENDENTE' end,
                       proxima_tentativa_em = now() + cast(:delay as interval),
                       erro = :erro
                 where id = :id
                """)
                .param("id", id)
                .param("delay", "1 minute")
                .param("erro", limitarErro(erro))
                .update();
    }

    public Optional<EventoSaida> proximaSaida() {
        return jdbc.sql("""
                update evento_saida
                   set status = 'ENVIANDO',
                       tentativas = tentativas + 1
                 where id = (
                       select id
                         from evento_saida
                        where status = 'PENDENTE'
                          and proxima_tentativa_em <= now()
                        order by criado_em
                        for update skip locked
                        limit 1
                 )
                returning id, clinica_id, phone_number_id, telefone, texto
                """)
                .query((rs, rowNum) -> new EventoSaida(
                        rs.getObject("id", UUID.class),
                        rs.getObject("clinica_id", UUID.class),
                        rs.getString("phone_number_id"),
                        rs.getString("telefone"),
                        rs.getString("texto")))
                .optional();
    }

    public void marcarSaidaEnviada(UUID id) {
        jdbc.sql("update evento_saida set status = 'ENVIADO', enviado_em = now(), erro = null where id = :id")
                .param("id", id)
                .update();
    }

    public void marcarSaidaComErro(UUID id, String erro) {
        jdbc.sql("""
                update evento_saida
                   set status = case when tentativas >= 5 then 'MORTO' else 'PENDENTE' end,
                       proxima_tentativa_em = now() + cast(:delay as interval),
                       erro = :erro
                 where id = :id
                """)
                .param("id", id)
                .param("delay", "1 minute")
                .param("erro", limitarErro(erro))
                .update();
    }

    private String jsonPayloadTexto(String telefone, String texto) {
        try {
            return objectMapper.writeValueAsString(new TextoPayload(telefone, texto));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar payload de saida", e);
        }
    }

    private static String limitarErro(String erro) {
        if (erro == null) {
            return null;
        }
        return erro.length() <= 500 ? erro : erro.substring(0, 500);
    }

    private record TextoPayload(String to, String text) {
    }
}
