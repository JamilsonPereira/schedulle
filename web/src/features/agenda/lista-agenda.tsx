"use client";

import { IconeStatus } from "@/features/agenda/bloco-sessao";
import { posicaoDaSessao, type Coluna } from "@/features/agenda/posicionamento";
import { VISUAL_STATUS } from "@/features/agenda/status";
import type { Sessao } from "@/lib/api/tipos";
import { formatarDiaCurto, formatarHora } from "@/lib/datas";
import { NOME_STATUS, NOME_TIPO_SESSAO } from "@/lib/rotulos";
import { cn } from "@/lib/utils";

/** Agenda em lista para telas pequenas (SDD Web, 9: abaixo de 768 px). */
export function ListaAgenda({
  colunas,
  sessoes,
  fuso,
  aoAbrirSessao,
}: {
  colunas: Coluna[];
  sessoes: Sessao[];
  fuso: string;
  aoAbrirSessao: (s: Sessao) => void;
}) {
  const mesmaData = colunas.every((c) => c.data === colunas[0]?.data);
  return (
    <div className="flex flex-col gap-4">
      {colunas.map((c) => {
        const doGrupo = sessoes
          .filter((s) => s.profissionalId === c.profissional.id && posicaoDaSessao(s, c.data, fuso))
          .sort((a, b) => Date.parse(a.inicio) - Date.parse(b.inicio));
        return (
          <section key={c.chave} aria-label={mesmaData ? c.profissional.nome : formatarDiaCurto(c.data)}>
            <h2 className="mb-2 text-sm font-semibold">{mesmaData ? c.profissional.nome : formatarDiaCurto(c.data)}</h2>
            {doGrupo.length === 0 ? (
              <p className="text-texto-suave text-sm">Sem sessões.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {doGrupo.map((s) => {
                  const v = VISUAL_STATUS[s.status];
                  return (
                    <li key={s.id}>
                      <button
                        type="button"
                        onClick={() => aoAbrirSessao(s)}
                        className={cn(
                          "flex w-full items-center gap-3 rounded-md border-l-4 px-3 py-2 text-left",
                          v.classe,
                        )}
                      >
                        <span className="w-12 shrink-0 text-sm font-semibold">{formatarHora(s.inicio, fuso)}</span>
                        <span className="min-w-0 flex-1">
                          <span className={cn("block truncate font-medium", v.riscado && "line-through")}>
                            {s.pacienteNome ?? "Paciente"}
                          </span>
                          <span className="flex items-center gap-1 text-xs opacity-90">
                            <IconeStatus status={s.status} /> {NOME_STATUS[s.status]} · {NOME_TIPO_SESSAO[s.tipo]}
                          </span>
                        </span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
