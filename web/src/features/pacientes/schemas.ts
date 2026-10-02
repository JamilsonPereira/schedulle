import { z } from "zod";
import { DEMANDAS } from "@/lib/rotulos";

const telefone = z
  .string()
  .trim()
  .refine((v) => {
    const digitos = v.replace(/\D/g, "");
    return digitos.length >= 10 && digitos.length <= 13;
  }, "Telefone com DDD, ex.: (11) 99999-0000");

const dataNascimento = z
  .string()
  .refine((v) => v === "" || (/^\d{4}-\d{2}-\d{2}$/.test(v) && v <= new Date().toISOString().slice(0, 10)), {
    message: "Data inválida ou no futuro",
  });

export const novoPacienteSchema = z.object({
  telefoneResponsavel: telefone,
  nomeResponsavel: z.string().trim().max(120, "Máximo de 120 caracteres"),
  nome: z.string().trim().min(2, "Informe o nome do paciente").max(120, "Máximo de 120 caracteres"),
  dataNascimento,
  demanda: z.union([z.enum(DEMANDAS as [string, ...string[]]), z.literal("")]),
  consentimentoColetado: z.boolean(),
});
export type NovoPacienteForm = z.infer<typeof novoPacienteSchema>;

export const editarPacienteSchema = novoPacienteSchema.pick({ nome: true, dataNascimento: true, demanda: true });
export type EditarPacienteForm = z.infer<typeof editarPacienteSchema>;

export const responsavelSchema = z.object({
  nome: z.string().trim().max(120, "Máximo de 120 caracteres"),
  telefone,
});
export type ResponsavelForm = z.infer<typeof responsavelSchema>;
