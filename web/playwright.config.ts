import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta contra o sistema de verdade (SDD Web, seção 11):
 * backend Spring + Postgres rodando (docker compose + mvn spring-boot:run) e um usuário ADMIN criado.
 *
 *   E2E_EMAIL=dona@clinica.com.br E2E_SENHA='senha-definitiva' npm run test:e2e
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    locale: "pt-BR",
    timezoneId: "Asia/Tokyo", // fuso diferente do da clínica: o painel tem de mostrar o da clínica
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"], viewport: { width: 1366, height: 900 } } },
    { name: "celular", use: { ...devices["Pixel 7"] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: "npm run dev", url: "http://localhost:3000/login", reuseExistingServer: true },
});
