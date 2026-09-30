package br.com.agendafono.clinica.adapter.in.web;

import br.com.agendafono.clinica.adapter.in.web.ClinicaDtos.NovaClinicaRequest;
import br.com.agendafono.clinica.application.PlataformaService;
import br.com.agendafono.clinica.application.PlataformaService.ClinicaCriada;
import br.com.agendafono.compartilhado.seguranca.SegurancaProperties;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Onboarding interno: cria uma clínica e o primeiro ADMIN. Protegido por um token de plataforma
 * ({@code PLATAFORMA_TOKEN}); sem o token configurado, o endpoint responde 404 como se não existisse.
 */
@RestController
@RequestMapping("/api/v1/plataforma")
class PlataformaController {

    static final String HEADER = "X-Plataforma-Token";
    private static final String FUSO_PADRAO = "America/Sao_Paulo";

    private final PlataformaService plataforma;
    private final byte[] tokenEsperado;

    PlataformaController(PlataformaService plataforma, SegurancaProperties propriedades) {
        this.plataforma = plataforma;
        String token = propriedades.tokenPlataforma();
        this.tokenEsperado = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/clinicas")
    ResponseEntity<ClinicaCriada> criarClinica(@RequestHeader(value = HEADER, required = false) String token,
                                               @Valid @RequestBody NovaClinicaRequest r) {
        exigirToken(token);
        String fuso = r.fuso() == null || r.fuso().isBlank() ? FUSO_PADRAO : r.fuso();
        ClinicaCriada criada = plataforma.criarClinica(r.nomeClinica(), fuso, r.nomeAdministrador(),
                r.emailAdministrador());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(criada);
    }

    private void exigirToken(String informado) {
        if (tokenEsperado == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        byte[] recebido = informado == null ? new byte[0] : informado.getBytes(StandardCharsets.UTF_8);
        // Comparação em tempo constante: não revela quantos caracteres do token estão certos.
        if (!MessageDigest.isEqual(tokenEsperado, recebido)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
