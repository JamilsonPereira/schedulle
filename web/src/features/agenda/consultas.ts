import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/cliente";
import type {
  AcaoStatus,
  Bloqueio,
  DataIso,
  HorarioLivre,
  InstanteIso,
  Sessao,
  TipoSessao,
  Uuid,
} from "@/lib/api/tipos";
import { statusApos } from "@/features/agenda/status";

/** Chaves do cache (SDD Web, seção 8). */
export const chavesAgenda = {
  sessoes: (de: InstanteIso, ate: InstanteIso, profissionalId: Uuid | null) =>
    ["sessoes", { de, ate, profissionalId }] as const,
  bloqueios: (de: InstanteIso, ate: InstanteIso) => ["bloqueios", { de, ate }] as const,
  disponibilidade: (profissionalId: Uuid, data: DataIso, duracaoMin: number | null) =>
    ["disponibilidade", { profissionalId, data, duracaoMin }] as const,
};

export function useSessoes(de: InstanteIso, ate: InstanteIso, profissionalId: Uuid | null) {
  return useQuery({
    queryKey: chavesAgenda.sessoes(de, ate, profissionalId),
    queryFn: () => api<Sessao[]>("agenda/sessoes", { parametros: { de, ate, profissionalId } }),
    staleTime: 30_000,
    // Sem SSE no backend ainda: polling de 30 s como reserva (SDD Web, ADR-05).
    refetchInterval: 30_000,
    placeholderData: (anterior) => anterior,
  });
}

export function useBloqueios(de: InstanteIso, ate: InstanteIso) {
  return useQuery({
    queryKey: chavesAgenda.bloqueios(de, ate),
    queryFn: () => api<Bloqueio[]>("bloqueios", { parametros: { de, ate } }),
    staleTime: 60_000,
    placeholderData: (anterior) => anterior,
  });
}

/** Sempre buscada de novo ao abrir o diálogo (staleTime 0). */
export function useDisponibilidade(profissionalId: Uuid | null, data: DataIso | null, duracaoMin: number | null) {
  return useQuery({
    queryKey: chavesAgenda.disponibilidade(profissionalId ?? "", data ?? "", duracaoMin),
    queryFn: () =>
      api<HorarioLivre[]>("disponibilidade", {
        parametros: { profissionalId, de: data, ate: data, duracaoMin },
      }),
    enabled: Boolean(profissionalId && data),
    staleTime: 0,
  });
}

export interface NovoAgendamento {
  pacienteId: Uuid;
  profissionalId: Uuid;
  tipo: TipoSessao;
  inicio: InstanteIso;
  duracaoMin: number | null;
  recursoId: Uuid | null;
  permitirForaDaGrade: boolean;
}

/** Criar agendamento espera a resposta do servidor (sem update otimista). */
export function useAgendar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: NovoAgendamento) => api<Sessao>("sessoes", { metodo: "POST", corpo: d }),
    meta: { semToast: true },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ["sessoes"] });
      qc.invalidateQueries({ queryKey: ["disponibilidade"] });
    },
  });
}

type Snapshot = [readonly unknown[], Sessao[] | undefined][];

/** Aplica uma mudança em todas as listas de sessões em cache e devolve o estado anterior. */
async function otimista(
  qc: ReturnType<typeof useQueryClient>,
  id: Uuid,
  mudar: (s: Sessao) => Sessao,
): Promise<Snapshot> {
  await qc.cancelQueries({ queryKey: ["sessoes"] });
  const anteriores = qc.getQueriesData<Sessao[]>({ queryKey: ["sessoes"] });
  for (const [chave, lista] of anteriores) {
    if (lista)
      qc.setQueryData(
        chave,
        lista.map((s) => (s.id === id ? mudar(s) : s)),
      );
  }
  return anteriores;
}

function desfazer(qc: ReturnType<typeof useQueryClient>, snapshot?: Snapshot) {
  snapshot?.forEach(([chave, lista]) => qc.setQueryData(chave, lista));
}

/** Confirmar, registrar presença ou falta, cancelar. Otimista: a tela muda na hora e desfaz se der erro. */
export function useAlterarStatus() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { sessao: Sessao; acao: AcaoStatus }) =>
      api<Sessao>(`sessoes/${d.sessao.id}/status`, {
        metodo: "PATCH",
        corpo: { acao: d.acao, versao: d.sessao.versao },
      }),
    onMutate: async (d) => ({
      snapshot: await otimista(qc, d.sessao.id, (s) => ({ ...s, status: statusApos(d.acao) })),
    }),
    onError: (_e, _d, ctx) => desfazer(qc, ctx?.snapshot),
    onSettled: () => qc.invalidateQueries({ queryKey: ["sessoes"] }),
  });
}

/** Remarcar (arrastar ou pelo painel). Otimista; 409 desfaz e recarrega (nunca tenta de novo sozinho). */
export function useRemarcar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { sessao: Sessao; novoInicio: InstanteIso; permitirForaDaGrade?: boolean }) =>
      api<Sessao>(`sessoes/${d.sessao.id}/remarcar`, {
        metodo: "POST",
        corpo: {
          novoInicio: d.novoInicio,
          recursoId: null,
          permitirForaDaGrade: d.permitirForaDaGrade ?? false,
          versao: d.sessao.versao,
        },
      }),
    meta: { semToast: true },
    onMutate: async (d) => {
      const duracao = Date.parse(d.sessao.fim) - Date.parse(d.sessao.inicio);
      const fim = new Date(Date.parse(d.novoInicio) + duracao).toISOString();
      return {
        snapshot: await otimista(qc, d.sessao.id, (s) => ({ ...s, inicio: d.novoInicio, fim, status: "AGENDADA" })),
      };
    },
    onError: (_e, _d, ctx) => desfazer(qc, ctx?.snapshot),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ["sessoes"] });
      qc.invalidateQueries({ queryKey: ["disponibilidade"] });
    },
  });
}

export function useCriarBloqueio() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { profissionalId: Uuid | null; inicio: InstanteIso; fim: InstanteIso; motivo: string | null }) =>
      api<Bloqueio>("bloqueios", { metodo: "POST", corpo: d }),
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: ["bloqueios"] }),
  });
}

export function useRemoverBloqueio() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: Uuid) => api<void>(`bloqueios/${id}`, { metodo: "DELETE" }),
    onSettled: () => qc.invalidateQueries({ queryKey: ["bloqueios"] }),
  });
}
