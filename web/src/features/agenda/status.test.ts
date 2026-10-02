import { describe, expect, it } from "vitest";
import { acoesDaSessao } from "@/features/agenda/status";
import type { Sessao } from "@/lib/api/tipos";

const base: Sessao = {
  id: "s",
  pacienteId: "p",
  pacienteNome: "Ana",
  profissionalId: "f",
  recursoId: null,
  serieId: null,
  tipo: "TERAPIA",
  inicio: "2026-10-05T17:00:00Z",
  fim: "2026-10-05T17:40:00Z",
  status: "AGENDADA",
  expiraEm: null,
  versao: 0,
};
const antes = new Date("2026-10-05T12:00:00Z");
const depois = new Date("2026-10-05T17:10:00Z");
const acoes = (s: Sessao, papeis: ("ADMIN" | "RECEPCAO" | "FONO")[], agora: Date) =>
  acoesDaSessao(s, papeis, agora).map((a) => a.acao);

describe("ações da sessão", () => {
  it("recepção antes do início: confirmar, remarcar, aviso de falta e cancelar", () => {
    expect(acoes(base, ["RECEPCAO"], antes)).toEqual(["CONFIRMAR_PRESENCA", "REMARCAR", "AVISAR_FALTA", "CANCELAR"]);
  });

  it("presença só depois do início", () => {
    expect(acoes(base, ["RECEPCAO"], depois)).toContain("REGISTRAR_ATENDIMENTO");
    expect(acoes(base, ["RECEPCAO"], antes)).not.toContain("REGISTRAR_ATENDIMENTO");
  });

  it("FONO só registra presença ou falta", () => {
    expect(acoes(base, ["FONO"], depois)).toEqual(["REGISTRAR_ATENDIMENTO", "REGISTRAR_FALTA_SEM_AVISO"]);
    expect(acoes(base, ["FONO"], antes)).toEqual([]);
  });

  it("estados finais não têm ações", () => {
    expect(acoes({ ...base, status: "ATENDIDA" }, ["ADMIN"], depois)).toEqual([]);
    expect(acoes({ ...base, status: "RESERVADA" }, ["ADMIN"], antes)).toEqual(["CANCELAR"]);
  });
});
