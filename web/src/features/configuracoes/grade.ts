import { minutosDaHora } from "@/lib/datas";
import type { IntervaloGrade } from "@/lib/api/tipos";

/** Linha do editor de grade: horários como "HH:mm" e uma chave estável para o React. */
export interface LinhaGrade extends IntervaloGrade {
  chave: string;
}

/**
 * Valida a grade como o backend (fim depois do início; sem sobreposição no mesmo dia), para mostrar o erro
 * na linha certa antes de enviar. Devolve um mapa chave → mensagem.
 */
export function validarGrade(linhas: LinhaGrade[]): Record<string, string> {
  const erros: Record<string, string> = {};
  for (const l of linhas) {
    if (!l.inicio || !l.fim) {
      erros[l.chave] = "Informe início e fim";
    } else if (minutosDaHora(l.fim) <= minutosDaHora(l.inicio)) {
      erros[l.chave] = "O fim deve ser depois do início";
    }
  }
  const porDia = new Map<string, LinhaGrade[]>();
  for (const l of linhas) {
    if (erros[l.chave]) continue;
    porDia.set(l.dia, [...(porDia.get(l.dia) ?? []), l]);
  }
  for (const doDia of porDia.values()) {
    const ordenadas = [...doDia].sort((a, b) => minutosDaHora(a.inicio) - minutosDaHora(b.inicio));
    for (let i = 1; i < ordenadas.length; i++) {
      if (minutosDaHora(ordenadas[i].inicio) < minutosDaHora(ordenadas[i - 1].fim)) {
        erros[ordenadas[i].chave] = "Sobrepõe outro intervalo do mesmo dia";
      }
    }
  }
  return erros;
}

/** "08:00:00" → "08:00" (o backend pode devolver com segundos). */
export function horaCurta(hora: string): string {
  return hora.slice(0, 5);
}

/** Resumo para a tabela: "Seg 08:00–12:00, 13:00–18:00 · Qua 08:00–12:00" */
export function resumirGrade(grade: IntervaloGrade[]): string {
  const abrev: Record<string, string> = {
    MONDAY: "Seg",
    TUESDAY: "Ter",
    WEDNESDAY: "Qua",
    THURSDAY: "Qui",
    FRIDAY: "Sex",
    SATURDAY: "Sáb",
    SUNDAY: "Dom",
  };
  const ordem = Object.keys(abrev);
  const porDia = new Map<string, string[]>();
  for (const i of [...grade].sort(
    (a, b) => ordem.indexOf(a.dia) - ordem.indexOf(b.dia) || minutosDaHora(a.inicio) - minutosDaHora(b.inicio),
  )) {
    porDia.set(i.dia, [...(porDia.get(i.dia) ?? []), `${horaCurta(i.inicio)}–${horaCurta(i.fim)}`]);
  }
  return [...porDia.entries()].map(([dia, faixas]) => `${abrev[dia]} ${faixas.join(", ")}`).join(" · ") || "Sem grade";
}
