import "server-only";
import { getIronSession, type SessionOptions } from "iron-session";
import { cookies } from "next/headers";
import type { Papel, Uuid } from "@/lib/api/tipos";
import { config, segredoDaSessao } from "@/lib/servidor/config";

export const NOME_COOKIE = "agenda_fono_sessao";

/** Dados mínimos do usuário para montar o painel sem chamar a API (sem dado de paciente). */
export interface UsuarioDaSessao {
  id: Uuid;
  nome: string;
  papeis: Papel[];
  profissionalId: Uuid | null;
  precisaTrocarSenha: boolean;
}

export interface ClinicaDaSessao {
  id: Uuid;
  nome: string;
  fuso: string;
}

/**
 * Conteúdo do cookie de sessão. Criptografado e assinado pelo iron-session; o navegador não lê nada
 * (httpOnly). Tokens nunca vão para o JavaScript da página (ADR-02).
 */
export interface DadosSessao {
  tokenAcesso?: string;
  acessoExpiraEm?: string;
  refreshToken?: string;
  refreshExpiraEm?: string;
  ultimaAtividade?: number;
  usuario?: UsuarioDaSessao;
  clinica?: ClinicaDaSessao;
}

export function opcoesDaSessao(): SessionOptions {
  return {
    password: segredoDaSessao(),
    cookieName: NOME_COOKIE,
    cookieOptions: {
      httpOnly: true,
      secure: config.cookieSeguro,
      sameSite: "lax",
      path: "/",
      // Sem maxAge: cookie de sessão do navegador. A validade real é a do refresh token e a inatividade.
    },
  };
}

export async function obterSessao() {
  return getIronSession<DadosSessao>(await cookies(), opcoesDaSessao());
}

export function estaAutenticada(s: DadosSessao): boolean {
  return Boolean(s.refreshToken && s.usuario && s.clinica);
}

/** Logout por inatividade (padrão 30 min): computadores de recepção são compartilhados. */
export function expirouPorInatividade(s: DadosSessao, agora = Date.now()): boolean {
  if (!s.ultimaAtividade) return false;
  return agora - s.ultimaAtividade > config.idleMinutes * 60_000;
}
