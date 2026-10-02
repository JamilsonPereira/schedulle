import type { Bloqueio, DataIso, IntervaloGrade, Profissional, Sessao } from "@/lib/api/tipos";
import { dataEMinutos, diaDaSemana, minutosDaHora } from "@/lib/datas";

/**
 * Cálculos puros da grade da agenda (sem React), testados à parte.
 * Tudo em minutos desde 0h, no fuso da clínica.
 */

export interface Faixa {
  inicio: number;
  fim: number;
}

export interface Coluna {
  chave: string;
  data: DataIso;
  profissional: Profissional;
}

/** Intervalos da grade do profissional naquele dia da semana. */
export function gradeDoDia(profissional: Profissional, data: DataIso): (Faixa & { recursoId: string | null })[] {
  const dia = diaDaSemana(data);
  return profissional.grade
    .filter((g: IntervaloGrade) => g.dia === dia)
    .map((g) => ({ inicio: minutosDaHora(g.inicio), fim: minutosDaHora(g.fim), recursoId: g.recursoId }))
    .sort((a, b) => a.inicio - b.inicio);
}

/** Posição de uma sessão na coluna do dia; nulo se não começa naquele dia. */
export function posicaoDaSessao(sessao: Sessao, data: DataIso, fuso: string): Faixa | null {
  const inicio = dataEMinutos(sessao.inicio, fuso);
  if (inicio.data !== data) return null;
  const fim = dataEMinutos(sessao.fim, fuso);
  const fimMin = fim.data === data ? fim.minutos : 24 * 60;
  return { inicio: inicio.minutos, fim: Math.max(fimMin, inicio.minutos + 10) };
}

/** Parte do bloqueio que cai na data (bloqueios podem durar dias). */
export function faixaDoBloqueio(bloqueio: Bloqueio, data: DataIso, fuso: string): Faixa | null {
  const ini = dataEMinutos(bloqueio.inicio, fuso);
  const fim = dataEMinutos(bloqueio.fim, fuso);
  if (fim.data < data || ini.data > data) return null;
  if (fim.data === data && fim.minutos === 0 && ini.data !== data) return null;
  const inicio = ini.data === data ? ini.minutos : 0;
  const termino = fim.data === data ? fim.minutos : 24 * 60;
  return termino > inicio ? { inicio, fim: termino } : null;
}

/** Bloqueio vale para a coluna se é do profissional ou da clínica inteira. */
export function bloqueioDaColuna(b: Bloqueio, profissionalId: string): boolean {
  return b.profissionalId === null || b.profissionalId === profissionalId;
}

/**
 * Faixa de horas exibida: da primeira à última hora com grade ou sessão nas colunas visíveis,
 * arredondada para horas cheias; padrão 07:00–19:00 quando não há nada.
 */
export function faixaVisivel(colunas: Coluna[], sessoes: Sessao[], fuso: string): Faixa {
  let inicio = Infinity;
  let fim = -Infinity;
  for (const c of colunas) {
    for (const g of gradeDoDia(c.profissional, c.data)) {
      inicio = Math.min(inicio, g.inicio);
      fim = Math.max(fim, g.fim);
    }
    for (const s of sessoes) {
      if (s.profissionalId !== c.profissional.id) continue;
      const p = posicaoDaSessao(s, c.data, fuso);
      if (p) {
        inicio = Math.min(inicio, p.inicio);
        fim = Math.max(fim, p.fim);
      }
    }
  }
  if (!Number.isFinite(inicio)) return { inicio: 7 * 60, fim: 19 * 60 };
  return { inicio: Math.floor(inicio / 60) * 60, fim: Math.min(24 * 60, Math.ceil(fim / 60) * 60) };
}

/** Minuto (múltiplo de `passo`) correspondente ao ponto clicado/solto na coluna. */
export function minutoNoPonto(offsetY: number, pxPorMinuto: number, faixa: Faixa, passo = 15): number {
  const bruto = faixa.inicio + offsetY / pxPorMinuto;
  const arredondado = Math.floor(bruto / passo) * passo;
  return Math.max(faixa.inicio, Math.min(faixa.fim - passo, arredondado));
}

/** Se a sessão inteira (início + duração) cabe num intervalo da grade do profissional naquele dia. */
export function dentroDaGrade(profissional: Profissional, data: DataIso, minuto: number, duracaoMin = 0): boolean {
  return gradeDoDia(profissional, data).some((g) => minuto >= g.inicio && minuto + duracaoMin <= g.fim);
}
