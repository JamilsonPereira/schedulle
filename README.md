# agenda-fono

SaaS de agendamento via WhatsApp para clínicas de fonoaudiologia.
Stack: Java 21, Spring Boot 4.1, Maven, PostgreSQL 16, Flyway, Testcontainers.

## Pré-requisitos

- JDK 21
- Maven 3.9+ (ou o Maven embutido do IntelliJ)
- Docker Desktop rodando (para o `docker compose` e para os testes com Testcontainers)

## Rodando localmente

O repositório tem duas partes: a API (Spring Boot, raiz) e o painel web em [`web/`](web/README.md) (Next.js).

```bash
docker compose up -d          # Postgres 16 + Redis
mvn spring-boot:run           # sobe a API em http://localhost:8080 e aplica as migrations
curl http://localhost:8080/actuator/health

cd web && npm install && npm run dev   # painel em http://localhost:3000 (veja web/README.md)
```

### Atalho para desenvolvimento: dados de teste com senha padrão

Suba com o perfil `dev` e a aplicação cria, na primeira vez, uma clínica de teste já pronta para usar:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| Login | Perfil |
| --- | --- |
| `admin@clinica.dev` | ADMIN |
| `recepcao@clinica.dev` | RECEPCAO |
| `fono@clinica.dev` | FONO (vinculado à "Dra. Teste", com grade de seg a sex, 08–12 e 13–18) |

Senha de todos: `Agenda-Dev-2026` (troque com a variável `DEV_SENHA_PADRAO`). Não pedem troca no primeiro acesso.
O perfil `dev` também liga o onboarding com `PLATAFORMA_TOKEN=dev-plataforma`. **Nunca ative o perfil `dev` em produção.**

Variáveis de ambiente: veja `.env.example`. Sem nada configurado, a aplicação usa valores de desenvolvimento.

## Testes

```bash
mvn verify
```

Os testes de integração sobem um Postgres via Testcontainers: **o Docker Desktop precisa estar aberto**.

- `SessaoConflitoIntegrationTest`: prova, num Postgres real (Testcontainers), que o banco barra
  duas sessões sobrepostas do mesmo fono ou da mesma sala (regra RN-03).
- `WebhookSignatureVerifierTest` e `WhatsAppWebhookControllerTest`: verificação do webhook
  e validação da assinatura `X-Hub-Signature-256`.

## Conectando o webhook da Meta (número de teste)

1. Em developers.facebook.com, crie um app do tipo Business e adicione o produto **WhatsApp**.
2. Copie o **App Secret** (Configurações do app > Básico) para `WHATSAPP_APP_SECRET`.
3. Invente um `WHATSAPP_VERIFY_TOKEN` e suba a aplicação.
4. Exponha a porta local: `ngrok http 8080`.
5. Em WhatsApp > Configuração, informe a URL `https://<seu-ngrok>/webhook/whatsapp` e o mesmo verify token.
6. Assine o campo `messages` e mande uma mensagem para o número de teste: o log mostra
   `Evento do webhook recebido`.

## Estrutura

Monólito modular com um pacote por módulo (fronteiras descritas em cada `package-info.java`):

```
br.com.agendafono
├── agenda        disponibilidade, sessões, reservas, faltas, presença
├── compartilhado relógio, segurança (JWT, perfis), auditoria
├── bot           máquina de estados da conversa
├── clinica       clínica, usuários e login, salas, profissionais, grade, bloqueios
├── mensageria    WhatsApp Cloud API (webhook, envio, templates)
└── pacientes     responsáveis, pacientes, consentimento, anexos, direitos do titular
```

## Roadmap em passos

- [x] **Passo 0 — Fundação**: projeto (Spring Boot 4.1), schema do MVP sem convênio (V1), constraint de conflito testada, webhook com verificação e assinatura, CI.
- [ ] **Passo 1 — Mensageria**: DTOs do payload da Meta, deduplicação por `wamid`, fila de eventos (outbox), cliente de envio (`RestClient`), bot "eco" funcionando no número de teste.
- [x] **Passo 2 — Clínica e autenticação**: login com JWT RS256 (15 min) + refresh token rotativo, perfis ADMIN/RECEPCAO/FONO, bloqueio após 5 tentativas, troca de senha, usuários, clínica e política, salas, profissionais e grade, bloqueios de agenda, onboarding de clínica, auditoria. A clínica passa a vir do token (fim do header `X-Clinica-Id`).
- [x] **Passo 3 — Disponibilidade e agendamento**: cálculo de slots (grade − bloqueios − sessões), pré-reserva de 5 min do bot, agendamento/remarcação/cancelamento pelo painel, aviso de falta, presença, job de expiração, API `/api/v1/disponibilidade` e `/api/v1/sessoes`.
- [x] **Passo 4 — Bot de agendamento** (parcial): consentimento LGPD, menu, paciente novo, escolha de horário com pré-reserva, confirmação da avaliação, transbordo para a recepção e reinício após 30 min, com botões e listas da Meta. Pendente: ligar o `Bot` no worker de entrada e o envio `interactive` no worker de saída da mensageria.
- [ ] **Passo 5 — Terapias recorrentes e lembretes**: séries, materialização de 8 semanas, templates de lembrete com botões Confirmo/Vou faltar.
- [ ] **Passo 6 — Faltas e transbordo**: aviso de falta, reposição, lista de espera e caixa de entrada da recepção.
- [x] **Passo 7 — Pacientes e anexos** (parcial): responsáveis, pacientes, consentimento LGPD com histórico, busca, ficha, anexos (armazenamento local; S3 no Passo 8), exportação e anonimização. Pendentes: auditoria de acesso e endpoint `/pendencias`. Convênio e guias ficam para depois do MVP.
- [x] **Painel web W0–W4** (`web/`): login com BFF e cookie httpOnly, permissões por perfil, configurações (clínica, política, salas, profissionais e grade, usuários), agenda dia/semana com arrastar e bloqueios, pacientes com ficha, anexos e LGPD. Pendentes: séries, inbox, métricas, SSE e OpenAPI.
- [ ] **Passo 8 — Produção**: RLS no Postgres, deploy AWS São Paulo, observabilidade com Grafana, checklist de segurança, piloto.

## Autenticação

Todas as rotas `/api/v1/**` exigem `Authorization: Bearer <tokenAcesso>`, exceto login, refresh, logout e o
onboarding de plataforma. A clínica e o perfil vêm do token; não há como acessar dados de outra clínica.

| Perfil | Pode |
| --- | --- |
| `ADMIN` | Tudo, inclusive usuários, dados da clínica, salas, profissionais, grade e direitos do titular (LGPD) |
| `RECEPCAO` | Agenda completa, pacientes, responsáveis, anexos e bloqueios |
| `FONO` | Só a própria agenda: vê as próprias sessões, registra atendimento/falta, cria e remove os próprios bloqueios; consulta pacientes |

| Método | Rota | Uso |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | `email`, `senha` → `tokenAcesso` (15 min), `refreshToken` (7 dias), `usuario` |
| POST | `/api/v1/auth/refresh` | `refreshToken` → par novo. Refresh já usado derruba a sessão inteira (proteção contra roubo) |
| POST | `/api/v1/auth/logout` | `refreshToken`, `todas?` |
| GET | `/api/v1/auth/me` | Usuário logado e clínica |
| POST | `/api/v1/auth/senha` | `senhaAtual`, `novaSenha` (mín. 10 caracteres) → derruba as outras sessões e devolve tokens novos |

Usuários novos recebem senha temporária e `precisaTrocarSenha = true`: o painel deve levar direto à troca de senha.
Cinco senhas erradas seguidas bloqueiam o login por 15 minutos.

### Primeira clínica (onboarding)

Defina `PLATAFORMA_TOKEN` e chame (sem o token configurado a rota responde 404):

```bash
curl -X POST http://localhost:8080/api/v1/plataforma/clinicas \
  -H "X-Plataforma-Token: $PLATAFORMA_TOKEN" -H "Content-Type: application/json" \
  -d '{"nomeClinica":"Clínica Falar Bem","nomeAdministrador":"Dona","emailAdministrador":"dona@falarbem.com.br"}'
```

A resposta traz a `senhaTemporaria` do ADMIN, uma única vez.

### Chaves do JWT

Sem `JWT_CHAVE_PRIVADA_PEM`/`JWT_CHAVE_PUBLICA_PEM`, a aplicação gera um par temporário a cada start (tokens
caem ao reiniciar). Para fixar as chaves:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-privada.pem   # PKCS#8
openssl rsa -in jwt-privada.pem -pubout -out jwt-publica.pem
```

Coloque o conteúdo dos arquivos nas variáveis (em produção, no Secrets Manager). Não versione as chaves.

## API da clínica

| Método | Rota | Uso |
| --- | --- | --- |
| GET · POST | `/api/v1/usuarios` | ADMIN. Criar: `nome`, `email`, `papeis` → devolve `senhaTemporaria` |
| PUT | `/api/v1/usuarios/{id}` | ADMIN. `nome`, `papeis`, `ativo`, `versao?`. Sempre fica ao menos um ADMIN ativo |
| POST | `/api/v1/usuarios/{id}/senha-temporaria` | ADMIN. Gera nova senha temporária e derruba as sessões do usuário |
| GET · PUT | `/api/v1/clinica` | Dados da clínica (`nome`, `fuso`, `versao?`); PUT só ADMIN |
| PUT | `/api/v1/clinica/politica` | ADMIN. `antecedenciaMinimaMin`, `janelaMaximaDias`, `passoMin`, `ttlReservaMin`, `antecedenciaAvisoFaltaHoras` (nulo = padrão) |
| GET · POST · PUT | `/api/v1/recursos[/{id}]` | Salas e cabines (`nome`, `tipo`: `SALA`/`CABINE`, `ativo`) |
| GET · POST · PUT | `/api/v1/profissionais[/{id}]` | `nome`, `registroCrfa`, `subareas`, `duracaoPadraoMin`, `usuarioId?` (login FONO vinculado) |
| PUT | `/api/v1/profissionais/{id}/grade` | ADMIN. `intervalos`: `[{dia: "MONDAY", inicio: "08:00", fim: "12:00", recursoId?}]` |
| POST | `/api/v1/profissionais/{id}/inativar` · `/reativar` | ADMIN |
| GET | `/api/v1/bloqueios?de=<ISO>&ate=<ISO>[&profissionalId=]` | Bloqueios do período (inclui os da clínica inteira) |
| POST · DELETE | `/api/v1/bloqueios[/{id}]` | `profissionalId?` (nulo = clínica inteira), `inicio`, `fim`, `motivo?` |

Erros: 401 `/erros/credenciais-invalidas` e `/erros/sessao-expirada`; 403 sem permissão; 409 `/erros/versao-desatualizada`,
`/erros/email-ja-cadastrado`, `/erros/nome-duplicado`, `/erros/grade-sobreposta`; 400 `/erros/senha-fraca`.

## API da agenda

| GET | `/api/v1/disponibilidade?profissionalId=&de=AAAA-MM-DD&ate=AAAA-MM-DD[&duracaoMin=]` | Horários livres |
| GET | `/api/v1/sessoes?de=<ISO>&ate=<ISO>[&profissionalId=]` | Sessões do período |
| GET | `/api/v1/agenda/sessoes?de=<ISO>&ate=<ISO>[&profissionalId=][&pacienteId=]` | Leitura do painel: sessões com `pacienteNome`; com `pacienteId`, o histórico do paciente (máx. 400 dias) |
| GET | `/api/v1/sessoes/{id}` | Uma sessão |
| POST | `/api/v1/sessoes` | Agendar (`pacienteId`, `profissionalId`, `tipo`, `inicio`, `duracaoMin?`, `recursoId?`, `permitirForaDaGrade?`) |
| POST | `/api/v1/sessoes/{id}/remarcar` | Remarcar (`novoInicio`, `versao?`) |
| PATCH | `/api/v1/sessoes/{id}/status` | `acao`: `CONFIRMAR_PRESENCA`, `CANCELAR`, `AVISAR_FALTA`, `REGISTRAR_ATENDIMENTO`, `REGISTRAR_FALTA_SEM_AVISO` |

Erros seguem RFC 9457 (Problem Details): 409 `/erros/horario-indisponivel` traz `alternativas`; 422 `/erros/horario-fora-da-agenda` traz `motivo`.

## API de pacientes

Telefones aceitam `(11) 99999-0000`, `11999990000` ou `+5511999990000`.

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/api/v1/pacientes?busca=&ativo=&pagina=0&tamanho=25` | Lista (telefone mascarado); `busca` = nome ou dígitos do telefone |
| POST | `/api/v1/pacientes` | Cadastro pelo painel (`telefoneResponsavel`, `nomeResponsavel?`, `nome`, `dataNascimento?`, `demanda?`, `consentimentoColetado`) |
| GET | `/api/v1/pacientes/{id}` | Ficha: paciente, responsável, outros pacientes do responsável, anexos |
| PUT | `/api/v1/pacientes/{id}` | Editar (`nome`, `dataNascimento`, `demanda`, `versao?`) |
| POST | `/api/v1/pacientes/{id}/inativar` · `/reativar` | Ativo/inativo |
| GET · POST | `/api/v1/pacientes/{id}/anexos` | Listar · enviar (multipart `arquivo`, `tipo`); JPEG, PNG ou PDF até 10 MB |
| GET · DELETE | `/api/v1/anexos/{id}/conteudo` · `/api/v1/anexos/{id}` | Baixar · remover |
| GET · PUT | `/api/v1/responsaveis/{id}` | Ver · editar (`nome`, `telefone`, `versao?`) |
| POST · DELETE | `/api/v1/responsaveis/{id}/consentimento` | Registrar (presencial) · revogar |
| GET | `/api/v1/responsaveis/{id}/exportacao` | LGPD: dados do titular em JSON |
| POST | `/api/v1/responsaveis/{id}/anonimizacao` | LGPD: anonimização irreversível |

Regra central: nenhum paciente é cadastrado sem consentimento do responsável (LGPD arts. 11 e 14).

## Bot de agendamento (Passo 4)

Máquina de estados determinística no módulo `bot` (sem IA). Cada mensagem recebida passa por `Bot.processar`; a etapa do estado atual decide a resposta, a conversa é gravada em `conversa` e as respostas vão para `evento_saida` na mesma transação (outbox).

```
INICIO → CONSENTIMENTO → MENU → PARA_QUEM → (NOVO_NOME → NOVO_NASCIMENTO → NOVA_DEMANDA) → ESCOLHER_HORARIO → CONFIRMAR → MENU
                                    qualquer etapa → HUMANO (recepção)
```

- **Consentimento**: botões Aceito / Não aceito, com o link de `bot.politica-privacidade-url`. O aceite grava a versão de `pacientes.versao-consentimento-atual` com o `wamid` como evidência.
- **Horários**: até 5 opções (no máximo 2 por dia) entre os fonos ativos da subárea da demanda (sem nenhum, entre todos), mais "Ver mais datas" e "Falar com a recepção". Respeita antecedência mínima e janela da clínica. A escolha cria a pré-reserva de 5 min, e o "Confirmar" vira AGENDADA. Horário tomado ou reserva vencida: o bot mostra as opções de novo.
- **Transbordo**: a palavra "atendente" (ou "recepção", "humano"), o botão "Falar com a recepção", 2 respostas não entendidas seguidas ou nenhum horário livre. Em modo HUMANO o bot fica em silêncio até a recepção devolver a conversa. Publica `TransbordoSolicitado`.
- **Reinício**: 30 min sem mensagem fazem a conversa voltar ao início.
- **Texto digitado**: além de clicar, o contato pode responder "1", "2"... ou o texto da opção (WhatsApp sem suporte a botões).
- **LGPD**: o contexto guarda só o que a conversa precisa. A anonimização do titular apaga a conversa.

| Método | Rota | Quem |
|---|---|---|
| GET | `/api/v1/conversas/em-atendimento` | ADMIN, RECEPCAO. Conversas em modo HUMANO, telefone mascarado |
| POST | `/api/v1/conversas/{id}/devolver-ao-bot` | ADMIN, RECEPCAO. Encerra o atendimento humano (204) |

**Fila de saída (V5).** `evento_saida.tipo` aceita `TEXT` ou `INTERACTIVE`. Em `INTERACTIVE`, `payload` é o objeto `interactive` da Cloud API (`{"type":"button"|"list","body":{...},"action":{...}}`), pronto para `{"messaging_product":"whatsapp","to":<telefone>,"type":"interactive","interactive":<payload>}`; `texto` repete o corpo. Mensagens da mesma resposta saem em ordem de `criado_em`.

Variável nova: `BOT_POLITICA_URL` (endereço da política de privacidade).

## Documentação

- [`docs/sdd-backend.md`](docs/sdd-backend.md): Software Design Document do backend (arquitetura, decisões e plano de entrega).
