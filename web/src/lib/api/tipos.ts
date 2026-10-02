/**
 * Contratos da API Spring (/api/v1). Escritos à mão a partir dos records Java até o backend publicar o
 * OpenAPI (springdoc); quando publicar, troque este arquivo pelo gerado com openapi-typescript (ADR-04).
 * Horários são sempre ISO 8601 em UTC.
 */

export type Uuid = string;
export type InstanteIso = string; // 2026-10-05T17:00:00Z
export type DataIso = string; // 2026-10-05
export type HoraIso = string; // 08:00 ou 08:00:00

export type Papel = "ADMIN" | "RECEPCAO" | "FONO";
export const PAPEIS: Papel[] = ["ADMIN", "RECEPCAO", "FONO"];

// ------------------------------------------------------------------ autenticação

export interface Usuario {
  id: Uuid;
  nome: string;
  email: string;
  papeis: Papel[];
  ativo: boolean;
  precisaTrocarSenha: boolean;
  bloqueado: boolean;
  ultimoLoginEm: InstanteIso | null;
  profissionalId: Uuid | null;
  versao: number;
}

export interface SessaoDeLogin {
  tokenAcesso: string;
  acessoExpiraEm: InstanteIso;
  refreshToken: string;
  refreshExpiraEm: InstanteIso;
  usuario: Usuario;
}

export interface UsuarioComSenhaTemporaria {
  usuario: Usuario;
  senhaTemporaria: string;
}

// ------------------------------------------------------------------ clínica

export interface Politica {
  antecedenciaMinimaMin: number | null;
  janelaMaximaDias: number | null;
  passoMin: number | null;
  ttlReservaMin: number | null;
  antecedenciaAvisoFaltaHoras: number | null;
}

export interface Clinica {
  id: Uuid;
  nome: string;
  fuso: string;
  politica: Politica;
  versao: number;
}

export interface Perfil {
  usuario: Usuario;
  clinica: Clinica;
}

export type DiaSemana = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
export const DIAS_SEMANA: DiaSemana[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"];

export interface IntervaloGrade {
  dia: DiaSemana;
  inicio: HoraIso;
  fim: HoraIso;
  recursoId: Uuid | null;
}

export type Subarea = "LINGUAGEM" | "GAGUEIRA" | "VOZ" | "DEGLUTICAO" | "MOTRICIDADE_OROFACIAL" | "AUDICAO" | "OUTRO";

export interface Profissional {
  id: Uuid;
  nome: string;
  registroCrfa: string | null;
  subareas: Subarea[];
  duracaoPadraoMin: number;
  usuarioId: Uuid | null;
  ativo: boolean;
  grade: IntervaloGrade[];
  versao: number;
}

export type TipoRecurso = "SALA" | "CABINE";

export interface Recurso {
  id: Uuid;
  nome: string;
  tipo: TipoRecurso;
  ativo: boolean;
  versao: number;
}

export interface Bloqueio {
  id: Uuid;
  profissionalId: Uuid | null;
  inicio: InstanteIso;
  fim: InstanteIso;
  motivo: string | null;
}

// ------------------------------------------------------------------ agenda

export type StatusSessao =
  "RESERVADA" | "AGENDADA" | "CONFIRMADA" | "ATENDIDA" | "CANCELADA" | "FALTA_AVISADA" | "FALTA_SEM_AVISO";

export type TipoSessao = "AVALIACAO" | "TERAPIA" | "REPOSICAO" | "DEVOLUTIVA";

export type AcaoStatus =
  "CONFIRMAR_PRESENCA" | "CANCELAR" | "AVISAR_FALTA" | "REGISTRAR_ATENDIMENTO" | "REGISTRAR_FALTA_SEM_AVISO";

/** GET /api/v1/agenda/sessoes (com pacienteNome); as demais rotas de sessão devolvem pacienteNome ausente. */
export interface Sessao {
  id: Uuid;
  pacienteId: Uuid;
  pacienteNome: string | null;
  profissionalId: Uuid;
  recursoId: Uuid | null;
  serieId: Uuid | null;
  tipo: TipoSessao;
  inicio: InstanteIso;
  fim: InstanteIso;
  status: StatusSessao;
  expiraEm: InstanteIso | null;
  versao: number;
}

export interface HorarioLivre {
  inicio: InstanteIso;
  fim: InstanteIso;
  recursoId: Uuid | null;
}

// ------------------------------------------------------------------ pacientes

export type Demanda = Subarea;
export type Canal = "WHATSAPP" | "PAINEL";
export type TipoAnexo = "PEDIDO_MEDICO" | "DOCUMENTO" | "OUTRO";

export interface Pagina<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  totalElementos: number;
}

export interface PacienteResumo {
  id: Uuid;
  nome: string;
  idade: number | null;
  demanda: Demanda | null;
  ativo: boolean;
  responsavelId: Uuid;
  responsavelNome: string | null;
  telefoneMascarado: string;
}

export interface Paciente {
  id: Uuid;
  clinicaId: Uuid;
  responsavelId: Uuid;
  nome: string;
  dataNascimento: DataIso | null;
  idade: number | null;
  demanda: Demanda | null;
  ativo: boolean;
  anonimizado: boolean;
  versao: number;
}

export interface Consentimento {
  versaoTexto: string;
  canal: Canal;
  registradoEm: InstanteIso;
}

export interface Responsavel {
  id: Uuid;
  clinicaId: Uuid;
  nome: string | null;
  telefone: string | null;
  telefoneMascarado: string;
  consentimento: Consentimento | null;
  anonimizado: boolean;
  versao: number;
}

export interface Anexo {
  id: Uuid;
  pacienteId: Uuid;
  tipo: TipoAnexo;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  origem: Canal;
  criadoEm: InstanteIso;
}

export interface FichaPaciente {
  paciente: Paciente;
  responsavel: Responsavel;
  outrosPacientesDoResponsavel: Paciente[];
  anexos: Anexo[];
}

/** RFC 9457 — formato de todos os erros da API. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  alternativas?: HorarioLivre[];
  motivo?: string;
}
