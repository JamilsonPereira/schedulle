package br.com.agendafono.clinica.adapter.out.seguranca;

import br.com.agendafono.clinica.application.port.EmissorDeTokens;
import br.com.agendafono.clinica.domain.Usuario;
import br.com.agendafono.compartilhado.seguranca.ClaimsJwt;
import br.com.agendafono.compartilhado.seguranca.ConfiguracaoSeguranca.ChavesJwt;
import br.com.agendafono.compartilhado.seguranca.Papel;
import br.com.agendafono.compartilhado.seguranca.SegurancaProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/** JWT RS256 com sub, cid, roles, pid, jti. Sem nome, e-mail ou qualquer dado pessoal. */
@Component
class EmissorJwt implements EmissorDeTokens {

    private final JwtEncoder encoder;
    private final SegurancaProperties propriedades;
    private final String kid;

    EmissorJwt(JwtEncoder encoder, SegurancaProperties propriedades, ChavesJwt chaves) {
        this.encoder = encoder;
        this.propriedades = propriedades;
        this.kid = chaves.kid();
    }

    @Override
    public TokenDeAcesso emitir(Usuario usuario, UUID profissionalId, Instant agora) {
        Instant expira = agora.plus(propriedades.validadeAcesso());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(propriedades.emissor())
                .subject(usuario.id().toString())
                .issuedAt(agora)
                .expiresAt(expira)
                .id(UUID.randomUUID().toString())
                .claim(ClaimsJwt.CLINICA, usuario.clinicaId().toString())
                .claim(ClaimsJwt.PAPEIS, usuario.papeis().stream().map(Papel::name).sorted().toList());
        if (profissionalId != null) {
            claims.claim(ClaimsJwt.PROFISSIONAL, profissionalId.toString());
        }
        JwsHeader cabecalho = JwsHeader.with(SignatureAlgorithm.RS256).keyId(kid).build();
        String valor = encoder.encode(JwtEncoderParameters.from(cabecalho, claims.build())).getTokenValue();
        return new TokenDeAcesso(valor, expira);
    }
}
