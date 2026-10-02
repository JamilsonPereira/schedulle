import { json, problema } from "@/lib/servidor/respostas";
import { estaAutenticada, expirouPorInatividade, obterSessao } from "@/lib/servidor/sessao";

/** Quem está logado (dados guardados no login). Não renova a inatividade. */
export async function GET() {
  const sessao = await obterSessao();
  if (!estaAutenticada(sessao) || expirouPorInatividade(sessao)) {
    return problema(401, "sessao-expirada", "Sua sessão expirou. Entre novamente.");
  }
  return json({ usuario: sessao.usuario, clinica: sessao.clinica });
}
