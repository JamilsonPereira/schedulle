import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/cliente";
import type {
  Clinica,
  IntervaloGrade,
  Papel,
  Politica,
  Profissional,
  Recurso,
  Subarea,
  TipoRecurso,
  Usuario,
  UsuarioComSenhaTemporaria,
  Uuid,
} from "@/lib/api/tipos";

/** Chaves do cache de configurações (SDD Web, seção 8: 10 min de staleTime). */
export const chaves = {
  clinica: ["clinica"] as const,
  recursos: ["recursos"] as const,
  profissionais: (apenasAtivos: boolean) => ["profissionais", { apenasAtivos }] as const,
  usuarios: ["usuarios"] as const,
};
const DEZ_MIN = 10 * 60_000;

// ------------------------------------------------------------------ clínica

export function useClinica() {
  return useQuery({ queryKey: chaves.clinica, queryFn: () => api<Clinica>("clinica"), staleTime: DEZ_MIN });
}

export function useSalvarClinica() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { nome: string; fuso: string; versao: number }) =>
      api<Clinica>("clinica", { metodo: "PUT", corpo: d }),
    onSuccess: (c) => qc.setQueryData(chaves.clinica, c),
    onError: () => qc.invalidateQueries({ queryKey: chaves.clinica }),
  });
}

export function useSalvarPolitica() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: Politica & { versao: number }) => api<Clinica>("clinica/politica", { metodo: "PUT", corpo: d }),
    onSuccess: (c) => qc.setQueryData(chaves.clinica, c),
    onError: () => qc.invalidateQueries({ queryKey: chaves.clinica }),
  });
}

// ------------------------------------------------------------------ salas

export function useRecursos() {
  return useQuery({ queryKey: chaves.recursos, queryFn: () => api<Recurso[]>("recursos"), staleTime: DEZ_MIN });
}

export function useSalvarRecurso() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { id?: Uuid; nome: string; tipo: TipoRecurso; ativo: boolean; versao?: number }) =>
      d.id
        ? api<Recurso>(`recursos/${d.id}`, { metodo: "PUT", corpo: d })
        : api<Recurso>("recursos", { metodo: "POST", corpo: { nome: d.nome, tipo: d.tipo } }),
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: chaves.recursos }),
  });
}

// ------------------------------------------------------------------ profissionais

export function useProfissionais(apenasAtivos = true) {
  return useQuery({
    queryKey: chaves.profissionais(apenasAtivos),
    queryFn: () => api<Profissional[]>("profissionais", { parametros: { apenasAtivos } }),
    staleTime: DEZ_MIN,
  });
}

export interface DadosProfissional {
  nome: string;
  registroCrfa: string | null;
  subareas: Subarea[];
  duracaoPadraoMin: number;
  usuarioId: Uuid | null;
}

export function useSalvarProfissional() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (d: { id?: Uuid; versao?: number; dados: DadosProfissional; grade?: IntervaloGrade[] }) => {
      let p = d.id
        ? await api<Profissional>(`profissionais/${d.id}`, { metodo: "PUT", corpo: { ...d.dados, versao: d.versao } })
        : await api<Profissional>("profissionais", { metodo: "POST", corpo: d.dados });
      if (d.grade) {
        p = await api<Profissional>(`profissionais/${p.id}/grade`, {
          metodo: "PUT",
          corpo: { intervalos: d.grade, versao: p.versao },
        });
      }
      return p;
    },
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: ["profissionais"] }),
  });
}

export function useAlternarProfissional() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (p: Profissional) =>
      api<Profissional>(`profissionais/${p.id}/${p.ativo ? "inativar" : "reativar"}`, {
        metodo: "POST",
        corpo: { versao: p.versao },
      }),
    onSettled: () => qc.invalidateQueries({ queryKey: ["profissionais"] }),
  });
}

// ------------------------------------------------------------------ usuários

export function useUsuarios(habilitado = true) {
  return useQuery({ queryKey: chaves.usuarios, queryFn: () => api<Usuario[]>("usuarios"), enabled: habilitado });
}

export function useCriarUsuario() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { nome: string; email: string; papeis: Papel[] }) =>
      api<UsuarioComSenhaTemporaria>("usuarios", { metodo: "POST", corpo: d }),
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: chaves.usuarios }),
  });
}

export function useAtualizarUsuario() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (d: { id: Uuid; nome: string; papeis: Papel[]; ativo: boolean; versao: number }) =>
      api<Usuario>(`usuarios/${d.id}`, { metodo: "PUT", corpo: d }),
    meta: { semToast: true },
    onSettled: () => qc.invalidateQueries({ queryKey: chaves.usuarios }),
  });
}

export function useRedefinirSenha() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: Uuid) => api<UsuarioComSenhaTemporaria>(`usuarios/${id}/senha-temporaria`, { metodo: "POST" }),
    onSettled: () => qc.invalidateQueries({ queryKey: chaves.usuarios }),
  });
}
