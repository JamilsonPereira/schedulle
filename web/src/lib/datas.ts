import { TZDate } from "@date-fns/tz";
import { format } from "date-fns";
import { ptBR } from "date-fns/locale";
import type { DataIso, DiaSemana, InstanteIso } from "@/lib/api/tipos";

/**
 * Toda conversão de horário do painel passa por aqui (SDD Web, seção 9).
 * A API trabalha em UTC; a tela mostra sempre o fuso da CLÍNICA, nunca o do navegador.
 * Datas "de calendário" (sem hora) circulam como string AAAA-MM-DD para não sofrerem com fuso.
 */

/** Instante → data e hora no fuso da clínica. */
export function paraFusoClinica(instante: InstanteIso | Date, fuso: string): TZDate {
  const base = typeof instante === "string" ? new Date(instante) : instante;
  return new TZDate(base.getTime(), fuso);
}

/** Data (AAAA-MM-DD) + hora (HH:mm) no fuso da clínica → instante ISO em UTC. */
export function deFusoClinica(data: DataIso, hora: string, fuso: string): InstanteIso {
  const [ano, mes, dia] = data.split("-").map(Number);
  const [h, m] = hora.split(":").map(Number);
  return new Date(new TZDate(ano, mes - 1, dia, h, m, 0, fuso).getTime()).toISOString();
}

/** 0h da data no fuso da clínica, em UTC. */
export function inicioDoDia(data: DataIso, fuso: string): InstanteIso {
  return deFusoClinica(data, "00:00", fuso);
}

export function formatarHora(instante: InstanteIso, fuso: string): string {
  return format(paraFusoClinica(instante, fuso), "HH:mm");
}

/** "ter, 07/10" */
export function formatarData(instante: InstanteIso, fuso: string): string {
  return format(paraFusoClinica(instante, fuso), "EEEEEE, dd/MM", { locale: ptBR });
}

/** "ter, 07/10 14:30" */
export function formatarDataHora(instante: InstanteIso, fuso: string): string {
  return format(paraFusoClinica(instante, fuso), "EEEEEE, dd/MM HH:mm", { locale: ptBR });
}

/** Data de calendário AAAA-MM-DD → "07/10/2026" (sem fuso: é só uma data). */
export function formatarDataCalendario(data: DataIso | null | undefined): string {
  if (!data) return "—";
  const [ano, mes, dia] = data.split("-");
  return `${dia}/${mes}/${ano}`;
}

/** "terça-feira, 7 de outubro" para cabeçalhos. */
export function formatarDataLonga(data: DataIso): string {
  return format(dataLocal(data), "EEEE, d 'de' MMMM", { locale: ptBR });
}

/** Rótulo curto da coluna da semana: "ter 07/10". */
export function formatarDiaCurto(data: DataIso): string {
  return format(dataLocal(data), "EEEEEE dd/MM", { locale: ptBR });
}

/** Data de hoje no fuso da clínica. */
export function hojeNoFuso(fuso: string, agora: Date = new Date()): DataIso {
  return format(paraFusoClinica(agora, fuso), "yyyy-MM-dd");
}

/** Data (AAAA-MM-DD) e minutos desde 0h de um instante, no fuso da clínica. */
export function dataEMinutos(instante: InstanteIso, fuso: string): { data: DataIso; minutos: number } {
  const z = paraFusoClinica(instante, fuso);
  return { data: format(z, "yyyy-MM-dd"), minutos: z.getHours() * 60 + z.getMinutes() };
}

export function somarDias(data: DataIso, dias: number): DataIso {
  const d = dataUtc(data);
  d.setUTCDate(d.getUTCDate() + dias);
  return d.toISOString().slice(0, 10);
}

/** Segunda-feira da semana da data (a semana começa na segunda). */
export function inicioDaSemana(data: DataIso): DataIso {
  const dow = dataUtc(data).getUTCDay(); // 0 = domingo
  return somarDias(data, dow === 0 ? -6 : 1 - dow);
}

export function diaDaSemana(data: DataIso): DiaSemana {
  const nomes: DiaSemana[] = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];
  return nomes[dataUtc(data).getUTCDay()];
}

export const NOME_DIA: Record<DiaSemana, string> = {
  MONDAY: "Segunda",
  TUESDAY: "Terça",
  WEDNESDAY: "Quarta",
  THURSDAY: "Quinta",
  FRIDAY: "Sexta",
  SATURDAY: "Sábado",
  SUNDAY: "Domingo",
};

/** "08:00" ou "08:00:00" → minutos desde 0h. */
export function minutosDaHora(hora: string): number {
  const [h, m] = hora.split(":").map(Number);
  return h * 60 + m;
}

/** minutos desde 0h → "08:05". */
export function horaDosMinutos(minutos: number): string {
  const h = Math.floor(minutos / 60);
  const m = minutos % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

function dataUtc(data: DataIso): Date {
  return new Date(`${data}T00:00:00Z`);
}

/** Meio-dia local evita que o format do date-fns troque de dia por causa do fuso do navegador. */
function dataLocal(data: DataIso): Date {
  const [ano, mes, dia] = data.split("-").map(Number);
  return new Date(ano, mes - 1, dia, 12);
}
