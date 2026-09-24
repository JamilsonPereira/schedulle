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
├── agenda        grades, bloqueios, disponibilidade, séries, sessões
├── bot           máquina de estados da conversa
├── clinica       tenants, usuários, profissionais
├── mensageria    WhatsApp Cloud API (webhook, envio, templates)
└── pacientes     responsáveis, pacientes, consentimento
```

## Roadmap em passos

- [x] **Passo 0 — Fundação**: projeto (Spring Boot 4.1), schema do MVP sem convênio (V1), constraint de conflito testada, webhook com verificação e assinatura, CI.
- [ ] **Passo 1 — Mensageria**: DTOs do payload da Meta, deduplicação por `wamid`, fila de eventos (outbox), cliente de envio (`RestClient`), bot "eco" funcionando no número de teste.
- [ ] **Passo 2 — Cadastros da clínica**: entidades JPA de clínica, profissional, grade e bloqueio; API de cadastro; seed de uma clínica de exemplo.
- [ ] **Passo 3 — Disponibilidade**: cálculo de slots (grade − bloqueios − sessões), pré-reserva de 5 min, job de expiração, `GET /disponibilidade`.
- [ ] **Passo 4 — Bot de agendamento**: consentimento LGPD, menu, paciente novo, escolha de horário e confirmação da avaliação.
- [ ] **Passo 5 — Terapias recorrentes e lembretes**: séries, materialização de 8 semanas, templates de lembrete com botões Confirmo/Vou faltar.
- [ ] **Passo 6 — Faltas e transbordo**: aviso de falta, reposição, lista de espera e caixa de entrada da recepção.
- [ ] **Passo 7 — Pacientes e anexos**: ficha do paciente, anexos no S3 e pendências (convênio e guias ficam para depois do MVP).
- [ ] **Passo 8 — Produção**: RLS no Postgres, deploy AWS São Paulo, observabilidade com Grafana, checklist de segurança, piloto.

## Documentação

- [`docs/sdd-backend.md`](docs/sdd-backend.md): Software Design Document do backend (arquitetura, decisões e plano de entrega).
