"use client";

import { Vazio } from "@/components/ui/diversos";
import { usePode } from "@/features/auth/contexto";

/** Telas de configuração são do ADMIN; a API também recusa (403). */
export function SomenteAdmin({ children }: { children: React.ReactNode }) {
  const pode = usePode("configuracoes.ver");
  if (!pode) return <Vazio titulo="Acesso restrito">Esta área é só para a administração da clínica.</Vazio>;
  return <>{children}</>;
}
