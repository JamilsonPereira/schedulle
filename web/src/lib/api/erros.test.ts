import { describe, expect, it } from "vitest";
import { codigoDo, ErroApi } from "@/lib/api/erros";

describe("tradutor de erros (Problem Details)", () => {
  it("extrai o código do type", () => {
    expect(codigoDo({ type: "/erros/horario-indisponivel" })).toBe("horario-indisponivel");
    expect(codigoDo({ type: "about:blank" })).toBeNull();
  });

  it("conflito de horário traz alternativas e mensagem própria", () => {
    const e = new ErroApi(409, {
      type: "/erros/horario-indisponivel",
      alternativas: [{ inicio: "2026-10-05T17:00:00Z", fim: "2026-10-05T17:40:00Z", recursoId: null }],
    });
    expect(e.codigo).toBe("horario-indisponivel");
    expect(e.alternativas).toHaveLength(1);
    expect(e.message).toContain("acabou de ser ocupado");
  });

  it("horário fora da agenda explica o motivo", () => {
    const e = new ErroApi(422, { type: "/erros/horario-fora-da-agenda", motivo: "FORA_DA_GRADE" });
    expect(e.message).toBe("O horário está fora da grade do profissional.");
  });

  it("usa o detail da API e mensagens padrão por status", () => {
    expect(new ErroApi(400, { type: "/erros/senha-fraca", detail: "Senha muito comum" }).message).toBe(
      "Senha muito comum",
    );
    expect(new ErroApi(403, {}).message).toContain("permissão");
    expect(new ErroApi(503, {}).message).toContain("instável");
  });
});
