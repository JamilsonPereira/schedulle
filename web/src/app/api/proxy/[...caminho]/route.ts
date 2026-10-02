import type { NextRequest } from "next/server";
import { chamarApi, SemSessao } from "@/lib/servidor/api";
import { csrfRecusado, origemValida, problema } from "@/lib/servidor/respostas";
import { obterSessao } from "@/lib/servidor/sessao";

/**
 * Proxy autenticado: /api/proxy/<caminho> → API Spring /api/v1/<caminho> com o Bearer da sessão.
 * Rotas de autenticação e de plataforma não passam por aqui.
 */
const BLOQUEADOS = new Set(["auth", "plataforma"]);
const HEADERS_DA_RESPOSTA = ["content-type", "content-disposition", "x-content-type-options"];

type Contexto = { params: Promise<{ caminho: string[] }> };

async function repassar(req: NextRequest, ctx: Contexto) {
  const { caminho } = await ctx.params;
  if (caminho.length === 0 || BLOQUEADOS.has(caminho[0]) || caminho.some((p) => p === ".." || p === ".")) {
    return problema(404, "nao-encontrado", "Rota inexistente.");
  }
  if (req.method !== "GET" && !origemValida(req)) return csrfRecusado();

  const destino = caminho.map(encodeURIComponent).join("/") + req.nextUrl.search;
  const headers: Record<string, string> = {};
  const tipo = req.headers.get("content-type");
  if (tipo) headers["Content-Type"] = tipo;
  const corpo = req.method === "GET" || req.method === "HEAD" ? null : await req.arrayBuffer();

  const sessao = await obterSessao();
  let resposta: Response;
  try {
    resposta = await chamarApi(sessao, destino, { method: req.method, headers, body: corpo });
  } catch (e) {
    if (e instanceof SemSessao) {
      sessao.destroy();
      return problema(401, "sessao-expirada", "Sua sessão expirou. Entre novamente.");
    }
    return problema(502, "api-indisponivel", "Não foi possível falar com o servidor. Tente de novo.");
  }
  await sessao.save();

  const saida = new Headers({ "Cache-Control": "no-store" });
  for (const nome of HEADERS_DA_RESPOSTA) {
    const valor = resposta.headers.get(nome);
    if (valor) saida.set(nome, valor);
  }
  return new Response(resposta.status === 204 ? null : resposta.body, { status: resposta.status, headers: saida });
}

export const GET = repassar;
export const POST = repassar;
export const PUT = repassar;
export const PATCH = repassar;
export const DELETE = repassar;
