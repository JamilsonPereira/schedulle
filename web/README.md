# agenda-fono-web

Painel da clínica (Next.js 16 + React 19 + TypeScript). Implementa os passos W0 a W4 do
[SDD do painel web](https://claude.ai/artifact/CgXAtp9bn5Jiz6YPqXJFYv), exceto guias de convênio (fora do MVP).

## Rodando

Pré-requisito: a API Spring rodando em `http://localhost:8080` (veja o README da raiz).

```bash
cd web
cp .env.example .env.local     # ajuste SESSION_SECRET (openssl rand -base64 48)
npm install
npm run dev                    # http://localhost:3000
```

Para entrar pela primeira vez, crie a clínica e o ADMIN pelo endpoint de plataforma do backend; o painel pede a
troca da senha temporária no primeiro acesso.

## Scripts

| Comando | O que faz |
| --- | --- |
| `npm run dev` | Servidor de desenvolvimento |
| `npm run build` / `npm start` | Build de produção (`output: standalone`) |
| `npm run lint` · `npm run typecheck` | ESLint e TypeScript |
| `npm test` | Testes unitários (Vitest) |
| `npm run test:tz` | Os mesmos testes com `TZ=UTC` e `TZ=America/Manaus`: o painel usa o fuso da clínica, não o da máquina |
| `npm run test:e2e` | Playwright contra o sistema rodando (`E2E_EMAIL` e `E2E_SENHA` de um ADMIN) |

## Arquitetura

- **BFF no próprio Next** (ADR-02): o navegador só fala com `/api/*`. Os tokens da API ficam num cookie
  criptografado e `httpOnly` (iron-session). `/api/proxy/<caminho>` repassa para `/api/v1/<caminho>` com o Bearer.
- **Renovação de token**: o BFF renova antes de vencer e repete uma vez após 401. Renovações simultâneas do mesmo
  refresh token viram uma só chamada, porque o backend derruba a sessão quando um refresh é reutilizado.
  Com mais de uma instância do painel, use afinidade de sessão no balanceador.
- **CSRF**: rotas que alteram dados exigem `X-Requested-With: fetch` e conferem `Origin`.
- **Inatividade**: logout após `SESSION_IDLE_MINUTES` (padrão 30) sem uso, no navegador e no BFF.
- **Permissões**: `src/lib/auth/permissoes.ts` esconde o que o perfil não pode usar; quem decide é a API.
- **Fuso**: tudo passa por `src/lib/datas.ts` e usa o fuso da clínica.
- **Dados do servidor**: TanStack Query; filtros e data ficam na URL. Updates otimistas só em confirmar, registrar
  presença e arrastar sessão; conflito (409) desfaz e avisa.

```
src/
├── app/                 rotas (login, trocar-senha, (painel)/..., api/auth/*, api/proxy/*)
├── components/          ui/ (botões, campos, diálogos) e painel/ (moldura com menu)
├── features/
│   ├── agenda/          grade dia/semana, lista no celular, painel da sessão, novo agendamento, bloqueios
│   ├── pacientes/       lista, ficha com abas, anexos, consentimento, direitos do titular
│   ├── configuracoes/   clínica e política, salas, profissionais com grade, usuários
│   └── auth/            login, troca de senha, contexto do usuário
├── lib/                 cliente da API, tipos, erros (RFC 9457), datas, permissões, servidor (BFF)
└── proxy.ts             guarda de rota (redireciona para /login sem cookie)
```

## Diferenças em relação ao SDD

| SDD | Aqui | Motivo |
| --- | --- | --- |
| FullCalendar (ADR-07) | Grade própria em CSS | A visão com um fono por coluna é paga no FullCalendar; a grade própria tem colunas, hachurado, arrastar e lista no celular |
| Tipos gerados do OpenAPI (ADR-04) | `src/lib/api/tipos.ts` escrito à mão | O backend ainda não publica `/v3/api-docs` (springdoc) |
| SSE para tempo real (ADR-05) | Polling de 30 s da agenda | O backend ainda não tem o endpoint SSE |
| CSP com nonce | CSP fixa sem scripts de terceiros | Nonce entra no W6, junto com o deploy |
| Séries, guias, inbox, métricas | Fora | Dependem dos passos 5, 6 e 7 do backend |
