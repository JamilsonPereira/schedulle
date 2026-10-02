import { requisitar } from "@/lib/api/cliente";
import type { ClinicaLogada, UsuarioLogado } from "@/features/auth/contexto";

type Sessao = { usuario: UsuarioLogado & { precisaTrocarSenha: boolean }; clinica: ClinicaLogada };

export function entrar(email: string, senha: string) {
  return requisitar<Sessao>("/api/auth/login", { metodo: "POST", corpo: { email, senha } });
}

export function sair(todas = false) {
  return requisitar<void>("/api/auth/logout", { metodo: "POST", corpo: { todas } });
}

export function trocarSenha(senhaAtual: string, novaSenha: string) {
  return requisitar<Sessao>("/api/auth/senha", { metodo: "POST", corpo: { senhaAtual, novaSenha } });
}
