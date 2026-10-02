"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { useHidratado } from "@/lib/use-hidratado";
import { Campo, Input } from "@/components/ui/campos";
import { MensagemErro } from "@/components/ui/diversos";
import { entrar } from "@/features/auth/acoes";
import { destinoSeguro } from "@/features/auth/destino";
import { loginSchema, type LoginForm } from "@/features/auth/schemas";
import { mensagemDoErro } from "@/lib/api/erros";

const AVISOS: Record<string, string> = {
  inatividade: "Você saiu automaticamente após um período sem uso.",
  expirada: "Sua sessão expirou. Entre novamente.",
  saiu: "Você saiu do painel.",
};

export function FormularioLogin() {
  const router = useRouter();
  const params = useSearchParams();
  const [erro, setErro] = React.useState<string | null>(null);
  const aviso = AVISOS[params.get("motivo") ?? ""];
  const form = useForm<LoginForm>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", senha: "" } });

  async function enviar(dados: LoginForm) {
    setErro(null);
    try {
      const sessao = await entrar(dados.email, dados.senha);
      const destino = sessao.usuario.precisaTrocarSenha ? "/trocar-senha" : destinoSeguro(params.get("voltar"));
      router.replace(destino);
      router.refresh();
    } catch (e) {
      setErro(mensagemDoErro(e));
      form.setValue("senha", "");
      form.setFocus("senha");
    }
  }

  const { errors, isSubmitting } = form.formState;
  const hidratado = useHidratado();
  return (
    <form onSubmit={form.handleSubmit(enviar)} className="flex flex-col gap-4" method="post" noValidate>
      {aviso && !erro && (
        <p role="status" className="rounded-padrao bg-primaria-suave text-primaria px-4 py-3 text-sm">
          {aviso}
        </p>
      )}
      {erro && <MensagemErro>{erro}</MensagemErro>}
      <Campo id="email" rotulo="E-mail" erro={errors.email?.message}>
        <Input type="email" autoComplete="username" autoFocus {...form.register("email")} />
      </Campo>
      <Campo id="senha" rotulo="Senha" erro={errors.senha?.message}>
        <Input type="password" autoComplete="current-password" {...form.register("senha")} />
      </Campo>
      <Button type="submit" carregando={isSubmitting} disabled={!hidratado}>
        {isSubmitting ? "Entrando…" : "Entrar"}
      </Button>
      <p className="text-texto-suave text-sm">
        Esqueceu a senha? Peça ao administrador da clínica uma senha temporária.
      </p>
    </form>
  );
}
