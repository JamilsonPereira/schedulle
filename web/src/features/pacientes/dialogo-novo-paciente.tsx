"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import * as React from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input, Select } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { MensagemErro } from "@/components/ui/diversos";
import { useCadastrarPaciente } from "@/features/pacientes/consultas";
import { novoPacienteSchema, type NovoPacienteForm } from "@/features/pacientes/schemas";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import type { Demanda, Paciente } from "@/lib/api/tipos";
import { DEMANDAS, NOME_DEMANDA } from "@/lib/rotulos";

/**
 * Cadastro pelo painel (também usado como "criação rápida" no novo agendamento).
 * O responsável é encontrado pelo telefone ou criado; sem consentimento prévio, o termo presencial é obrigatório.
 */
export function DialogoNovoPaciente({
  aoFechar,
  aoCriar,
  nomeInicial = "",
}: {
  aoFechar: () => void;
  aoCriar: (p: Paciente) => void;
  nomeInicial?: string;
}) {
  const cadastrar = useCadastrarPaciente();
  const [erro, setErro] = React.useState<string | null>(null);
  const form = useForm<NovoPacienteForm>({
    resolver: zodResolver(novoPacienteSchema),
    defaultValues: {
      telefoneResponsavel: "",
      nomeResponsavel: "",
      nome: nomeInicial,
      dataNascimento: "",
      demanda: "",
      consentimentoColetado: false,
    },
  });
  const { errors } = form.formState;

  const enviar = form.handleSubmit((d) => {
    setErro(null);
    cadastrar.mutate(
      {
        telefoneResponsavel: d.telefoneResponsavel,
        nomeResponsavel: d.nomeResponsavel || null,
        nome: d.nome,
        dataNascimento: d.dataNascimento || null,
        demanda: (d.demanda || null) as Demanda | null,
        consentimentoColetado: d.consentimentoColetado,
      },
      {
        onSuccess: (p) => {
          toast.success(`${p.nome} cadastrado(a).`);
          aoCriar(p);
        },
        onError: (e) => {
          if (e instanceof ErroApi && e.codigo === "consentimento-ausente") {
            form.setError("consentimentoColetado", { message: e.message });
          } else if (e instanceof ErroApi && e.codigo === "telefone-invalido") {
            form.setError("telefoneResponsavel", { message: e.message });
          } else {
            setErro(mensagemDoErro(e));
          }
        },
      },
    );
  });

  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo="Novo paciente"
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button onClick={enviar} carregando={cadastrar.isPending}>
            Cadastrar
          </Button>
        </>
      }
    >
      <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-2" noValidate>
        {erro && (
          <div className="sm:col-span-2">
            <MensagemErro>{erro}</MensagemErro>
          </div>
        )}
        <Campo id="np-nome" rotulo="Nome do paciente" erro={errors.nome?.message} className="sm:col-span-2">
          <Input autoFocus {...form.register("nome")} />
        </Campo>
        <Campo id="np-nascimento" rotulo="Data de nascimento" erro={errors.dataNascimento?.message}>
          <Input type="date" {...form.register("dataNascimento")} />
        </Campo>
        <Campo id="np-demanda" rotulo="Demanda principal">
          <Select {...form.register("demanda")}>
            <option value="">Não informada</option>
            {DEMANDAS.map((d) => (
              <option key={d} value={d}>
                {NOME_DEMANDA[d]}
              </option>
            ))}
          </Select>
        </Campo>
        <Campo
          id="np-telefone"
          rotulo="WhatsApp do responsável"
          erro={errors.telefoneResponsavel?.message}
          dica="Se já houver responsável com este número, o paciente fica ligado a ele."
        >
          <Input type="tel" inputMode="tel" placeholder="(11) 99999-0000" {...form.register("telefoneResponsavel")} />
        </Campo>
        <Campo id="np-responsavel" rotulo="Nome do responsável" erro={errors.nomeResponsavel?.message}>
          <Input {...form.register("nomeResponsavel")} />
        </Campo>
        <div className="sm:col-span-2">
          <Checkbox
            id="np-consentimento"
            rotulo="O responsável assinou o termo de consentimento (LGPD) na recepção."
            aria-invalid={errors.consentimentoColetado ? true : undefined}
            {...form.register("consentimentoColetado")}
          />
          {errors.consentimentoColetado && (
            <p className="text-perigo mt-1 text-xs" role="alert">
              {errors.consentimentoColetado.message}
            </p>
          )}
          <p className="text-texto-suave mt-1 text-xs">
            Obrigatório se o responsável ainda não deu consentimento pelo WhatsApp.
          </p>
        </div>
        <button type="submit" hidden />
      </form>
    </Dialogo>
  );
}
