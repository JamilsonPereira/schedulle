package br.com.agendafono.compartilhado.seguranca;

import br.com.agendafono.compartilhado.web.ClinicaId;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Resolve {@link ClinicaId} e {@link UsuarioAutenticado} a partir do JWT validado pelo Spring Security.
 * A clínica vem sempre do token, nunca da URL nem de header: é isso que impede acesso entre clínicas.
 */
class IdentidadeArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        Class<?> tipo = parameter.getParameterType();
        return ClinicaId.class.equals(tipo) || UsuarioAutenticado.class.equals(tipo);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        UsuarioAutenticado usuario = usuarioAtual();
        return ClinicaId.class.equals(parameter.getParameterType()) ? new ClinicaId(usuario.clinicaId()) : usuario;
    }

    static UsuarioAutenticado usuarioAtual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new InsufficientAuthenticationException("Autenticação necessária");
        }
        String clinica = jwt.getClaimAsString(ClaimsJwt.CLINICA);
        if (clinica == null || jwt.getSubject() == null) {
            throw new InsufficientAuthenticationException("Token sem identificação da clínica");
        }
        String profissional = jwt.getClaimAsString(ClaimsJwt.PROFISSIONAL);
        Set<Papel> papeis = EnumSet.noneOf(Papel.class);
        List<String> nomes = jwt.getClaimAsStringList(ClaimsJwt.PAPEIS);
        if (nomes != null) {
            for (String nome : nomes) {
                try {
                    papeis.add(Papel.valueOf(nome));
                } catch (IllegalArgumentException ignorado) {
                    // papel desconhecido é descartado
                }
            }
        }
        return new UsuarioAutenticado(UUID.fromString(jwt.getSubject()), UUID.fromString(clinica),
                profissional != null ? UUID.fromString(profissional) : null, papeis);
    }
}
