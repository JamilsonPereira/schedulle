"use client";

import { Button } from "@/components/ui/button";
import { Vazio } from "@/components/ui/diversos";

export default function Erro({ reset }: { error: Error; reset: () => void }) {
  return (
    <Vazio titulo="Algo deu errado nesta tela">
      <p className="mb-3">Os dados não foram perdidos. Tente de novo.</p>
      <Button onClick={reset}>Tentar de novo</Button>
    </Vazio>
  );
}
