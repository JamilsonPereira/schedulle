"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Plus } from "lucide-react";
import * as React from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input, Select } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { Badge, Cabecalho, Carregando, MensagemErro, Tabela, Td, Th, Vazio } from "@/components/ui/diversos";
import { useRecursos, useSalvarRecurso } from "@/features/configuracoes/consultas";
import { SomenteAdmin } from "@/features/configuracoes/somente-admin";
import { mensagemDoErro } from "@/lib/api/erros";
import type { Recurso } from "@/lib/api/tipos";
import { NOME_TIPO_RECURSO } from "@/lib/rotulos";

export function PaginaSalas() {
  return (
    <SomenteAdmin>
      <Conteudo />
    </SomenteAdmin>
  );
}

function Conteudo() {
  const { data, isLoading, error } = useRecursos();
  const [editando, setEditando] = React.useState<Recurso | "nova" | null>(null);

  return (
    <>
      <div className="mx-auto max-w-3xl">
        <Cabecalho
          titulo="Salas"
          descricao="Salas e cabines usadas na grade. Duas sessões nunca ocupam a mesma sala ao mesmo tempo."
          acoes={
            <Button onClick={() => setEditando("nova")}>
              <Plus /> Nova sala
            </Button>
          }
        />
        {isLoading && <Carregando />}
        {error && <MensagemErro>{mensagemDoErro(error)}</MensagemErro>}
        {data && data.length === 0 && <Vazio titulo="Nenhuma sala cadastrada">Cadastrar salas é opcional.</Vazio>}
        {data && data.length > 0 && (
          <Tabela>
            <thead>
              <tr>
                <Th>Nome</Th>
                <Th>Tipo</Th>
                <Th>Situação</Th>
                <Th className="w-24">
                  <span className="sr-only">Ações</span>
                </Th>
              </tr>
            </thead>
            <tbody>
              {data.map((r) => (
                <tr key={r.id}>
                  <Td className="font-medium">{r.nome}</Td>
                  <Td>{NOME_TIPO_RECURSO[r.tipo]}</Td>
                  <Td>{r.ativo ? <Badge tom="sucesso">Ativa</Badge> : <Badge>Inativa</Badge>}</Td>
                  <Td>
                    <Button
                      variante="fantasma"
                      tamanho="pequeno"
                      onClick={() => setEditando(r)}
                      aria-label={`Editar ${r.nome}`}
                    >
                      Editar
                    </Button>
                  </Td>
                </tr>
              ))}
            </tbody>
          </Tabela>
        )}
        {editando && <DialogoSala recurso={editando === "nova" ? null : editando} aoFechar={() => setEditando(null)} />}
      </div>
    </>
  );
}

const schema = z.object({
  nome: z.string().trim().min(1, "Informe o nome").max(60, "Máximo de 60 caracteres"),
  tipo: z.enum(["SALA", "CABINE"]),
  ativo: z.boolean(),
});

function DialogoSala({ recurso, aoFechar }: { recurso: Recurso | null; aoFechar: () => void }) {
  const salvar = useSalvarRecurso();
  const form = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { nome: recurso?.nome ?? "", tipo: recurso?.tipo ?? "SALA", ativo: recurso?.ativo ?? true },
  });
  const [erro, setErro] = React.useState<string | null>(null);

  const enviar = form.handleSubmit((d) => {
    setErro(null);
    salvar.mutate(
      { ...d, id: recurso?.id, versao: recurso?.versao },
      {
        onSuccess: () => {
          toast.success(recurso ? "Sala atualizada." : "Sala criada.");
          aoFechar();
        },
        onError: (e) => setErro(mensagemDoErro(e)),
      },
    );
  });

  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo={recurso ? "Editar sala" : "Nova sala"}
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button onClick={enviar} carregando={salvar.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        {erro && <MensagemErro>{erro}</MensagemErro>}
        <Campo id="sala-nome" rotulo="Nome" erro={form.formState.errors.nome?.message}>
          <Input autoFocus {...form.register("nome")} />
        </Campo>
        <Campo id="sala-tipo" rotulo="Tipo">
          <Select {...form.register("tipo")}>
            <option value="SALA">Sala</option>
            <option value="CABINE">Cabine</option>
          </Select>
        </Campo>
        {recurso && <Checkbox id="sala-ativo" rotulo="Ativa" {...form.register("ativo")} />}
        <button type="submit" hidden />
      </form>
    </Dialogo>
  );
}
