# agenda-fono

SaaS de agendamento via WhatsApp para clínicas de fonoaudiologia.
Stack: Java 21, Spring Boot 4.1, Maven, PostgreSQL 16, Flyway, Testcontainers.

## Pré-requisitos

- JDK 21
- Maven 3.9+ (ou o Maven embutido do IntelliJ)
- Docker Desktop rodando (para o `docker compose` e para os testes com Testcontainers)

## Rodando localmente

```bash
docker compose up -d          # Postgres 16 + Redis
mvn spring-boot:run           # sobe a API em http://localhost:8080 e aplica as migrations
curl http://localhost:8080/actuator/health
```

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
├── compartilhado relógio, clínica da requisição (tenant)
├── bot           máquina de estados da conversa
├── clinica       tenants, usuários, profissionais
├── mensageria    WhatsApp Cloud API (webhook, envio, templates)
└── pacientes     responsáveis, pacientes, consentimento, anexos, direitos do titular
```

## Roadmap em passos

- [x] **Passo 0 — Fundação**: projeto (Spring Boot 4.1), schema do MVP sem convênio (V1), constraint de conflito testada, webhook com verificação e assinatura, CI.
- [ ] **Passo 1 — Mensageria**: DTOs do payload da Meta, deduplicação por `wamid`, fila de eventos (outbox), cliente de envio (`RestClient`), bot "eco" funcionando no número de teste.
- [ ] **Passo 2 — Cadastros da clínica**: entidades JPA de clínica, profissional, grade e bloqueio; API de cadastro; seed de uma clínica de exemplo.
- [x] **Passo 3 — Disponibilidade e agendamento**: cálculo de slots (grade − bloqueios − sessões), pré-reserva de 5 min do bot, agendamento/remarcação/cancelamento pelo painel, aviso de falta, presença, job de expiração, API `/api/v1/disponibilidade` e `/api/v1/sessoes`.
- [ ] **Passo 4 — Bot de agendamento**: consentimento LGPD, menu, paciente novo, escolha de horário e confirmação da avaliação.
- [ ] **Passo 5 — Terapias recorrentes e lembretes**: séries, materialização de 8 semanas, templates de lembrete com botões Confirmo/Vou faltar.
- [ ] **Passo 6 — Faltas e transbordo**: aviso de falta, reposição, lista de espera e caixa de entrada da recepção.
- [x] **Passo 7 — Pacientes e anexos** (parcial): responsáveis, pacientes, consentimento LGPD com histórico, busca, ficha, anexos (armazenamento local; S3 no Passo 8), exportação e anonimização. Pendentes: auditoria de acesso e endpoint `/pendencias`. Convênio e guias ficam para depois do MVP.
- [ ] **Passo 8 — Produção**: RLS no Postgres, deploy AWS São Paulo, observabilidade com Grafana, checklist de segurança, piloto.

## API da agenda (provisória)

Até o Passo 2 (autenticação), a clínica é informada no header `X-Clinica-Id`. Não exponha a API publicamente antes disso.

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/api/v1/disponibilidade?profissionalId=&de=AAAA-MM-DD&ate=AAAA-MM-DD[&duracaoMin=]` | Horários livres |
| GET | `/api/v1/sessoes?de=<ISO>&ate=<ISO>[&profissionalId=]` | Sessões do período |
| GET | `/api/v1/sessoes/{id}` | Uma sessão |
| POST | `/api/v1/sessoes` | Agendar (`pacienteId`, `profissionalId`, `tipo`, `inicio`, `duracaoMin?`, `recursoId?`, `permitirForaDaGrade?`) |
| POST | `/api/v1/sessoes/{id}/remarcar` | Remarcar (`novoInicio`, `versao?`) |
| PATCH | `/api/v1/sessoes/{id}/status` | `acao`: `CONFIRMAR_PRESENCA`, `CANCELAR`, `AVISAR_FALTA`, `REGISTRAR_ATENDIMENTO`, `REGISTRAR_FALTA_SEM_AVISO` |

Erros seguem RFC 9457 (Problem Details): 409 `/erros/horario-indisponivel` traz `alternativas`; 422 `/erros/horario-fora-da-agenda` traz `motivo`.

## API de pacientes (provisória)

Mesmo header `X-Clinica-Id`. Telefones aceitam `(11) 99999-0000`, `11999990000` ou `+5511999990000`.

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

## Documentação

- [`docs/sdd-backend.md`](docs/sdd-backend.md): Software Design Document do backend (arquitetura, decisões e plano de entrega).
