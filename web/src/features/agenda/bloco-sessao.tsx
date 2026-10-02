"use client";

import { Ban, CalendarClock, Check, CheckCheck, Clock, UserX } from "lucide-react";
import * as React from "react";
import { VISUAL_STATUS } from "@/features/agenda/status";
import type { Sessao, StatusSessao } from "@/lib/api/tipos";
import { formatarHora } from "@/lib/datas";
import { NOME_STATUS, NOME_TIPO_SESSAO } from "@/lib/rotulos";
import { cn } from "@/lib/utils";

const ICONES: Record<StatusSessao, React.ComponentType<{ className?: string }>> = {
  RESERVADA: Clock,
  AGENDADA: CalendarClock,
  CONFIRMADA: Check,
  ATENDIDA: CheckCheck,
  FALTA_AVISADA: UserX,
  FALTA_SEM_AVISO: UserX,
  CANCELADA: Ban,
};

export function IconeStatus({ status, className }: { status: StatusSessao; className?: string }) {
  const Icone = ICONES[status];
  return <Icone className={cn("size-3.5 shrink-0", className)} aria-hidden />;
}

/** Bloco da sessão na grade. É um botão (teclado: Tab chega, Enter abre o painel). */
export function BlocoSessao({
  sessao,
  fuso,
  topo,
  altura,
  arrastavel,
  destacar,
  aoAbrir,
  aoIniciarArraste,
}: {
  sessao: Sessao;
  fuso: string;
  topo: number;
  altura: number;
  arrastavel: boolean;
  destacar: boolean;
  aoAbrir: () => void;
  aoIniciarArraste: (e: React.DragEvent) => void;
}) {
  const visual = VISUAL_STATUS[sessao.status];
  const compacto = altura < 34;
  const horario = `${formatarHora(sessao.inicio, fuso)}–${formatarHora(sessao.fim, fuso)}`;
  const nome = sessao.pacienteNome ?? "Paciente";
  return (
    <button
      type="button"
      data-sessao={sessao.id}
      draggable={arrastavel}
      onDragStart={aoIniciarArraste}
      onClick={(e) => {
        e.stopPropagation();
        aoAbrir();
      }}
      aria-label={`${horario}, ${nome}, ${NOME_TIPO_SESSAO[sessao.tipo]}, ${NOME_STATUS[sessao.status]}`}
      className={cn(
        "absolute right-1 left-1 overflow-hidden rounded-md border-l-4 px-1.5 text-left text-xs shadow-sm",
        visual.classe,
        visual.tracejado && "border border-l-4 border-dashed",
        sessao.status === "CANCELADA" ? "z-0" : "z-10",
        arrastavel && "cursor-grab active:cursor-grabbing",
        destacar && "piscar",
      )}
      style={{ top: topo, height: altura }}
    >
      <span className={cn("flex items-center gap-1 font-semibold", compacto ? "py-0.5" : "pt-1")}>
        <IconeStatus status={sessao.status} />
        <span className={cn("truncate", visual.riscado && "line-through")}>{nome}</span>
      </span>
      {!compacto && (
        <span className="block truncate text-[11px] opacity-90">
          {horario} · {NOME_TIPO_SESSAO[sessao.tipo]}
        </span>
      )}
    </button>
  );
}
