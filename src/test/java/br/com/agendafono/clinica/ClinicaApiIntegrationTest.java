package br.com.agendafono.clinica;

import br.com.agendafono.IntegracaoBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Passo 2 de ponta a ponta: onboarding, login, troca de senha, refresh com rotação, bloqueio por tentativas,
 * cadastros da clínica e regras de perfil.
 */
class ClinicaApiIntegrationTest extends IntegracaoBase {

    private static final String SENHA = "Caneca-Azul-42";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    /** Token de acesso do ADMIN da clínica criada no {@link #setUp()}, já com senha definitiva. */
    String admin;
    String clinicaId;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("TRUNCATE clinica CASCADE");
        String[] criada = onboarding("Clínica Falar Bem", "dona@falarbem.com.br");
        clinicaId = criada[0];
        admin = "Bearer " + trocarSenhaInicial("dona@falarbem.com.br", criada[1]);
    }

    // ------------------------------------------------------------------ auxiliares

    private ResultActions json(MockHttpServletRequestBuilder req, String bearer, String corpo) throws Exception {
        if (bearer != null) {
            req.header("Authorization", bearer);
        }
        if (corpo != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(req);
    }

    private static String ler(ResultActions r, String caminho) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), caminho).toString();
    }

    /** Devolve {clinicaId, senhaTemporaria}. */
    private String[] onboarding(String nome, String email) throws Exception {
        ResultActions r = json(post("/api/v1/plataforma/clinicas").header("X-Plataforma-Token", TOKEN_PLATAFORMA),
                null, """
                        {"nomeClinica": "%s", "nomeAdministrador": "Dona da Clínica", "emailAdministrador": "%s"}
                        """.formatted(nome, email))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.administrador.papeis[0]").value("ADMIN"))
                .andExpect(jsonPath("$.administrador.precisaTrocarSenha").value(true));
        return new String[]{ler(r, "$.clinica.id"), ler(r, "$.senhaTemporaria")};
    }

    private ResultActions login(String email, String senha) throws Exception {
        return json(post("/api/v1/auth/login"), null, """
                {"email": "%s", "senha": "%s"}""".formatted(email, senha));
    }

    /** Primeiro acesso: entra com a temporária e define {@link #SENHA}. Devolve o token de acesso novo. */
    private String trocarSenhaInicial(String email, String temporaria) throws Exception {
        String acesso = ler(login(email, temporaria).andExpect(status().isOk()), "$.tokenAcesso");
        return ler(json(post("/api/v1/auth/senha"), "Bearer " + acesso, """
                {"senhaAtual": "%s", "novaSenha": "%s"}""".formatted(temporaria, SENHA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.precisaTrocarSenha").value(false)), "$.tokenAcesso");
    }

    /** Cria um usuário pelo ADMIN e já troca a senha dele. Devolve {usuarioId, tokenDeAcesso}. */
    private String[] usuario(String email, String papeis) throws Exception {
        ResultActions r = json(post("/api/v1/usuarios"), admin, """
                {"nome": "Fulana", "email": "%s", "papeis": [%s]}""".formatted(email, papeis))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"));
        String token = trocarSenhaInicial(email, ler(r, "$.senhaTemporaria"));
        return new String[]{ler(r, "$.usuario.id"), "Bearer " + token};
    }

    private String profissional(String usuarioId) throws Exception {
        String vinculo = usuarioId == null ? "null" : "\"" + usuarioId + "\"";
        return ler(json(post("/api/v1/profissionais"), admin, """
                {"nome": "Dra. Paula", "registroCrfa": "CRFa 2-12345", "subareas": ["LINGUAGEM"],
                 "duracaoPadraoMin": 40, "usuarioId": %s}""".formatted(vinculo))
                .andExpect(status().isCreated()), "$.id");
    }

    // ------------------------------------------------------------------ autenticação

    @Test
    void onboardingExigeTokenDePlataforma() throws Exception {
        json(post("/api/v1/plataforma/clinicas").header("X-Plataforma-Token", "errado"), null, """
                {"nomeClinica": "X", "nomeAdministrador": "Y", "emailAdministrador": "y@x.com"}""")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meDevolveUsuarioEClinicaSemDadosSensiveis() throws Exception {
        json(get("/api/v1/auth/me"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.email").value("dona@falarbem.com.br"))
                .andExpect(jsonPath("$.usuario.senhaHash").doesNotExist())
                .andExpect(jsonPath("$.clinica.id").value(clinicaId))
                .andExpect(jsonPath("$.clinica.fuso").value("America/Sao_Paulo"));
    }

    @Test
    void senhaErradaEEmailInexistenteDaoOMesmo401() throws Exception {
        login("dona@falarbem.com.br", "errada-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/erros/credenciais-invalidas"));
        login("ninguem@falarbem.com.br", "errada-errada")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/erros/credenciais-invalidas"));
    }

    @Test
    void cincoFalhasBloqueiamAteMesmoASenhaCerta() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("dona@falarbem.com.br", "errada-errada").andExpect(status().isUnauthorized());
        }
        login("dona@falarbem.com.br", SENHA).andExpect(status().isUnauthorized());
        Integer auditadas = jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE acao = 'LOGIN_FALHOU'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(auditadas).isGreaterThanOrEqualTo(5);
    }

    @Test
    void refreshRotacionaEReusoDerrubaAFamilia() throws Exception {
        String r1 = ler(login("dona@falarbem.com.br", SENHA).andExpect(status().isOk()), "$.refreshToken");

        String r2 = ler(json(post("/api/v1/auth/refresh"), null, "{\"refreshToken\": \"" + r1 + "\"}")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tokenAcesso", not(emptyOrNullString()))), "$.refreshToken");

        // r1 de novo = provável roubo: recusa e revoga também o r2
        json(post("/api/v1/auth/refresh"), null, "{\"refreshToken\": \"" + r1 + "\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value("/erros/sessao-expirada"));
        json(post("/api/v1/auth/refresh"), null, "{\"refreshToken\": \"" + r2 + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutInvalidaORefresh() throws Exception {
        String r1 = ler(login("dona@falarbem.com.br", SENHA).andExpect(status().isOk()), "$.refreshToken");
        json(post("/api/v1/auth/logout"), null, "{\"refreshToken\": \"" + r1 + "\"}")
                .andExpect(status().isNoContent());
        json(post("/api/v1/auth/refresh"), null, "{\"refreshToken\": \"" + r1 + "\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void senhaFracaERecusada() throws Exception {
        json(post("/api/v1/auth/senha"), admin, """
                {"senhaAtual": "%s", "novaSenha": "1234567890"}""".formatted(SENHA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("/erros/senha-fraca"));
    }

    @Test
    void tokenAdulteradoRetorna401() throws Exception {
        json(get("/api/v1/clinica"), admin.substring(0, admin.length() - 4) + "AAAA", null)
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ usuários

    @Test
    void emailDuplicadoRetorna409() throws Exception {
        json(post("/api/v1/usuarios"), admin, """
                {"nome": "Outra", "email": "DONA@falarbem.com.br", "papeis": ["RECEPCAO"]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/erros/email-ja-cadastrado"));
    }

    @Test
    void recepcaoNaoGerenciaUsuarios() throws Exception {
        String recepcao = usuario("recepcao@falarbem.com.br", "\"RECEPCAO\"")[1];
        json(get("/api/v1/usuarios"), recepcao, null).andExpect(status().isForbidden());
        json(put("/api/v1/clinica"), recepcao, """
                {"nome": "Nova", "fuso": "America/Sao_Paulo"}""").andExpect(status().isForbidden());
    }

    @Test
    void adminNaoRemoveOProprioAcesso() throws Exception {
        String meuId = ler(json(get("/api/v1/auth/me"), admin, null), "$.usuario.id");
        json(put("/api/v1/usuarios/{id}", meuId), admin, """
                {"nome": "Dona", "papeis": ["RECEPCAO"], "ativo": true}""")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("/erros/operacao-nao-permitida"));
    }

    @Test
    void usuarioInativadoNaoConsegueEntrar() throws Exception {
        String id = usuario("sai@falarbem.com.br", "\"RECEPCAO\"")[0];
        json(put("/api/v1/usuarios/{id}", id), admin, """
                {"nome": "Fulana", "papeis": ["RECEPCAO"], "ativo": false}""").andExpect(status().isOk());
        login("sai@falarbem.com.br", SENHA).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ cadastros

    @Test
    void salaComNomeRepetidoRetorna409() throws Exception {
        json(post("/api/v1/recursos"), admin, "{\"nome\": \"Sala 1\", \"tipo\": \"SALA\"}")
                .andExpect(status().isCreated());
        json(post("/api/v1/recursos"), admin, "{\"nome\": \"Sala 1\", \"tipo\": \"CABINE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/erros/nome-duplicado"));
    }

    @Test
    void politicaInvalidaRetorna400EVersaoVelha409() throws Exception {
        json(put("/api/v1/clinica/politica"), admin, "{\"ttlReservaMin\": 90}")
                .andExpect(status().isBadRequest());
        json(put("/api/v1/clinica/politica"), admin, "{\"passoMin\": 20, \"versao\": 0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.politica.passoMin").value(20))
                .andExpect(jsonPath("$.versao").value(1));
        json(put("/api/v1/clinica/politica"), admin, "{\"passoMin\": 30, \"versao\": 0}")
                .andExpect(status().isConflict());
    }

    @Test
    void gradeSobrepostaRetorna409EGradeValidaAbreADisponibilidade() throws Exception {
        String sala = ler(json(post("/api/v1/recursos"), admin, "{\"nome\": \"Sala 1\", \"tipo\": \"SALA\"}"),
                "$.id");
        String fono = profissional(null);

        json(put("/api/v1/profissionais/{id}/grade", fono), admin, """
                {"intervalos": [{"dia": "MONDAY", "inicio": "08:00", "fim": "12:00"},
                                {"dia": "MONDAY", "inicio": "11:00", "fim": "13:00"}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("/erros/grade-sobreposta"));

        json(put("/api/v1/profissionais/{id}/grade", fono), admin, """
                {"intervalos": [{"dia": "MONDAY", "inicio": "08:00", "fim": "12:00", "recursoId": "%s"}]}"""
                .formatted(sala))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grade", hasSize(1)));

        // A agenda enxerga a grade cadastrada aqui: 08:00 a 12:00 em sessões de 40 min = 6 horários
        LocalDate segunda = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(3)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        json(get("/api/v1/disponibilidade").param("profissionalId", fono)
                .param("de", segunda.toString()).param("ate", segunda.toString()), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    void outraClinicaNaoEnxergaOProfissional() throws Exception {
        String fono = profissional(null);
        String[] outra = onboarding("Outra Clínica", "dono@outra.com.br");
        String adminOutra = "Bearer " + trocarSenhaInicial("dono@outra.com.br", outra[1]);

        json(get("/api/v1/profissionais/{id}", fono), adminOutra, null).andExpect(status().isNotFound());
        json(get("/api/v1/profissionais"), adminOutra, null).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void usuarioVinculadoPrecisaTerPerfilFono() throws Exception {
        String recepcaoId = usuario("recepcao@falarbem.com.br", "\"RECEPCAO\"")[0];
        json(post("/api/v1/profissionais"), admin, """
                {"nome": "Dra. Paula", "subareas": ["LINGUAGEM"], "usuarioId": "%s"}""".formatted(recepcaoId))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fonoSoBloqueiaAPropriaAgenda() throws Exception {
        String[] fonoUsuario = usuario("paula@falarbem.com.br", "\"FONO\"");
        String minhaAgenda = profissional(fonoUsuario[0]);
        String outraAgenda = profissional(null);
        // O token do fono foi emitido antes do vínculo; um login novo traz o profissionalId (claim pid)
        String fono = "Bearer " + ler(login("paula@falarbem.com.br", SENHA), "$.tokenAcesso");

        String corpo = """
                {"profissionalId": "%s", "inicio": "2030-01-10T11:00:00Z", "fim": "2030-01-20T23:00:00Z",
                 "motivo": "Férias"}""";
        json(post("/api/v1/bloqueios"), fono, corpo.formatted(outraAgenda)).andExpect(status().isForbidden());
        String bloqueio = ler(json(post("/api/v1/bloqueios"), fono, corpo.formatted(minhaAgenda))
                .andExpect(status().isCreated()), "$.id");

        json(get("/api/v1/bloqueios").param("de", "2030-01-01T00:00:00Z").param("ate", "2030-02-01T00:00:00Z")
                .param("profissionalId", minhaAgenda), fono, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        json(delete("/api/v1/bloqueios/{id}", bloqueio), fono, null).andExpect(status().isNoContent());
    }

    @Test
    void semTokenRetorna401() throws Exception {
        json(get("/api/v1/profissionais"), null, null).andExpect(status().isUnauthorized());
    }
}
