import { ErroApi, ErroRede } from "@/lib/api/erros";
import type { ProblemDetail } from "@/lib/api/tipos";

/**
 * Cliente da API no navegador. Fala só com o BFF (/api/proxy/*); o token nunca passa por aqui.
 * Todo erro vira ErroApi (Problem Details) ou ErroRede.
 */
export const EVENTO_SESSAO_EXPIRADA = "agenda-fono:sessao-expirada";

type Parametros = Record<string, string | number | boolean | null | undefined>;

interface Opcoes {
  metodo?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  corpo?: unknown;
  formulario?: FormData;
  parametros?: Parametros;
  sinal?: AbortSignal;
}

export function montarQuery(parametros?: Parametros): string {
  if (!parametros) return "";
  const busca = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== null && valor !== undefined && valor !== "") busca.set(chave, String(valor));
  }
  const texto = busca.toString();
  return texto ? `?${texto}` : "";
}

export async function requisitar<T>(url: string, opcoes: Opcoes = {}): Promise<T> {
  const headers: Record<string, string> = { "X-Requested-With": "fetch", Accept: "application/json" };
  let body: BodyInit | undefined;
  if (opcoes.formulario) {
    body = opcoes.formulario;
  } else if (opcoes.corpo !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(opcoes.corpo);
  }

  let resposta: Response;
  try {
    resposta = await fetch(url + montarQuery(opcoes.parametros), {
      method: opcoes.metodo ?? "GET",
      headers,
      body,
      signal: opcoes.sinal,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ErroRede();
  }

  if (!resposta.ok) {
    let problema: ProblemDetail = {};
    if ((resposta.headers.get("content-type") ?? "").includes("json")) {
      problema = (await resposta.json().catch(() => ({}))) as ProblemDetail;
    }
    if (resposta.status === 401 && typeof window !== "undefined") {
      window.dispatchEvent(new Event(EVENTO_SESSAO_EXPIRADA));
    }
    throw new ErroApi(resposta.status, problema);
  }
  if (resposta.status === 204) return undefined as T;
  const tipo = resposta.headers.get("content-type") ?? "";
  return (tipo.includes("json") ? await resposta.json() : await resposta.blob()) as T;
}

/** Chamada à API Spring via BFF: api("sessoes", { parametros: { de, ate } }). */
export function api<T>(caminho: string, opcoes?: Opcoes): Promise<T> {
  return requisitar<T>(`/api/proxy/${caminho.replace(/^\//, "")}`, opcoes);
}

/** URL para abrir ou baixar um arquivo pela sessão (anexos, exportação LGPD). */
export function urlDoArquivo(caminho: string): string {
  return `/api/proxy/${caminho.replace(/^\//, "")}`;
}
