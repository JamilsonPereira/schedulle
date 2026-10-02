"use client";

import { Lock } from "lucide-react";
import * as React from "react";
import { BlocoSessao } from "@/features/agenda/bloco-sessao";
import {
  bloqueioDaColuna,
  faixaDoBloqueio,
  gradeDoDia,
  minutoNoPonto,
  posicaoDaSessao,
  type Coluna,
  type Faixa,
} from "@/features/agenda/posicionamento";
import { ocupaHorario } from "@/features/agenda/status";
import type { Bloqueio, Sessao } from "@/lib/api/tipos";
import { dataEMinutos, horaDosMinutos } from "@/lib/datas";
import { cn } from "@/lib/utils";

export const PX_POR_MINUTO = 1.2; // 72 px por hora

export interface PropsGrade {
  colunas: Coluna[];
  faixa: Faixa;
  sessoes: Sessao[];
  bloqueios: Bloqueio[];
  fuso: string;
  agora: Date;
  podeArrastar: boolean;
  destacarId: string | null;
  aoClicarHorario: (coluna: Coluna, minuto: number) => void;
  aoAbrirSessao: (s: Sessao) => void;
  aoAbrirBloqueio: (b: Bloqueio) => void;
  aoSoltarSessao: (s: Sessao, coluna: Coluna, minuto: number) => void;
}

/**
 * Grade da agenda: uma coluna por fono (visão Dia) ou por dia (visão Semana).
 * Fora da grade e bloqueios aparecem hachurados; sessões são botões posicionados pelo horário.
 */
export function GradeAgenda(props: PropsGrade) {
  const { colunas, faixa } = props;
  const altura = (faixa.fim - faixa.inicio) * PX_POR_MINUTO;
  const horas: number[] = [];
  for (let m = faixa.inicio; m <= faixa.fim; m += 60) horas.push(m);

  return (
    <div className="rounded-padrao border-borda bg-superficie overflow-x-auto border">
      <div
        className="grid min-w-fit"
        style={{ gridTemplateColumns: `3.5rem repeat(${colunas.length}, minmax(9.5rem, 1fr))` }}
      >
        <div className="border-borda bg-superficie sticky top-0 left-0 z-20 border-b" />
        {colunas.map((c) => (
          <div
            key={c.chave}
            className="border-borda bg-superficie sticky top-0 z-10 border-b border-l px-2 py-2 text-sm font-medium"
          >
            <CabecalhoColuna coluna={c} colunas={colunas} />
          </div>
        ))}

        <div className="bg-superficie sticky left-0 z-10" style={{ height: altura }} aria-hidden>
          <div className="relative h-full">
            {horas
              .filter((m) => m < faixa.fim)
              .map((m) => (
                <span
                  key={m}
                  className={cn(
                    "text-texto-suave absolute right-2 text-[11px]",
                    m > faixa.inicio && "-translate-y-1/2",
                  )}
                  style={{ top: (m - faixa.inicio) * PX_POR_MINUTO }}
                >
                  {horaDosMinutos(m)}
                </span>
              ))}
          </div>
        </div>
        {colunas.map((c) => (
          <CorpoColuna key={c.chave} coluna={c} altura={altura} {...props} />
        ))}
      </div>
    </div>
  );
}

function CabecalhoColuna({ coluna, colunas }: { coluna: Coluna; colunas: Coluna[] }) {
  const mesmaData = colunas.every((c) => c.data === colunas[0].data);
  if (mesmaData) return <span className="block truncate">{coluna.profissional.nome}</span>;
  const [, mes, dia] = coluna.data.split("-");
  const nomes = ["dom", "seg", "ter", "qua", "qui", "sex", "sáb"];
  const dow = new Date(`${coluna.data}T12:00:00Z`).getUTCDay();
  return (
    <span className="block">
      {nomes[dow]} {dia}/{mes}
    </span>
  );
}

function CorpoColuna({
  coluna,
  altura,
  faixa,
  sessoes,
  bloqueios,
  fuso,
  agora,
  podeArrastar,
  destacarId,
  aoClicarHorario,
  aoAbrirSessao,
  aoAbrirBloqueio,
  aoSoltarSessao,
}: PropsGrade & { coluna: Coluna; altura: number }) {
  const ref = React.useRef<HTMLDivElement>(null);
  const [alvo, setAlvo] = React.useState<number | null>(null);
  const px = (m: number) => (m - faixa.inicio) * PX_POR_MINUTO;
  const grade = gradeDoDia(coluna.profissional, coluna.data);
  const doDia = sessoes.filter((s) => s.profissionalId === coluna.profissional.id);
  const hoje = dataEMinutos(agora.toISOString(), fuso);

  function minutoDoEvento(clientY: number) {
    const topo = ref.current?.getBoundingClientRect().top ?? 0;
    return minutoNoPonto(clientY - topo, PX_POR_MINUTO, faixa, 15);
  }

  return (
    <div
      ref={ref}
      className="hachurado border-borda relative cursor-pointer border-l"
      style={{ height: altura }}
      onClick={(e) => aoClicarHorario(coluna, minutoDoEvento(e.clientY))}
      onDragOver={(e) => {
        if (!e.dataTransfer.types.includes("application/x-sessao")) return;
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
        setAlvo(minutoDoEvento(e.clientY));
      }}
      onDragLeave={() => setAlvo(null)}
      onDrop={(e) => {
        e.preventDefault();
        setAlvo(null);
        const id = e.dataTransfer.getData("application/x-sessao");
        const sessao = sessoes.find((s) => s.id === id);
        if (sessao) aoSoltarSessao(sessao, coluna, minutoDoEvento(e.clientY));
      }}
      aria-label={`Agenda de ${coluna.profissional.nome} em ${coluna.data}`}
    >
      {/* Expediente (grade) em fundo liso; o resto fica hachurado. */}
      {grade.map((g, i) => (
        <div
          key={i}
          className="bg-superficie absolute inset-x-0"
          style={{
            top: px(Math.max(g.inicio, faixa.inicio)),
            height: (Math.min(g.fim, faixa.fim) - Math.max(g.inicio, faixa.inicio)) * PX_POR_MINUTO,
          }}
          aria-hidden
        />
      ))}
      {/* Linhas de hora e meia hora */}
      <div
        className="pointer-events-none absolute inset-0"
        aria-hidden
        style={{
          backgroundImage: `repeating-linear-gradient(to bottom, var(--borda) 0, var(--borda) 1px, transparent 1px, transparent ${30 * PX_POR_MINUTO}px)`,
          opacity: 0.6,
        }}
      />
      {bloqueios
        .filter((b) => bloqueioDaColuna(b, coluna.profissional.id))
        .map((b) => {
          const f = faixaDoBloqueio(b, coluna.data, fuso);
          if (!f || f.fim <= faixa.inicio || f.inicio >= faixa.fim) return null;
          const inicio = Math.max(f.inicio, faixa.inicio);
          const fim = Math.min(f.fim, faixa.fim);
          return (
            <button
              key={b.id}
              type="button"
              className="hachurado border-borda bg-superficie-2/70 text-texto-suave absolute inset-x-0 z-[5] flex items-start gap-1 border-y px-2 py-1 text-left text-[11px]"
              style={{ top: px(inicio), height: (fim - inicio) * PX_POR_MINUTO }}
              onClick={(e) => {
                e.stopPropagation();
                aoAbrirBloqueio(b);
              }}
              aria-label={`Bloqueado${b.motivo ? `: ${b.motivo}` : ""}`}
            >
              <Lock className="mt-0.5 size-3 shrink-0" aria-hidden />
              <span className="truncate">
                {b.profissionalId === null ? "Clínica fechada" : "Bloqueado"}
                {b.motivo ? ` · ${b.motivo}` : ""}
              </span>
            </button>
          );
        })}
      {alvo !== null && (
        <div
          className="border-primaria pointer-events-none absolute inset-x-1 z-20 rounded border-2 border-dashed"
          style={{ top: px(alvo), height: 15 * PX_POR_MINUTO }}
          aria-hidden
        />
      )}
      {doDia.map((s) => {
        const p = posicaoDaSessao(s, coluna.data, fuso);
        if (!p) return null;
        const inicio = Math.max(p.inicio, faixa.inicio);
        return (
          <BlocoSessao
            key={s.id}
            sessao={s}
            fuso={fuso}
            topo={px(inicio)}
            altura={Math.max((Math.min(p.fim, faixa.fim) - inicio) * PX_POR_MINUTO - 2, 18)}
            arrastavel={
              podeArrastar &&
              ocupaHorario(s.status) &&
              s.status !== "RESERVADA" &&
              Date.parse(s.inicio) > agora.getTime()
            }
            destacar={destacarId === s.id}
            aoAbrir={() => aoAbrirSessao(s)}
            aoIniciarArraste={(e) => {
              e.dataTransfer.setData("application/x-sessao", s.id);
              e.dataTransfer.effectAllowed = "move";
            }}
          />
        );
      })}
      {hoje.data === coluna.data && hoje.minutos >= faixa.inicio && hoje.minutos <= faixa.fim && (
        <div
          className={cn("border-perigo pointer-events-none absolute inset-x-0 z-20 border-t-2")}
          style={{ top: px(hoje.minutos) }}
          aria-hidden
        />
      )}
    </div>
  );
}
