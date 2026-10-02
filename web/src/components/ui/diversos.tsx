import { LoaderCircle } from "lucide-react";
import * as React from "react";
import { cn } from "@/lib/utils";

export function Cartao({ className, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return <div className={cn("rounded-padrao border-borda bg-superficie border", className)} {...props} />;
}

export function Badge({
  className,
  tom = "neutro",
  ...props
}: React.HTMLAttributes<HTMLSpanElement> & { tom?: "neutro" | "sucesso" | "perigo" | "aviso" | "info" }) {
  const tons = {
    neutro: "bg-superficie-2 text-texto-suave",
    sucesso: "bg-[var(--status-confirmada-fundo)] text-[var(--status-confirmada)]",
    perigo: "bg-perigo-suave text-perigo",
    aviso: "bg-[var(--status-falta-avisada-fundo)] text-[var(--status-falta-avisada)]",
    info: "bg-primaria-suave text-primaria",
  };
  return (
    <span
      className={cn("inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium", tons[tom], className)}
      {...props}
    />
  );
}

export function Carregando({ texto = "Carregando…", className }: { texto?: string; className?: string }) {
  return (
    <div role="status" className={cn("text-texto-suave flex items-center gap-2 py-6 text-sm", className)}>
      <LoaderCircle className="size-4 animate-spin" aria-hidden />
      {texto}
    </div>
  );
}

export function Vazio({ titulo, children }: { titulo: string; children?: React.ReactNode }) {
  return (
    <div className="rounded-padrao border-borda flex flex-col items-center gap-2 border border-dashed px-6 py-10 text-center">
      <p className="font-medium">{titulo}</p>
      {children && <div className="text-texto-suave text-sm">{children}</div>}
    </div>
  );
}

export function MensagemErro({ children, acao }: { children: React.ReactNode; acao?: React.ReactNode }) {
  return (
    <div
      role="alert"
      className="rounded-padrao bg-perigo-suave text-perigo flex items-center justify-between gap-4 px-4 py-3 text-sm"
    >
      <span>{children}</span>
      {acao}
    </div>
  );
}

export function Cabecalho({
  titulo,
  descricao,
  acoes,
}: {
  titulo: React.ReactNode;
  descricao?: React.ReactNode;
  acoes?: React.ReactNode;
}) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-xl font-semibold">{titulo}</h1>
        {descricao && <p className="text-texto-suave mt-1 text-sm">{descricao}</p>}
      </div>
      {acoes && <div className="flex flex-wrap gap-2">{acoes}</div>}
    </div>
  );
}

export function Tabela({ className, ...props }: React.TableHTMLAttributes<HTMLTableElement>) {
  return (
    <div className="rounded-padrao border-borda bg-superficie overflow-x-auto border">
      <table className={cn("w-full text-left text-sm", className)} {...props} />
    </div>
  );
}

export function Th({ className, ...props }: React.ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      scope="col"
      className={cn(
        "border-borda bg-superficie-2 text-texto-suave border-b px-3 py-2 text-xs font-semibold tracking-wide uppercase",
        className,
      )}
      {...props}
    />
  );
}

export function Td({ className, ...props }: React.TdHTMLAttributes<HTMLTableCellElement>) {
  return <td className={cn("border-borda border-b px-3 py-2 align-middle", className)} {...props} />;
}
