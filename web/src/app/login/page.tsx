import type { Metadata } from "next";
import { Suspense } from "react";
import { FormularioLogin } from "@/features/auth/formulario-login";

export const metadata: Metadata = { title: "Entrar" };

export default function PaginaLogin() {
  return (
    <main className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <p className="text-primaria mb-1 text-sm font-medium">Agenda Fono</p>
        <h1 className="mb-6 text-2xl font-semibold">Entrar no painel</h1>
        <Suspense>
          <FormularioLogin />
        </Suspense>
      </div>
    </main>
  );
}
