import type { Papel } from "@/lib/api/tipos";

/**
 * Matriz de permissões do painel (SDD Web, seção 5). Só esconde o que o perfil não pode usar:
 * quem decide de fato é sempre a API. Usuário com vários papéis recebe a união das permissões.
 */
export type Permissao =
  | "agenda.verTodos" // agenda de todos os fonos
  | "agenda.editar" // agendar, remarcar, cancelar, confirmar
  | "agenda.registrarPresenca" // registrar atendimento ou falta
  | "bloqueios.clinicaInteira" // bloqueio sem profissional (feriado)
  | "bloqueios.criar"
  | "pacientes.ver"
  | "pacientes.editar"
  | "pacientes.anexar"
  | "pacientes.direitosTitular" // exportação e anonimização (LGPD)
  | "configuracoes.ver"
  | "usuarios.gerenciar";

const MATRIZ: Record<Permissao, Papel[]> = {
  "agenda.verTodos": ["ADMIN", "RECEPCAO"],
  "agenda.editar": ["ADMIN", "RECEPCAO"],
  "agenda.registrarPresenca": ["ADMIN", "RECEPCAO", "FONO"],
  "bloqueios.clinicaInteira": ["ADMIN", "RECEPCAO"],
  "bloqueios.criar": ["ADMIN", "RECEPCAO", "FONO"],
  "pacientes.ver": ["ADMIN", "RECEPCAO", "FONO"],
  "pacientes.editar": ["ADMIN", "RECEPCAO"],
  "pacientes.anexar": ["ADMIN", "RECEPCAO", "FONO"],
  "pacientes.direitosTitular": ["ADMIN"],
  "configuracoes.ver": ["ADMIN"],
  "usuarios.gerenciar": ["ADMIN"],
};

export function pode(papeis: readonly Papel[], permissao: Permissao): boolean {
  return MATRIZ[permissao].some((p) => papeis.includes(p));
}

/** Só FONO (sem ADMIN nem RECEPCAO): enxerga apenas a própria agenda. Mesma regra do backend. */
export function somenteFono(papeis: readonly Papel[]): boolean {
  return papeis.includes("FONO") && !papeis.includes("ADMIN") && !papeis.includes("RECEPCAO");
}

export const NOME_PAPEL: Record<Papel, string> = {
  ADMIN: "Administração",
  RECEPCAO: "Recepção",
  FONO: "Fonoaudiólogo(a)",
};
