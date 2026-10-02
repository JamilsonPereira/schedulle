import "server-only";

/** Variáveis de ambiente do BFF (SDD Web, seção 12). Nenhuma chega ao navegador. */
export const config = {
  apiBaseUrl: (process.env.API_BASE_URL ?? "http://localhost:8080").replace(/\/$/, ""),
  sessionSecret: process.env.SESSION_SECRET ?? "",
  idleMinutes: Number(process.env.SESSION_IDLE_MINUTES ?? 30),
  cookieSeguro: process.env.NODE_ENV === "production",
};

export function segredoDaSessao(): string {
  const segredo = config.sessionSecret;
  if (segredo.length >= 32) return segredo;
  if (process.env.NODE_ENV === "production") {
    throw new Error("SESSION_SECRET precisa ter pelo menos 32 caracteres em produção");
  }
  // Desenvolvimento: chave fixa conhecida, só para rodar localmente sem configurar nada.
  return "dev-apenas-local-troque-em-producao-0123456789";
}
