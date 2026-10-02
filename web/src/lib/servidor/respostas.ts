import "server-only";
import { NextResponse, type NextRequest } from "next/server";

const SEM_CACHE = { "Cache-Control": "no-store" };

/** Problem Details (RFC 9457) gerado pelo próprio BFF. */
export function problema(status: number, codigo: string, detalhe: string) {
  return NextResponse.json(
    { type: `/erros/${codigo}`, title: codigo, status, detail: detalhe },
    { status, headers: { ...SEM_CACHE, "Content-Type": "application/problem+json" } },
  );
}

export function json(corpo: unknown, status = 200) {
  return NextResponse.json(corpo, { status, headers: SEM_CACHE });
}

/**
 * Proteção contra CSRF nas rotas que alteram dados (SDD Web, seção 5): exige o header X-Requested-With
 * (que um formulário de outro site não consegue enviar) e, quando o navegador manda Origin, que ele seja
 * o próprio painel. O cookie SameSite=Lax já barra a maior parte dos casos; isto fecha o resto.
 */
export function origemValida(req: NextRequest): boolean {
  if (req.headers.get("x-requested-with") !== "fetch") return false;
  const origem = req.headers.get("origin");
  if (!origem) return true;
  const host = req.headers.get("x-forwarded-host") ?? req.headers.get("host");
  try {
    return new URL(origem).host === host;
  } catch {
    return false;
  }
}

export function csrfRecusado() {
  return problema(403, "origem-invalida", "Requisição recusada por segurança. Recarregue a página.");
}
