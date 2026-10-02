import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().trim().min(1, "Informe o e-mail").email("E-mail inválido"),
  senha: z.string().min(1, "Informe a senha"),
});
export type LoginForm = z.infer<typeof loginSchema>;

/** Mesmas regras básicas do backend (Senhas.validar); o backend ainda checa senhas comuns. */
export const trocarSenhaSchema = z
  .object({
    senhaAtual: z.string().min(1, "Informe a senha atual"),
    novaSenha: z.string().min(10, "Use pelo menos 10 caracteres").max(128, "Use no máximo 128 caracteres"),
    confirmacao: z.string(),
  })
  .refine((v) => v.novaSenha === v.confirmacao, { path: ["confirmacao"], message: "As senhas não conferem" })
  .refine((v) => v.novaSenha !== v.senhaAtual, {
    path: ["novaSenha"],
    message: "A nova senha deve ser diferente da atual",
  });
export type TrocarSenhaForm = z.infer<typeof trocarSenhaSchema>;
