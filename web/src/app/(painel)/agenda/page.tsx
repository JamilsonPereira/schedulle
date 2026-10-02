import type { Metadata } from "next";
import { Suspense } from "react";
import { Carregando } from "@/components/ui/diversos";
import { PaginaAgenda } from "@/features/agenda/pagina-agenda";

export const metadata: Metadata = { title: "Agenda" };

export default function Agenda() {
  return (
    <Suspense fallback={<Carregando texto="Carregando agenda…" />}>
      <PaginaAgenda />
    </Suspense>
  );
}
