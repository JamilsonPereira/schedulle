"use client";

import { Dialog as D } from "radix-ui";
import { X } from "lucide-react";
import * as React from "react";
import { cn } from "@/lib/utils";

/** Diálogo modal (Radix): foco preso, Esc fecha, foco volta ao elemento de origem. */
export function Dialogo({
  aberto,
  aoMudar,
  titulo,
  descricao,
  children,
  rodape,
  largura = "max-w-lg",
}: {
  aberto: boolean;
  aoMudar: (aberto: boolean) => void;
  titulo: React.ReactNode;
  descricao?: React.ReactNode;
  children: React.ReactNode;
  rodape?: React.ReactNode;
  largura?: string;
}) {
  return (
    <D.Root open={aberto} onOpenChange={aoMudar}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <D.Content
          className={cn(
            "rounded-padrao border-borda bg-superficie fixed top-1/2 left-1/2 z-50 flex max-h-[90vh] w-[calc(100%-2rem)] -translate-x-1/2 -translate-y-1/2 flex-col border shadow-xl",
            largura,
          )}
        >
          <div className="border-borda flex items-start justify-between gap-4 border-b px-5 py-4">
            <div>
              <D.Title className="text-base font-semibold">{titulo}</D.Title>
              {descricao ? (
                <D.Description className="text-texto-suave mt-1 text-sm">{descricao}</D.Description>
              ) : (
                <D.Description className="sr-only">{titulo}</D.Description>
              )}
            </div>
            <D.Close className="text-texto-suave hover:bg-superficie-2 rounded p-1" aria-label="Fechar">
              <X className="size-4" />
            </D.Close>
          </div>
          <div className="overflow-y-auto px-5 py-4">{children}</div>
          {rodape && <div className="border-borda flex justify-end gap-2 border-t px-5 py-3">{rodape}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}

/** Painel lateral (mesmo Radix Dialog, ancorado à direita). */
export function PainelLateral({
  aberto,
  aoMudar,
  titulo,
  descricao,
  children,
  rodape,
  largura = "max-w-md",
}: {
  aberto: boolean;
  aoMudar: (aberto: boolean) => void;
  titulo: React.ReactNode;
  descricao?: React.ReactNode;
  children: React.ReactNode;
  rodape?: React.ReactNode;
  largura?: string;
}) {
  return (
    <D.Root open={aberto} onOpenChange={aoMudar}>
      <D.Portal>
        <D.Overlay className="fixed inset-0 z-40 bg-black/30" />
        <D.Content
          className={cn(
            "border-borda bg-superficie fixed inset-y-0 right-0 z-50 flex w-full flex-col border-l shadow-xl",
            largura,
          )}
        >
          <div className="border-borda flex items-start justify-between gap-4 border-b px-5 py-4">
            <div>
              <D.Title className="text-base font-semibold">{titulo}</D.Title>
              {descricao ? (
                <D.Description className="text-texto-suave mt-1 text-sm">{descricao}</D.Description>
              ) : (
                <D.Description className="sr-only">{titulo}</D.Description>
              )}
            </div>
            <D.Close className="text-texto-suave hover:bg-superficie-2 rounded p-1" aria-label="Fechar">
              <X className="size-4" />
            </D.Close>
          </div>
          <div className="flex-1 overflow-y-auto px-5 py-4">{children}</div>
          {rodape && <div className="border-borda flex flex-wrap justify-end gap-2 border-t px-5 py-3">{rodape}</div>}
        </D.Content>
      </D.Portal>
    </D.Root>
  );
}
