package br.com.agendafono.compartilhado.seguranca;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Segurança da API (ADR-08): JWT RS256 de 15 min emitido pela própria aplicação, validado como resource
 * server; perfis viram authorities {@code ROLE_*}; sem sessão HTTP e sem CSRF (o painel fala via BFF).
 *
 * <p>Implementa {@link WebMvcConfigurer} para registrar os resolvers de identidade; isso também faz os
 * testes {@code @WebMvcTest} carregarem esta configuração em vez da segurança padrão do Spring Boot.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SegurancaProperties.class)
public class ConfiguracaoSeguranca implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoSeguranca.class);

    /** Rotas que não exigem token. Webhook e onboarding têm autenticação própria (assinatura / segredo). */
    private static final String[] PUBLICAS_POST = {
            "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
            "/webhook/whatsapp", "/api/v1/plataforma/**"};
    private static final String[] PUBLICAS_GET = {"/webhook/whatsapp", "/actuator/health/**", "/actuator/info"};

    @Bean
    SecurityFilterChain filtroDeSeguranca(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter papeis = new JwtGrantedAuthoritiesConverter();
        papeis.setAuthoritiesClaimName(ClaimsJwt.PAPEIS);
        papeis.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(papeis);

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, PUBLICAS_POST).permitAll()
                        .requestMatchers(HttpMethod.GET, PUBLICAS_GET).permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conversor)));
        return http.build();
    }

    @Bean
    ChavesJwt chavesJwt(SegurancaProperties propriedades) {
        return ChavesJwt.de(propriedades);
    }

    @Bean
    JwtEncoder jwtEncoder(ChavesJwt chaves) {
        RSAKey jwk = new RSAKey.Builder(chaves.publica()).privateKey(chaves.privada()).keyID(chaves.kid()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(ChavesJwt chaves, SegurancaProperties propriedades) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(chaves.publica()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(propriedades.emissor()));
        return decoder;
    }

    /** BCrypt por padrão, com prefixo {@code {bcrypt}} para permitir trocar o algoritmo depois sem migração. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new IdentidadeArgumentResolver());
    }

    /** Par de chaves RSA usado para assinar e validar os tokens. */
    public record ChavesJwt(RSAPublicKey publica, RSAPrivateKey privada, String kid) {

        static ChavesJwt de(SegurancaProperties p) {
            boolean temChaves = p.chavePrivadaPem() != null && !p.chavePrivadaPem().isBlank()
                    && p.chavePublicaPem() != null && !p.chavePublicaPem().isBlank();
            try {
                if (temChaves) {
                    KeyFactory rsa = KeyFactory.getInstance("RSA");
                    RSAPrivateKey privada = (RSAPrivateKey) rsa.generatePrivate(
                            new PKCS8EncodedKeySpec(pem(p.chavePrivadaPem())));
                    RSAPublicKey publica = (RSAPublicKey) rsa.generatePublic(
                            new X509EncodedKeySpec(pem(p.chavePublicaPem())));
                    return new ChavesJwt(publica, privada, "agenda-fono-1");
                }
                log.warn("seguranca.chave-privada-pem não configurada: gerando par RSA temporário. "
                        + "Tokens deixam de valer a cada reinício. Não use assim em produção.");
                KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
                gerador.initialize(2048);
                KeyPair par = gerador.generateKeyPair();
                return new ChavesJwt((RSAPublicKey) par.getPublic(), (RSAPrivateKey) par.getPrivate(), "dev");
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                throw new IllegalStateException("Chaves JWT inválidas", e);
            }
        }

        private static byte[] pem(String texto) {
            String base64 = texto.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
            return Base64.getDecoder().decode(base64);
        }
    }
}
