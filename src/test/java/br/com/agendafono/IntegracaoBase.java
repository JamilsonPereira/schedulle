package br.com.agendafono;

import br.com.agendafono.compartilhado.seguranca.ClaimsJwt;
import br.com.agendafono.compartilhado.seguranca.ConfiguracaoSeguranca.ChavesJwt;
import br.com.agendafono.compartilhado.seguranca.Papel;
import br.com.agendafono.compartilhado.seguranca.SegurancaProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** Base dos testes de integração: um único contexto Spring e um único Postgres para todas as classes. */
@SpringBootTest(properties = {
        "pacientes.diretorio-anexos=${java.io.tmpdir}/agenda-fono-testes/anexos",
        "seguranca.token-plataforma=token-plataforma-de-teste"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegracaoBase {

    protected static final String TOKEN_PLATAFORMA = "token-plataforma-de-teste";

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private ChavesJwt chaves;

    @Autowired
    private SegurancaProperties seguranca;

    /** Header {@code Authorization} com um JWT válido de um usuário fictício da clínica. */
    protected String bearer(UUID clinicaId, Papel... papeis) {
        return bearer(clinicaId, UUID.randomUUID(), null, papeis);
    }

    protected String bearer(UUID clinicaId, UUID usuarioId, UUID profissionalId, Papel... papeis) {
        Instant agora = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(seguranca.emissor())
                .subject(usuarioId.toString())
                .issuedAt(agora)
                .expiresAt(agora.plus(Duration.ofMinutes(5)))
                .id(UUID.randomUUID().toString())
                .claim(ClaimsJwt.CLINICA, clinicaId.toString())
                .claim(ClaimsJwt.PAPEIS, Arrays.stream(papeis).map(Papel::name).toList());
        if (profissionalId != null) {
            claims.claim(ClaimsJwt.PROFISSIONAL, profissionalId.toString());
        }
        JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).keyId(chaves.kid()).build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(cabecalho, claims.build())).getTokenValue();
    }

    /** Atalho: adiciona o header de autenticação à requisição. */
    protected static MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder req, String bearer) {
        return req.header(HttpHeaders.AUTHORIZATION, bearer);
    }
}
