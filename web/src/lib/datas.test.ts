import { describe, expect, it } from "vitest";
import {
  dataEMinutos,
  deFusoClinica,
  diaDaSemana,
  formatarData,
  formatarHora,
  hojeNoFuso,
  inicioDaSemana,
  inicioDoDia,
  somarDias,
} from "@/lib/datas";

/**
 * Rodar também com TZ=UTC e TZ=America/Manaus (npm run test:tz): o painel mostra o fuso da CLÍNICA,
 * nunca o do computador.
 */
describe("datas no fuso da clínica", () => {
  it("converte data e hora da clínica para UTC", () => {
    expect(deFusoClinica("2026-10-05", "14:30", "America/Sao_Paulo")).toBe("2026-10-05T17:30:00.000Z");
    expect(deFusoClinica("2026-10-05", "14:30", "America/Manaus")).toBe("2026-10-05T18:30:00.000Z");
  });

  it("formata no fuso da clínica, não no do navegador", () => {
    expect(formatarHora("2026-10-05T17:30:00Z", "America/Sao_Paulo")).toBe("14:30");
    expect(formatarHora("2026-10-05T17:30:00Z", "America/Manaus")).toBe("13:30");
    expect(formatarData("2026-10-06T02:00:00Z", "America/Sao_Paulo")).toBe("seg, 05/10");
  });

  it("devolve data e minutos do dia no fuso", () => {
    expect(dataEMinutos("2026-10-06T02:00:00Z", "America/Sao_Paulo")).toEqual({ data: "2026-10-05", minutos: 23 * 60 });
  });

  it("início do dia em UTC", () => {
    expect(inicioDoDia("2026-10-05", "America/Sao_Paulo")).toBe("2026-10-05T03:00:00.000Z");
  });

  it("hoje no fuso da clínica vira o dia certo perto da meia-noite UTC", () => {
    expect(hojeNoFuso("America/Sao_Paulo", new Date("2026-10-06T02:00:00Z"))).toBe("2026-10-05");
  });

  it("aritmética de datas de calendário", () => {
    expect(somarDias("2026-10-31", 1)).toBe("2026-11-01");
    expect(inicioDaSemana("2026-10-07")).toBe("2026-10-05"); // quarta → segunda
    expect(inicioDaSemana("2026-10-11")).toBe("2026-10-05"); // domingo → segunda anterior
    expect(diaDaSemana("2026-10-05")).toBe("MONDAY");
  });
});
