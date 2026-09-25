package br.com.agendafono.compartilhado.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * PROVISÓRIO até o Passo 2 (autenticação): lê a clínica do header {@code X-Clinica-Id}.
 * No Passo 2 a clínica passa a vir do claim {@code cid} do JWT e este header deixa de ser aceito.
 * Não exponha a API publicamente enquanto este resolver estiver ativo.
 */
class ClinicaIdArgumentResolver implements HandlerMethodArgumentResolver {

    static final String HEADER = "X-Clinica-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return ClinicaId.class.equals(parameter.getParameterType());
    }

    @Override
    public ClinicaId resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                     NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String valor = webRequest.getHeader(HEADER);
        if (valor == null || valor.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Header " + HEADER + " ausente");
        }
        try {
            return new ClinicaId(UUID.fromString(valor.trim()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Header " + HEADER + " inválido");
        }
    }
}
