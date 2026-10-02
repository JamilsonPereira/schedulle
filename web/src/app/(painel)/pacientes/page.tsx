import type { Metadata } from "next";
import { Suspense } from "react";
import { Carregando } from "@/components/ui/diversos";
import { ListaPacientes } from "@/features/pacientes/lista-pacientes";

export const metadata: Metadata = { title: "Pacientes" };

export default function Pacientes() {
  return (
    <Suspense fallback={<Carregando />}>
      <ListaPacientes />
    </Suspense>
  );
}
