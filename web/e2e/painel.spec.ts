import { expect, test } from "@playwright/test";

const email = process.env.E2E_EMAIL;
const senha = process.env.E2E_SENHA;

test.skip(!email || !senha, "Defina E2E_EMAIL e E2E_SENHA (usuário ADMIN com senha definitiva)");

test.beforeEach(async ({ page }) => {
  await page.goto("/agenda");
  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel("E-mail").fill(email!);
  await page.getByLabel("Senha").fill(senha!);
  await page.getByRole("button", { name: "Entrar" }).click();
  await expect(page).toHaveURL(/\/agenda/);
});

test("login leva à agenda do dia", async ({ page }) => {
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
  await expect(page.getByRole("button", { name: "Novo agendamento" })).toBeVisible();
});

test("navega pelas áreas do painel", async ({ page, isMobile }) => {
  for (const [rotulo, titulo] of [
    ["Pacientes", "Pacientes"],
    ["Profissionais", "Profissionais"],
    ["Usuários", "Usuários"],
  ]) {
    if (isMobile) await page.getByRole("button", { name: "Abrir menu" }).click();
    await page.getByRole("link", { name: rotulo, exact: true }).click();
    await expect(page.getByRole("heading", { name: titulo, level: 1 })).toBeVisible();
  }
});

test("sair apaga a sessão", async ({ page, isMobile }) => {
  if (isMobile) await page.getByRole("button", { name: "Abrir menu" }).click();
  await page.getByRole("button", { name: "Sair" }).click();
  await expect(page).toHaveURL(/\/login\?motivo=saiu/);
  await page.goto("/pacientes");
  await expect(page).toHaveURL(/\/login/);
});

test("rotas de autenticação e plataforma não passam pelo proxy", async ({ request }) => {
  expect((await request.get("/api/proxy/auth/me")).status()).toBe(404);
  const semHeader = await request.post("/api/auth/login", { data: { email, senha } });
  expect(semHeader.status()).toBe(403);
});
