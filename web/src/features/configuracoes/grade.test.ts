import { describe, expect, it } from "vitest";
import { resumirGrade, validarGrade, type LinhaGrade } from "@/features/configuracoes/grade";

const l = (chave: string, dia: LinhaGrade["dia"], inicio: string, fim: string): LinhaGrade => ({
  chave,
  dia,
  inicio,
  fim,
  recursoId: null,
});

describe("editor de grade", () => {
  it("aceita intervalos encostados", () => {
    expect(validarGrade([l("a", "MONDAY", "08:00", "12:00"), l("b", "MONDAY", "12:00", "18:00")])).toEqual({});
  });

  it("aponta a linha sobreposta e a linha com fim antes do início", () => {
    const erros = validarGrade([
      l("a", "MONDAY", "08:00", "12:00"),
      l("b", "MONDAY", "11:00", "13:00"),
      l("c", "TUESDAY", "10:00", "09:00"),
      l("d", "TUESDAY", "08:00", "09:00"),
    ]);
    expect(Object.keys(erros).sort()).toEqual(["b", "c"]);
  });

  it("resume por dia", () => {
    expect(resumirGrade([l("a", "WEDNESDAY", "08:00:00", "12:00:00"), l("b", "MONDAY", "13:00", "18:00")])).toBe(
      "Seg 13:00–18:00 · Qua 08:00–12:00",
    );
  });
});
