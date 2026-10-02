import type { Metadata } from "next";
import { PaginaConta } from "@/features/auth/pagina-conta";

export const metadata: Metadata = { title: "Minha conta" };

export default function Conta() {
  return <PaginaConta />;
}
