import type { AcaoStatus, Papel, Sessao, StatusSessao } from "@/lib/api/tipos";
import { pode } from "@/lib/auth/permissoes";

/** Mesmas regras do backend (StatusSessao.podeIrPara e Sessao.*), para só mostrar ações possíveis. */
export function ocupaHorario(status: StatusSessao): boolean {
  return status === "RESERVADA" || status === "AGENDADA" || status === "CONFIRMADA";
}

export interface AcaoDisponivel {
  acao: AcaoStatus | "REMARCAR";
  rotulo: string;
  perigosa?: boolean;
}

/** Ações que a pessoa pode tomar nesta sessão, agora. */
export function acoesDaSessao(sessao: Sessao, papeis: readonly Papel[], agora: Date = new Date()): AcaoDisponivel[] {
  const editar = pode(papeis, "agenda.editar");
  const presenca = pode(papeis, "agenda.registrarPresenca");
  const comecou = agora.getTime() >= Date.parse(sessao.inicio);
  const ativa = sessao.status === "AGENDADA" || sessao.status === "CONFIRMADA";
  const acoes: AcaoDisponivel[] = [];

  if (sessao.status === "AGENDADA" && editar) acoes.push({ acao: "CONFIRMAR_PRESENCA", rotulo: "Confirmar presença" });
  if (ativa && presenca && comecou) {
    acoes.push({ acao: "REGISTRAR_ATENDIMENTO", rotulo: "Registrar atendimento" });
    acoes.push({ acao: "REGISTRAR_FALTA_SEM_AVISO", rotulo: "Falta sem aviso" });
  }
  if (ativa && editar && !comecou) {
    acoes.push({ acao: "REMARCAR", rotulo: "Remarcar" });
    acoes.push({ acao: "AVISAR_FALTA", rotulo: "Registrar aviso de falta" });
  }
  if (ocupaHorario(sessao.status) && editar)
    acoes.push({ acao: "CANCELAR", rotulo: "Cancelar sessão", perigosa: true });
  return acoes;
}

/** Status que o front assume na hora (update otimista) antes da resposta da API. */
export function statusApos(acao: AcaoStatus): StatusSessao {
  switch (acao) {
    case "CONFIRMAR_PRESENCA":
      return "CONFIRMADA";
    case "CANCELAR":
      return "CANCELADA";
    case "AVISAR_FALTA":
      return "FALTA_AVISADA";
    case "REGISTRAR_ATENDIMENTO":
      return "ATENDIDA";
    case "REGISTRAR_FALTA_SEM_AVISO":
      return "FALTA_SEM_AVISO";
  }
}

/** Visual de cada status (SDD Web, 7.1): sempre cor + ícone/texto, nunca só cor. */
export const VISUAL_STATUS: Record<StatusSessao, { classe: string; riscado?: boolean; tracejado?: boolean }> = {
  RESERVADA: {
    classe: "text-[var(--status-reservada)] bg-[var(--status-reservada-fundo)] border-[var(--status-reservada)]",
    tracejado: true,
  },
  AGENDADA: {
    classe: "text-[var(--status-agendada)] bg-[var(--status-agendada-fundo)] border-[var(--status-agendada)]",
  },
  CONFIRMADA: {
    classe: "text-[var(--status-confirmada)] bg-[var(--status-confirmada-fundo)] border-[var(--status-confirmada)]",
  },
  ATENDIDA: {
    classe: "text-[var(--status-atendida)] bg-[var(--status-atendida-fundo)] border-[var(--status-atendida)]",
  },
  FALTA_AVISADA: {
    classe:
      "text-[var(--status-falta-avisada)] bg-[var(--status-falta-avisada-fundo)] border-[var(--status-falta-avisada)]",
    riscado: true,
  },
  FALTA_SEM_AVISO: {
    classe: "text-[var(--status-falta)] bg-[var(--status-falta-fundo)] border-[var(--status-falta)]",
    riscado: true,
  },
  CANCELADA: {
    classe:
      "text-[var(--status-cancelada)] bg-[var(--status-cancelada-fundo)] border-[var(--status-cancelada)] opacity-70",
    riscado: true,
  },
};
