import { describe, expect, it } from "vitest";
import { destinoSeguro } from "@/features/auth/destino";

describe("destino após o login", () => {
  it("aceita rotas internas", () => {
    expect(destinoSeguro("/pacientes?busca=ana")).toBe("/pacientes?busca=ana");
  });

  it("recusa redirecionamento para fora", () => {
    for (const v of [null, "https://evil.com", "//evil.com", "/\\evil.com", "/login?x=1"]) {
      expect(destinoSeguro(v)).toBe("/agenda");
    }
  });
});
