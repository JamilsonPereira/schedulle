import { describe, expect, it } from "vitest";
import {
  faixaDoBloqueio,
  faixaVisivel,
  minutoNoPonto,
  posicaoDaSessao,
  type Coluna,
} from "@/features/agenda/posicionamento";
import type { Bloqueio, Profissional, Sessao } from "@/lib/api/tipos";

const SP = "America/Sao_Paulo";
const fono: Profissional = {
  id: "f1",
  nome: "Paula",
  registroCrfa: null,
  subareas: ["LINGUAGEM"],
  duracaoPadraoMin: 40,
  usuarioId: null,
  ativo: true,
  grade: [{ dia: "MONDAY", inicio: "08:00", fim: "12:00", recursoId: null }],
  versao: 0,
};
const sessao = (inicio: string, fim: string): Sessao => ({
  id: "s1",
  pacienteId: "p1",
  pacienteNome: "Ana",
  profissionalId: "f1",
  recursoId: null,
  serieId: null,
  tipo: "TERAPIA",
  inicio,
  fim,
  status: "AGENDADA",
  expiraEm: null,
  versao: 0,
});

describe("posicionamento na grade", () => {
  it("posiciona a sessão em minutos do dia no fuso da clínica", () => {
    expect(posicaoDaSessao(sessao("2026-10-05T11:00:00Z", "2026-10-05T11:40:00Z"), "2026-10-05", SP)).toEqual({
      inicio: 8 * 60,
      fim: 8 * 60 + 40,
    });
    expect(posicaoDaSessao(sessao("2026-10-05T11:00:00Z", "2026-10-05T11:40:00Z"), "2026-10-06", SP)).toBeNull();
  });

  it("faixa visível cobre grade e sessões, em horas cheias", () => {
    const colunas: Coluna[] = [{ chave: "f1", data: "2026-10-05", profissional: fono }];
    expect(faixaVisivel(colunas, [], SP)).toEqual({ inicio: 480, fim: 720 });
    // sessão encaixada às 18:20
    const s = sessao("2026-10-05T21:20:00Z", "2026-10-05T22:00:00Z");
    expect(faixaVisivel(colunas, [s], SP)).toEqual({ inicio: 480, fim: 19 * 60 });
  });

  it("sem grade nem sessões usa 07:00–19:00", () => {
    expect(faixaVisivel([{ chave: "x", data: "2026-10-06", profissional: fono }], [], SP)).toEqual({
      inicio: 420,
      fim: 1140,
    });
  });

  it("recorta bloqueios de vários dias", () => {
    const b: Bloqueio = {
      id: "b",
      profissionalId: null,
      inicio: "2026-10-05T15:00:00Z",
      fim: "2026-10-07T15:00:00Z",
      motivo: null,
    };
    expect(faixaDoBloqueio(b, "2026-10-05", SP)).toEqual({ inicio: 12 * 60, fim: 24 * 60 });
    expect(faixaDoBloqueio(b, "2026-10-06", SP)).toEqual({ inicio: 0, fim: 24 * 60 });
    expect(faixaDoBloqueio(b, "2026-10-07", SP)).toEqual({ inicio: 0, fim: 12 * 60 });
    expect(faixaDoBloqueio(b, "2026-10-08", SP)).toBeNull();
  });

  it("converte o ponto clicado em minuto múltiplo de 15", () => {
    const faixa = { inicio: 480, fim: 720 };
    expect(minutoNoPonto(0, 1.2, faixa)).toBe(480);
    expect(minutoNoPonto(1.2 * 50, 1.2, faixa)).toBe(525);
    expect(minutoNoPonto(99999, 1.2, faixa)).toBe(705);
  });
});
