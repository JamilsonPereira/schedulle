/** Só aceita voltar para uma rota interna do painel (evita redirecionamento aberto). */
export function destinoSeguro(voltar: string | null): string {
  if (!voltar || !voltar.startsWith("/") || voltar.startsWith("//") || voltar.startsWith("/\\")) return "/agenda";
  if (voltar.startsWith("/login")) return "/agenda";
  return voltar;
}
