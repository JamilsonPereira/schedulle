package br.com.agendafono;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Postgres real para os testes de integração. Como bean, o container vive junto com o contexto do Spring
 * e é compartilhado por todas as classes que usam o mesmo contexto (veja {@link IntegracaoBase}).
 * Requer Docker rodando na máquina.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16-alpine");
    }
}
