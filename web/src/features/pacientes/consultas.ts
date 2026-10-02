import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/cliente";
import type {
  Anexo,
  DataIso,
  Demanda,
  FichaPaciente,
  Paciente,
  PacienteResumo,
  Pagina,
  Responsavel,
  Sessao,
  TipoAnexo,
  Uuid,
} from "@/lib/api/tipos";

/** API pública da feature de pacientes (usada também pela agenda). */
export const chavesPacientes = {
  lista: (filtros: { busca: string; ativo: boolean | null; pagina: number; tamanho: number }) =>
    ["pacientes", filtros] as const,
  ficha: (id: Uuid) => ["paciente", id] as const,
  sessoes: (id: Uuid) => ["paciente", id, "sessoes"] as const,
};

export function usePacientes(
  filtros: { busca: string; ativo: boolean | null; pagina: number; tamanho?: number },
  habilitado = true,
) {
  const f = { busca: filtros.busca, ativo: filtros.ativo, pagina: filtros.pagina, tamanho: filtros.tamanho ?? 25 };
  return useQuery({
    queryKey: chavesPacientes.lista(f),
    queryFn: ({ signal }) =>
      api<Pagina<PacienteResumo>>("pacientes", {
        parametros: { busca: f.busca, ativo: f.ativo, pagina: f.pagina, tamanho: f.tamanho },
        sinal: signal,
      }),
    staleTime: 60_000,
    placeholderData: (anterior) => anterior,
    enabled: habilitado,
  });
}

export function useFicha(id: Uuid) {
  return useQuery({
    queryKey: chavesPacientes.ficha(id),
    queryFn: () => api<FichaPaciente>(`pacientes/${id}`),
    staleTime: 60_000,
  });
}

/** Sessões do paciente de 1 ano atrás a 3 meses à frente, mais recentes primeiro. */
export function useSessoesDoPaciente(id: Uuid) {
  return useQuery({
    queryKey: chavesPacientes.sessoes(id),
    queryFn: () => {
      const agora = Date.now();
      const dia = 86_400_000;
      return api<Sessao[]>("agenda/sessoes", {
        parametros: {
          pacienteId: id,
          de: new Date(agora - 365 * dia).toISOString(),
          ate: new Date(agora + 90 * dia).toISOString(),
        },
      });
    },
    staleTime: 30_000,
  });
}

export interface NovoPaciente {
  telefoneResponsavel: string;
  nomeResponsavel: string | null;
  nome: string;
  dataNascimento: DataIso | null;
  demanda: Demanda | null;
  consentimentoColetado: boolean;
}

export function useCadastrarPaciente() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: NovoPaciente) => api<Paciente>("pacientes", { metodo: "POST", corpo: d }),
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: ["pacientes"] }),
  });
}

export function useAtualizarPaciente(id: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { nome: string; dataNascimento: DataIso | null; demanda: Demanda | null; versao: number }) =>
      api<Paciente>(`pacientes/${id}`, { metodo: "PUT", corpo: d }),
    meta: { semToast: true },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: chavesPacientes.ficha(id) });
      qc.invalidateQueries({ queryKey: ["pacientes"] });
    },
  });
}

export function useAlternarAtivo(id: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: Paciente) =>
      api<Paciente>(`pacientes/${id}/${p.ativo ? "inativar" : "reativar"}`, {
        metodo: "POST",
        corpo: { versao: p.versao },
      }),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: chavesPacientes.ficha(id) });
      qc.invalidateQueries({ queryKey: ["pacientes"] });
    },
  });
}

export function useAtualizarResponsavel(pacienteId: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { id: Uuid; nome: string | null; telefone: string; versao: number }) =>
      api<Responsavel>(`responsaveis/${d.id}`, { metodo: "PUT", corpo: d }),
    meta: { semToast: true },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: chavesPacientes.ficha(pacienteId) });
      qc.invalidateQueries({ queryKey: ["pacientes"] });
    },
  });
}

export function useConsentimento(pacienteId: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { responsavelId: Uuid; registrar: boolean }) =>
      api<Responsavel>(`responsaveis/${d.responsavelId}/consentimento`, { metodo: d.registrar ? "POST" : "DELETE" }),
    onSettled: () => qc.invalidateQueries({ queryKey: chavesPacientes.ficha(pacienteId) }),
  });
}

export function useAnexar(pacienteId: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { arquivo: File; tipo: TipoAnexo }) => {
      const form = new FormData();
      form.append("arquivo", d.arquivo);
      form.append("tipo", d.tipo);
      return api<Anexo>(`pacientes/${pacienteId}/anexos`, { metodo: "POST", formulario: form });
    },
    onSettled: () => qc.invalidateQueries({ queryKey: chavesPacientes.ficha(pacienteId) }),
  });
}

export function useRemoverAnexo(pacienteId: Uuid) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (anexoId: Uuid) => api<void>(`anexos/${anexoId}`, { metodo: "DELETE" }),
    onSettled: () => qc.invalidateQueries({ queryKey: chavesPacientes.ficha(pacienteId) }),
  });
}

export function useAnonimizar() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (responsavelId: Uuid) => api<void>(`responsaveis/${responsavelId}/anonimizacao`, { metodo: "POST" }),
    onSettled: () => qc.invalidateQueries(),
  });
}
