import type { Metadata } from "next";
import { PaginaProfissionais } from "@/features/configuracoes/pagina-profissionais";

export const metadata: Metadata = { title: "Profissionais" };

export default function Pagina() {
  return <PaginaProfissionais />;
}
