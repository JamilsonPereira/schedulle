"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Campo, Input, Select } from "@/components/ui/campos";
import { Cabecalho, Carregando, Cartao, MensagemErro } from "@/components/ui/diversos";
import { useSalvarClinica, useSalvarPolitica, useClinica } from "@/features/configuracoes/consultas";
import { SomenteAdmin } from "@/features/configuracoes/somente-admin";
import { mensagemDoErro } from "@/lib/api/erros";
import type { Clinica } from "@/lib/api/tipos";
import { FUSOS_BRASIL } from "@/lib/rotulos";

export function PaginaClinica() {
  return (
    <SomenteAdmin>
      <Conteudo />
    </SomenteAdmin>
  );
}

function Conteudo() {
  const { data: clinica, isLoading, error, refetch } = useClinica();
  return (
    <>
      <div className="mx-auto max-w-3xl">
        <Cabecalho titulo="Clínica" descricao="Dados gerais e regras de agendamento usadas pelo bot e pelo painel." />
        {isLoading && <Carregando />}
        {error && (
          <MensagemErro
            acao={
              <Button variante="secundaria" tamanho="pequeno" onClick={() => refetch()}>
                Tentar de novo
              </Button>
            }
          >
            {mensagemDoErro(error)}
          </MensagemErro>
        )}
        {clinica && (
          <div className="flex flex-col gap-4">
            <FormularioDados clinica={clinica} />
            <FormularioPolitica clinica={clinica} />
          </div>
        )}
      </div>
    </>
  );
}

const dadosSchema = z.object({
  nome: z.string().trim().min(2, "Mínimo de 2 caracteres").max(120, "Máximo de 120 caracteres"),
  fuso: z.string().min(1, "Escolha o fuso"),
});

function FormularioDados({ clinica }: { clinica: Clinica }) {
  const salvar = useSalvarClinica();
  const form = useForm<z.infer<typeof dadosSchema>>({
    resolver: zodResolver(dadosSchema),
    values: { nome: clinica.nome, fuso: clinica.fuso },
  });
  const fusoMudou = useWatch({ control: form.control, name: "fuso" }) !== clinica.fuso;

  return (
    <Cartao className="p-5">
      <h2 className="mb-4 font-semibold">Dados da clínica</h2>
      <form
        className="grid gap-4 sm:grid-cols-2"
        noValidate
        onSubmit={form.handleSubmit((d) =>
          salvar.mutate(
            { ...d, versao: clinica.versao },
            {
              onSuccess: (c) =>
                toast.success(
                  c.fuso !== clinica.fuso ? "Salvo. Entre novamente para o painel usar o novo fuso." : "Dados salvos.",
                ),
            },
          ),
        )}
      >
        <Campo id="nome" rotulo="Nome" erro={form.formState.errors.nome?.message}>
          <Input {...form.register("nome")} />
        </Campo>
        <Campo
          id="fuso"
          rotulo="Fuso horário"
          erro={form.formState.errors.fuso?.message}
          dica={fusoMudou ? "Todos os horários do painel e do bot passam a usar este fuso." : undefined}
        >
          <Select {...form.register("fuso")}>
            {FUSOS_BRASIL.map((f) => (
              <option key={f.valor} value={f.valor}>
                {f.rotulo}
              </option>
            ))}
            {!FUSOS_BRASIL.some((f) => f.valor === clinica.fuso) && (
              <option value={clinica.fuso}>{clinica.fuso}</option>
            )}
          </Select>
        </Campo>
        <div className="sm:col-span-2">
          <Button type="submit" carregando={salvar.isPending}>
            Salvar dados
          </Button>
        </div>
      </form>
    </Cartao>
  );
}

const campoNumero = (min: number, max: number) =>
  z
    .string()
    .trim()
    .refine((v) => v === "" || (/^\d+$/.test(v) && Number(v) >= min && Number(v) <= max), {
      message: `Entre ${min} e ${max}, ou vazio para o padrão`,
    });

const politicaSchema = z.object({
  antecedenciaMinimaMin: campoNumero(0, 10080),
  janelaMaximaDias: campoNumero(1, 180),
  passoMin: campoNumero(5, 240),
  ttlReservaMin: campoNumero(1, 30),
  antecedenciaAvisoFaltaHoras: campoNumero(0, 168),
});
type PoliticaForm = z.infer<typeof politicaSchema>;

const CAMPOS_POLITICA: { nome: keyof PoliticaForm; rotulo: string; dica: string }[] = [
  {
    nome: "antecedenciaMinimaMin",
    rotulo: "Antecedência mínima (minutos)",
    dica: "O bot não oferece horários antes disso. Padrão: 720 (12 h).",
  },
  {
    nome: "janelaMaximaDias",
    rotulo: "Janela máxima (dias)",
    dica: "Até quantos dias à frente o bot oferece. Padrão: 30.",
  },
  { nome: "passoMin", rotulo: "Intervalo entre horários (minutos)", dica: "Vazio = duração da sessão." },
  {
    nome: "ttlReservaMin",
    rotulo: "Validade da reserva do bot (minutos)",
    dica: "Tempo para o responsável confirmar. Padrão: 5.",
  },
  {
    nome: "antecedenciaAvisoFaltaHoras",
    rotulo: "Aviso de falta com direito a reposição (horas)",
    dica: "Padrão: 24.",
  },
];

function FormularioPolitica({ clinica }: { clinica: Clinica }) {
  const salvar = useSalvarPolitica();
  const p = clinica.politica;
  const texto = (v: number | null) => (v === null || v === undefined ? "" : String(v));
  const form = useForm<PoliticaForm>({
    resolver: zodResolver(politicaSchema),
    values: {
      antecedenciaMinimaMin: texto(p.antecedenciaMinimaMin),
      janelaMaximaDias: texto(p.janelaMaximaDias),
      passoMin: texto(p.passoMin),
      ttlReservaMin: texto(p.ttlReservaMin),
      antecedenciaAvisoFaltaHoras: texto(p.antecedenciaAvisoFaltaHoras),
    },
  });
  const numero = (v: string) => (v === "" ? null : Number(v));

  return (
    <Cartao className="p-5">
      <h2 className="mb-1 font-semibold">Política de agendamento e faltas</h2>
      <p className="text-texto-suave mb-4 text-sm">Campos vazios usam o padrão do sistema.</p>
      <form
        className="grid gap-4 sm:grid-cols-2"
        noValidate
        onSubmit={form.handleSubmit((d) =>
          salvar.mutate(
            {
              antecedenciaMinimaMin: numero(d.antecedenciaMinimaMin),
              janelaMaximaDias: numero(d.janelaMaximaDias),
              passoMin: numero(d.passoMin),
              ttlReservaMin: numero(d.ttlReservaMin),
              antecedenciaAvisoFaltaHoras: numero(d.antecedenciaAvisoFaltaHoras),
              versao: clinica.versao,
            },
            { onSuccess: () => toast.success("Política salva.") },
          ),
        )}
      >
        {CAMPOS_POLITICA.map((c) => (
          <Campo key={c.nome} id={c.nome} rotulo={c.rotulo} dica={c.dica} erro={form.formState.errors[c.nome]?.message}>
            <Input inputMode="numeric" placeholder="Padrão" {...form.register(c.nome)} />
          </Campo>
        ))}
        <div className="sm:col-span-2">
          <Button type="submit" carregando={salvar.isPending}>
            Salvar política
          </Button>
        </div>
      </form>
    </Cartao>
  );
}
