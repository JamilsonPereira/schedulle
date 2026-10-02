import type { Metadata } from "next";
import { PaginaClinica } from "@/features/configuracoes/pagina-clinica";

export const metadata: Metadata = { title: "Clínica" };

export default function Pagina() {
  return <PaginaClinica />;
}
