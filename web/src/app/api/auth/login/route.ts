import type { NextRequest } from "next/server";
import type { Perfil, SessaoDeLogin } from "@/lib/api/tipos";
import { chamarApiPublica, guardarTokens, urlDaApi } from "@/lib/servidor/api";
import { csrfRecusado, json, origemValida, problema } from "@/lib/servidor/respostas";
import { obterSessao } from "@/lib/servidor/sessao";

/** Login: troca e-mail e senha pelo par de tokens e grava tudo no cookie criptografado. */
export async function POST(req: NextRequest) {
  if (!origemValida(req)) return csrfRecusado();

  let corpo: { email?: unknown; senha?: unknown };
  try {
    corpo = await req.json();
  } catch {
    return problema(400, "dados-invalidos", "Informe e-mail e senha.");
  }
  if (typeof corpo.email !== "string" || typeof corpo.senha !== "string") {
    return problema(400, "dados-invalidos", "Informe e-mail e senha.");
  }

  let resposta: Response;
  try {
    resposta = await chamarApiPublica("auth/login", { email: corpo.email, senha: corpo.senha });
  } catch {
    return problema(502, "api-indisponivel", "Não foi possível falar com o servidor. Tente de novo.");
  }
  if (!resposta.ok) {
    return new Response(resposta.body, {
      status: resposta.status,
      headers: { "Content-Type": "application/problem+json", "Cache-Control": "no-store" },
    });
  }
  const login = (await resposta.json()) as SessaoDeLogin;

  // Fuso e nome da clínica vêm do /auth/me: a agenda inteira depende do fuso da clínica.
  const me = await fetch(urlDaApi("auth/me"), {
    headers: { Authorization: `Bearer ${login.tokenAcesso}`, Accept: "application/json" },
    cache: "no-store",
  });
  if (!me.ok) return problema(502, "api-indisponivel", "Não foi possível carregar os dados da clínica.");
  const perfil = (await me.json()) as Perfil;

  const sessao = await obterSessao();
  guardarTokens(sessao, login);
  sessao.ultimaAtividade = Date.now();
  sessao.usuario = {
    id: login.usuario.id,
    nome: login.usuario.nome,
    papeis: login.usuario.papeis,
    profissionalId: login.usuario.profissionalId,
    precisaTrocarSenha: login.usuario.precisaTrocarSenha,
  };
  sessao.clinica = { id: perfil.clinica.id, nome: perfil.clinica.nome, fuso: perfil.clinica.fuso };
  await sessao.save();

  return json({ usuario: sessao.usuario, clinica: sessao.clinica });
}
