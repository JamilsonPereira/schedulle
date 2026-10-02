import type { NextRequest } from "next/server";
import type { SessaoDeLogin } from "@/lib/api/tipos";
import { chamarApi, guardarTokens, SemSessao } from "@/lib/servidor/api";
import { csrfRecusado, json, origemValida, problema } from "@/lib/servidor/respostas";
import { obterSessao } from "@/lib/servidor/sessao";

/** Troca da própria senha. A API derruba as outras sessões e devolve tokens novos para esta. */
export async function POST(req: NextRequest) {
  if (!origemValida(req)) return csrfRecusado();
  const sessao = await obterSessao();
  const corpo = await req.text();
  try {
    const resposta = await chamarApi(sessao, "auth/senha", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: corpo,
    });
    if (!resposta.ok) {
      await sessao.save();
      return new Response(resposta.body, {
        status: resposta.status,
        headers: { "Content-Type": "application/problem+json", "Cache-Control": "no-store" },
      });
    }
    const nova = (await resposta.json()) as SessaoDeLogin;
    guardarTokens(sessao, nova);
    if (sessao.usuario) sessao.usuario.precisaTrocarSenha = false;
    await sessao.save();
    return json({ usuario: sessao.usuario, clinica: sessao.clinica });
  } catch (e) {
    if (e instanceof SemSessao) {
      sessao.destroy();
      return problema(401, "sessao-expirada", "Sua sessão expirou. Entre novamente.");
    }
    return problema(502, "api-indisponivel", "Não foi possível falar com o servidor. Tente de novo.");
  }
}
