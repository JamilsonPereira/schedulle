"use client";

import { Tabs } from "radix-ui";
import * as React from "react";
import { cn } from "@/lib/utils";

export const Abas = Tabs.Root;

export function ListaAbas({ className, ...props }: React.ComponentProps<typeof Tabs.List>) {
  return <Tabs.List className={cn("border-borda mb-4 flex gap-1 overflow-x-auto border-b", className)} {...props} />;
}

export function Aba({ className, ...props }: React.ComponentProps<typeof Tabs.Trigger>) {
  return (
    <Tabs.Trigger
      className={cn(
        "text-texto-suave data-[state=active]:border-primaria data-[state=active]:text-texto -mb-px border-b-2 border-transparent px-3 py-2 text-sm whitespace-nowrap data-[state=active]:font-medium",
        className,
      )}
      {...props}
    />
  );
}

export const ConteudoAba = Tabs.Content;
