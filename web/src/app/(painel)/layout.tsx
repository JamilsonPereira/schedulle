import { redirect } from "next/navigation";
import { Moldura } from "@/components/painel/moldura";
import { ProvedorUsuario } from "@/features/auth/contexto";
import { config } from "@/lib/servidor/config";
import { estaAutenticada, expirouPorInatividade, obterSessao } from "@/lib/servidor/sessao";

/** Guarda real do painel: sessão válida, sem inatividade e com senha definitiva. */
export default async function LayoutPainel({ children }: { children: React.ReactNode }) {
  const sessao = await obterSessao();
  if (!estaAutenticada(sessao) || !sessao.usuario || !sessao.clinica) redirect("/login");
  if (expirouPorInatividade(sessao)) redirect("/login?motivo=inatividade");
  if (sessao.usuario.precisaTrocarSenha) redirect("/trocar-senha");

  const { id, nome, papeis, profissionalId } = sessao.usuario;
  return (
    <ProvedorUsuario valor={{ usuario: { id, nome, papeis, profissionalId }, clinica: sessao.clinica }}>
      <Moldura minutosInatividade={config.idleMinutes}>{children}</Moldura>
    </ProvedorUsuario>
  );
}
