import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { FormularioTrocarSenha } from "@/features/auth/formulario-trocar-senha";
import { estaAutenticada, obterSessao } from "@/lib/servidor/sessao";

export const metadata: Metadata = { title: "Definir senha" };

/** Primeiro acesso (senha temporária): a pessoa define a própria senha antes de usar o painel. */
export default async function PaginaTrocarSenha() {
  const sessao = await obterSessao();
  if (!estaAutenticada(sessao)) redirect("/login");
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <h1 className="mb-2 text-2xl font-semibold">Defina sua senha</h1>
        <p className="text-texto-suave mb-6 text-sm">
          Olá, {sessao.usuario?.nome}. Você entrou com uma senha temporária; escolha uma senha só sua para continuar.
        </p>
        <FormularioTrocarSenha destino="/agenda" />
      </div>
    </main>
  );
}
