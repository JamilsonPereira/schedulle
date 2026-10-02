import type { Metadata } from "next";
import { PaginaSalas } from "@/features/configuracoes/pagina-salas";

export const metadata: Metadata = { title: "Salas" };

export default function Pagina() {
  return <PaginaSalas />;
}
