import "server-only";
import type { IronSession } from "iron-session";
import type { SessaoDeLogin } from "@/lib/api/tipos";
import { config } from "@/lib/servidor/config";
import { type DadosSessao, estaAutenticada, expirouPorInatividade } from "@/lib/servidor/sessao";

/**
 * Chamadas do BFF à API Spring, com o access token da sessão e renovação automática (SDD Web, seção 5).
 *
 * Renovação: o backend troca o refresh token a cada uso e, se um refresh já usado aparecer de novo, derruba
 * todas as sessões do usuário (proteção contra roubo). Como o painel faz várias requisições em paralelo,
 * duas delas poderiam tentar renovar com o mesmo refresh token. Para evitar isso, renovações simultâneas do
 * mesmo token compartilham uma única chamada, e o resultado fica guardado por 60 s para as requisições que
 * ainda chegarem com o cookie antigo. Com mais de uma instância do BFF, use afinidade de sessão
 * (sticky sessions) no balanceador.
 */

type Tokens = Pick<SessaoDeLogin, "tokenAcesso" | "acessoExpiraEm" | "refreshToken" | "refreshExpiraEm">;

const emAndamento = new Map<string, Promise<Tokens | null>>();
const recentes = new Map<string, { tokens: Tokens; ate: number }>();
const MARGEM_RENOVACAO_MS = 30_000;

export class SemSessao extends Error {
  constructor(readonly motivo: "ausente" | "inatividade" | "expirada") {
    super(`Sessão ${motivo}`);
  }
}

export function urlDaApi(caminho: string): string {
  return `${config.apiBaseUrl}/api/v1/${caminho.replace(/^\//, "")}`;
}

/** Chamada sem autenticação (login, refresh, logout). */
export async function chamarApiPublica(caminho: string, corpo: unknown): Promise<Response> {
  return fetch(urlDaApi(caminho), {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json", "X-Request-Id": crypto.randomUUID() },
    body: JSON.stringify(corpo),
    cache: "no-store",
  });
}

export async function renovarTokens(refreshToken: string): Promise<Tokens | null> {
  const agora = Date.now();
  for (const [chave, valor] of recentes) {
    if (valor.ate < agora) recentes.delete(chave);
  }
  const recente = recentes.get(refreshToken);
  if (recente) return recente.tokens;

  let promessa = emAndamento.get(refreshToken);
  if (!promessa) {
    promessa = (async () => {
      const resposta = await chamarApiPublica("auth/refresh", { refreshToken });
      if (!resposta.ok) return null;
      const s = (await resposta.json()) as SessaoDeLogin;
      const tokens: Tokens = {
        tokenAcesso: s.tokenAcesso,
        acessoExpiraEm: s.acessoExpiraEm,
        refreshToken: s.refreshToken,
        refreshExpiraEm: s.refreshExpiraEm,
      };
      recentes.set(refreshToken, { tokens, ate: Date.now() + 60_000 });
      return tokens;
    })().finally(() => emAndamento.delete(refreshToken));
    emAndamento.set(refreshToken, promessa);
  }
  return promessa;
}

export function guardarTokens(sessao: DadosSessao, tokens: Tokens) {
  sessao.tokenAcesso = tokens.tokenAcesso;
  sessao.acessoExpiraEm = tokens.acessoExpiraEm;
  sessao.refreshToken = tokens.refreshToken;
  sessao.refreshExpiraEm = tokens.refreshExpiraEm;
}

/**
 * Chama a API como o usuário da sessão. Renova o token antes se estiver para vencer e repete uma vez
 * se a API responder 401. Atualiza a sessão em memória; quem chamou precisa salvar (`sessao.save()`).
 *
 * @throws SemSessao se não houver sessão válida (o chamador responde 401 e apaga o cookie)
 */
export async function chamarApi(
  sessao: IronSession<DadosSessao>,
  caminho: string,
  init: { method: string; headers?: Record<string, string>; body?: BodyInit | null },
): Promise<Response> {
  if (!estaAutenticada(sessao)) throw new SemSessao("ausente");
  if (expirouPorInatividade(sessao)) throw new SemSessao("inatividade");

  const venceEm = sessao.acessoExpiraEm ? Date.parse(sessao.acessoExpiraEm) : 0;
  if (!sessao.tokenAcesso || venceEm - Date.now() < MARGEM_RENOVACAO_MS) {
    await renovarOuFalhar(sessao);
  }

  const executar = () =>
    fetch(urlDaApi(caminho), {
      method: init.method,
      headers: {
        Accept: "application/json",
        ...init.headers,
        Authorization: `Bearer ${sessao.tokenAcesso}`,
        "X-Request-Id": crypto.randomUUID(),
      },
      body: init.body ?? undefined,
      cache: "no-store",
    });

  let resposta = await executar();
  if (resposta.status === 401) {
    await renovarOuFalhar(sessao);
    resposta = await executar();
    if (resposta.status === 401) throw new SemSessao("expirada");
  }
  sessao.ultimaAtividade = Date.now();
  return resposta;
}

async function renovarOuFalhar(sessao: DadosSessao) {
  const tokens = sessao.refreshToken ? await renovarTokens(sessao.refreshToken) : null;
  if (!tokens) throw new SemSessao("expirada");
  guardarTokens(sessao, tokens);
}
