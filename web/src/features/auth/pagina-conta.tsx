"use client";

import { useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/ui/button";
import { Cabecalho, Cartao } from "@/components/ui/diversos";
import { Dialogo } from "@/components/ui/dialog";
import { sair } from "@/features/auth/acoes";
import { useSessao } from "@/features/auth/contexto";
import { FormularioTrocarSenha } from "@/features/auth/formulario-trocar-senha";
import { NOME_PAPEL } from "@/lib/auth/permissoes";

export function PaginaConta() {
  const { usuario, clinica } = useSessao();
  const router = useRouter();
  const [confirmar, setConfirmar] = React.useState(false);
  const [saindo, setSaindo] = React.useState(false);

  async function sairDeTodos() {
    setSaindo(true);
    try {
      await sair(true);
    } finally {
      router.replace("/login?motivo=saiu");
      router.refresh();
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Cabecalho titulo="Minha conta" descricao={`${usuario.nome} · ${clinica.nome}`} />
      <Cartao className="mb-4 p-5">
        <h2 className="mb-1 font-semibold">Perfis</h2>
        <p className="text-texto-suave text-sm">{usuario.papeis.map((p) => NOME_PAPEL[p]).join(", ")}</p>
      </Cartao>
      <Cartao className="mb-4 p-5">
        <h2 className="mb-4 font-semibold">Trocar senha</h2>
        <div className="max-w-sm">
          <FormularioTrocarSenha />
        </div>
      </Cartao>
      <Cartao className="p-5">
        <h2 className="mb-1 font-semibold">Sessões</h2>
        <p className="text-texto-suave mb-4 text-sm">
          Esqueceu o painel aberto em outro computador? Encerre todas as sessões, inclusive esta.
        </p>
        <Button variante="secundaria" onClick={() => setConfirmar(true)}>
          Sair de todos os dispositivos
        </Button>
      </Cartao>
      <Dialogo
        aberto={confirmar}
        aoMudar={setConfirmar}
        titulo="Sair de todos os dispositivos?"
        descricao="Você precisará entrar de novo em todos os computadores e celulares."
        rodape={
          <>
            <Button variante="secundaria" onClick={() => setConfirmar(false)}>
              Voltar
            </Button>
            <Button variante="perigo" carregando={saindo} onClick={sairDeTodos}>
              Sair de todos
            </Button>
          </>
        }
      >
        <p className="text-sm">As sessões abertas serão encerradas imediatamente.</p>
      </Dialogo>
    </div>
  );
}
