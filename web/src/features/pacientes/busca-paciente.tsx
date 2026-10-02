"use client";

import { Search, UserPlus, X } from "lucide-react";
import * as React from "react";
import { Button } from "@/components/ui/button";
import { Input, Label } from "@/components/ui/campos";
import { usePacientes } from "@/features/pacientes/consultas";
import { useDebounce } from "@/lib/use-debounce";
import type { Uuid } from "@/lib/api/tipos";

export interface PacienteEscolhido {
  id: Uuid;
  nome: string;
}

/** Busca de paciente por nome ou telefone, com atalho para cadastro rápido. */
export function BuscaPaciente({
  valor,
  aoEscolher,
  aoCadastrar,
  erro,
}: {
  valor: PacienteEscolhido | null;
  aoEscolher: (p: PacienteEscolhido | null) => void;
  aoCadastrar?: (nomeDigitado: string) => void;
  erro?: string;
}) {
  const [texto, setTexto] = React.useState("");
  const busca = useDebounce(texto.trim(), 250);
  const { data, isFetching } = usePacientes({ busca, ativo: true, pagina: 0, tamanho: 8 }, busca.length >= 2);
  const listaId = React.useId();

  if (valor) {
    return (
      <div className="flex flex-col gap-1.5">
        <Label>Paciente</Label>
        <div className="rounded-padrao border-borda bg-superficie-2 flex items-center justify-between border px-3 py-2 text-sm">
          <span className="font-medium">{valor.nome}</span>
          <Button variante="fantasma" tamanho="pequeno" onClick={() => aoEscolher(null)} aria-label="Trocar paciente">
            <X /> Trocar
          </Button>
        </div>
      </div>
    );
  }

  const resultados = busca.length >= 2 ? (data?.conteudo ?? []) : [];
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor="busca-paciente">Paciente</Label>
      <div className="relative">
        <Search
          className="text-texto-suave pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
          aria-hidden
        />
        <Input
          id="busca-paciente"
          className="pl-9"
          placeholder="Nome do paciente, do responsável ou telefone"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          aria-invalid={erro ? true : undefined}
          aria-controls={listaId}
          autoComplete="off"
        />
      </div>
      {erro && (
        <p className="text-perigo text-xs" role="alert">
          {erro}
        </p>
      )}
      <div id={listaId} aria-live="polite">
        {busca.length >= 2 && (
          <ul className="rounded-padrao border-borda max-h-56 overflow-y-auto border">
            {resultados.map((p) => (
              <li key={p.id}>
                <button
                  type="button"
                  className="hover:bg-superficie-2 focus:bg-superficie-2 flex w-full items-center justify-between px-3 py-2 text-left text-sm"
                  onClick={() => aoEscolher({ id: p.id, nome: p.nome })}
                >
                  <span>
                    <span className="font-medium">{p.nome}</span>
                    {p.idade !== null && <span className="text-texto-suave"> · {p.idade} anos</span>}
                  </span>
                  <span className="text-texto-suave text-xs">
                    {p.responsavelNome ?? "Responsável"} · {p.telefoneMascarado}
                  </span>
                </button>
              </li>
            ))}
            {!isFetching && resultados.length === 0 && (
              <li className="text-texto-suave px-3 py-2 text-sm">Nenhum paciente encontrado.</li>
            )}
          </ul>
        )}
      </div>
      {aoCadastrar && (
        <Button
          type="button"
          variante="link"
          tamanho="pequeno"
          className="self-start"
          onClick={() => aoCadastrar(texto)}
        >
          <UserPlus /> Cadastrar paciente novo
        </Button>
      )}
    </div>
  );
}
