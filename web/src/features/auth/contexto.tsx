"use client";

import * as React from "react";
import { pode, somenteFono, type Permissao } from "@/lib/auth/permissoes";
import type { Papel, Uuid } from "@/lib/api/tipos";

export interface UsuarioLogado {
  id: Uuid;
  nome: string;
  papeis: Papel[];
  profissionalId: Uuid | null;
}

export interface ClinicaLogada {
  id: Uuid;
  nome: string;
  fuso: string;
}

interface Valor {
  usuario: UsuarioLogado;
  clinica: ClinicaLogada;
}

const Contexto = React.createContext<Valor | null>(null);

export function ProvedorUsuario({ valor, children }: { valor: Valor; children: React.ReactNode }) {
  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>;
}

export function useSessao(): Valor {
  const v = React.useContext(Contexto);
  if (!v) throw new Error("useSessao fora do painel");
  return v;
}

export function useFuso(): string {
  return useSessao().clinica.fuso;
}

/** usePode('agenda.editar') — esconde o que o perfil não pode usar. Quem decide é a API. */
export function usePode(permissao: Permissao): boolean {
  return pode(useSessao().usuario.papeis, permissao);
}

export function useSomenteFono(): boolean {
  return somenteFono(useSessao().usuario.papeis);
}
