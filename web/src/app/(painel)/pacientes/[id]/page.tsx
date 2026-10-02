import type { Metadata } from "next";
import { FichaDoPaciente } from "@/features/pacientes/ficha-paciente";

// Título genérico de propósito: nome do paciente não vai para a aba nem para o histórico do navegador.
export const metadata: Metadata = { title: "Ficha do paciente" };

export default async function Ficha({ params }: PageProps<"/pacientes/[id]">) {
  const { id } = await params;
  return <FichaDoPaciente id={id} />;
}
