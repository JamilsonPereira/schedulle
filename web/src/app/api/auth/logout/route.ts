import type { NextRequest } from "next/server";
import { chamarApiPublica } from "@/lib/servidor/api";
import { csrfRecusado, origemValida } from "@/lib/servidor/respostas";
import { obterSessao } from "@/lib/servidor/sessao";

/** Sai desta sessão ou, com {"todas": true}, de todos os dispositivos. Sempre apaga o cookie. */
export async function POST(req: NextRequest) {
  if (!origemValida(req)) return csrfRecusado();
  const corpo = (await req.json().catch(() => ({}))) as { todas?: boolean };
  const sessao = await obterSessao();
  if (sessao.refreshToken) {
    try {
      await chamarApiPublica("auth/logout", { refreshToken: sessao.refreshToken, todas: corpo.todas === true });
    } catch {
      // Sem API, o cookie é apagado mesmo assim; o refresh token expira sozinho.
    }
  }
  sessao.destroy();
  return new Response(null, { status: 204, headers: { "Cache-Control": "no-store" } });
}
