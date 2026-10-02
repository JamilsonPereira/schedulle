"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Toaster, toast } from "sonner";
import { EVENTO_SESSAO_EXPIRADA } from "@/lib/api/cliente";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";

/**
 * Cache do servidor (TanStack Query) e toasts. Política de erros (SDD Web, seção 8):
 * 401 → login; 403 → toast; 4xx não repete; 5xx e rede repetem 2 vezes com espera crescente.
 */
function criarCliente() {
  return new QueryClient({
    queryCache: new QueryCache({
      onError: (erro) => {
        if (erro instanceof ErroApi && erro.status === 403) toast.error(erro.message);
      },
    }),
    mutationCache: new MutationCache({
      onError: (erro, _v, _c, mutacao) => {
        if (mutacao.meta?.semToast) return;
        if (erro instanceof ErroApi && erro.status === 401) return;
        toast.error(mensagemDoErro(erro));
      },
    }),
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        retry: (tentativas, erro) => {
          if (erro instanceof ErroApi && erro.status < 500) return false;
          return tentativas < 2;
        },
        retryDelay: (tentativa) => Math.min(1000 * 2 ** tentativa, 8000),
      },
      mutations: { retry: false },
    },
  });
}

export function Provedores({ children }: { children: React.ReactNode }) {
  const [cliente] = React.useState(criarCliente);
  const router = useRouter();

  React.useEffect(() => {
    let redirecionando = false;
    const aoExpirar = () => {
      if (redirecionando) return;
      redirecionando = true;
      cliente.clear();
      const voltar = window.location.pathname + window.location.search;
      router.replace(`/login?motivo=expirada&voltar=${encodeURIComponent(voltar)}`);
    };
    window.addEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar);
    return () => window.removeEventListener(EVENTO_SESSAO_EXPIRADA, aoExpirar);
  }, [cliente, router]);

  return (
    <QueryClientProvider client={cliente}>
      {children}
      <Toaster position="bottom-right" richColors closeButton />
    </QueryClientProvider>
  );
}

declare module "@tanstack/react-query" {
  interface Register {
    mutationMeta: { semToast?: boolean };
  }
}
