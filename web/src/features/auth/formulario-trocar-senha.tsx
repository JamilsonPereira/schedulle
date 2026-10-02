"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useHidratado } from "@/lib/use-hidratado";
import { Campo, Input } from "@/components/ui/campos";
import { MensagemErro } from "@/components/ui/diversos";
import { trocarSenha } from "@/features/auth/acoes";
import { trocarSenhaSchema, type TrocarSenhaForm } from "@/features/auth/schemas";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";

export function FormularioTrocarSenha({ destino }: { destino?: string }) {
  const router = useRouter();
  const [erro, setErro] = React.useState<string | null>(null);
  const form = useForm<TrocarSenhaForm>({
    resolver: zodResolver(trocarSenhaSchema),
    defaultValues: { senhaAtual: "", novaSenha: "", confirmacao: "" },
  });

  async function enviar(dados: TrocarSenhaForm) {
    setErro(null);
    try {
      await trocarSenha(dados.senhaAtual, dados.novaSenha);
      toast.success("Senha alterada. As outras sessões foram encerradas.");
      form.reset();
      if (destino) {
        router.replace(destino);
        router.refresh();
      }
    } catch (e) {
      if (e instanceof ErroApi && e.codigo === "senha-fraca") {
        form.setError("novaSenha", { message: e.message });
      } else if (e instanceof ErroApi && e.codigo === "credenciais-invalidas") {
        form.setError("senhaAtual", { message: "Senha atual incorreta" });
      } else {
        setErro(mensagemDoErro(e));
      }
    }
  }

  const { errors, isSubmitting } = form.formState;
  const hidratado = useHidratado();
  return (
    <form onSubmit={form.handleSubmit(enviar)} className="flex flex-col gap-4" method="post" noValidate>
      {erro && <MensagemErro>{erro}</MensagemErro>}
      <Campo id="senhaAtual" rotulo="Senha atual" erro={errors.senhaAtual?.message}>
        <Input type="password" autoComplete="current-password" {...form.register("senhaAtual")} />
      </Campo>
      <Campo
        id="novaSenha"
        rotulo="Nova senha"
        erro={errors.novaSenha?.message}
        dica="Pelo menos 10 caracteres. Evite sequências e partes do seu e-mail."
      >
        <Input type="password" autoComplete="new-password" {...form.register("novaSenha")} />
      </Campo>
      <Campo id="confirmacao" rotulo="Repita a nova senha" erro={errors.confirmacao?.message}>
        <Input type="password" autoComplete="new-password" {...form.register("confirmacao")} />
      </Campo>
      <Button type="submit" carregando={isSubmitting} disabled={!hidratado}>
        Salvar senha
      </Button>
    </form>
  );
}
