package br.com.agendafono.compartilhado.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O Spring Boot 4 configura só o Jackson 3 ({@code tools.jackson}); o {@code ObjectMapper} do Jackson 2
 * ({@code com.fasterxml}) deixou de ser um bean automático. Este bean atende quem ainda usa Jackson 2
 * (a fila da mensageria). Não interfere nas respostas HTTP, que continuam com o Jackson 3.
 *
 * <p>Quando a mensageria migrar para {@code tools.jackson.databind.json.JsonMapper}, esta classe e as
 * dependências do Jackson 2 no pom podem sair.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoJackson2 {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }
}
