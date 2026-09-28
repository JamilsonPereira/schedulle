package br.com.agendafono;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/** Base dos testes de integração: um único contexto Spring e um único Postgres para todas as classes. */
@SpringBootTest(properties = "pacientes.diretorio-anexos=${java.io.tmpdir}/agenda-fono-testes/anexos")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegracaoBase {
}
