import type { Metadata } from "next";
import { PaginaUsuarios } from "@/features/configuracoes/pagina-usuarios";

export const metadata: Metadata = { title: "Usuários" };

export default function Pagina() {
  return <PaginaUsuarios />;
}
