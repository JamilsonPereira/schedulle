import Link from "next/link";
import { Vazio } from "@/components/ui/diversos";

export default function NaoEncontrado() {
  return (
    <Vazio titulo="Página não encontrada">
      <Link href="/agenda" className="text-primaria underline">
        Ir para a agenda
      </Link>
    </Vazio>
  );
}
