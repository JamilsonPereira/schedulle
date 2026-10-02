import { describe, expect, it } from "vitest";
import { pode, somenteFono } from "@/lib/auth/permissoes";

describe("matriz de permissões", () => {
  it("FONO não edita agenda nem acessa configurações", () => {
    expect(pode(["FONO"], "agenda.editar")).toBe(false);
    expect(pode(["FONO"], "configuracoes.ver")).toBe(false);
    expect(pode(["FONO"], "agenda.registrarPresenca")).toBe(true);
  });

  it("vários papéis recebem a união", () => {
    expect(pode(["FONO", "ADMIN"], "usuarios.gerenciar")).toBe(true);
    expect(somenteFono(["FONO", "RECEPCAO"])).toBe(false);
    expect(somenteFono(["FONO"])).toBe(true);
  });

  it("direitos do titular (LGPD) só para ADMIN", () => {
    expect(pode(["RECEPCAO"], "pacientes.direitosTitular")).toBe(false);
    expect(pode(["ADMIN"], "pacientes.direitosTitular")).toBe(true);
  });
});
