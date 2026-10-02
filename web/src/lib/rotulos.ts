import type { Demanda, StatusSessao, Subarea, TipoAnexo, TipoRecurso, TipoSessao } from "@/lib/api/tipos";

export const NOME_SUBAREA: Record<Subarea, string> = {
  LINGUAGEM: "Linguagem",
  GAGUEIRA: "Gagueira / fluência",
  VOZ: "Voz",
  DEGLUTICAO: "Deglutição",
  MOTRICIDADE_OROFACIAL: "Motricidade orofacial",
  AUDICAO: "Audição",
  OUTRO: "Outro",
};
export const SUBAREAS = Object.keys(NOME_SUBAREA) as Subarea[];

export const NOME_DEMANDA: Record<Demanda, string> = NOME_SUBAREA;
export const DEMANDAS = SUBAREAS;

export const NOME_TIPO_SESSAO: Record<TipoSessao, string> = {
  AVALIACAO: "Avaliação",
  TERAPIA: "Terapia",
  REPOSICAO: "Reposição",
  DEVOLUTIVA: "Devolutiva",
};
export const TIPOS_SESSAO = Object.keys(NOME_TIPO_SESSAO) as TipoSessao[];

export const NOME_STATUS: Record<StatusSessao, string> = {
  RESERVADA: "Reservada pelo bot",
  AGENDADA: "Agendada",
  CONFIRMADA: "Confirmada",
  ATENDIDA: "Atendida",
  CANCELADA: "Cancelada",
  FALTA_AVISADA: "Falta avisada",
  FALTA_SEM_AVISO: "Falta sem aviso",
};

export const NOME_TIPO_RECURSO: Record<TipoRecurso, string> = { SALA: "Sala", CABINE: "Cabine" };

export const NOME_TIPO_ANEXO: Record<TipoAnexo, string> = {
  PEDIDO_MEDICO: "Pedido médico",
  DOCUMENTO: "Documento",
  OUTRO: "Outro",
};

/** Fusos do Brasil (IANA). A clínica escolhe um; o painel inteiro mostra horários nele. */
export const FUSOS_BRASIL: { valor: string; rotulo: string }[] = [
  { valor: "America/Sao_Paulo", rotulo: "Brasília (SP, RJ, MG, Sul, GO, DF, ES)" },
  { valor: "America/Bahia", rotulo: "Bahia" },
  { valor: "America/Fortaleza", rotulo: "Fortaleza (CE, RN, PB, PI, MA)" },
  { valor: "America/Recife", rotulo: "Recife (PE)" },
  { valor: "America/Maceio", rotulo: "Maceió (AL, SE)" },
  { valor: "America/Belem", rotulo: "Belém (PA leste, AP)" },
  { valor: "America/Santarem", rotulo: "Santarém (PA oeste)" },
  { valor: "America/Araguaina", rotulo: "Araguaína (TO)" },
  { valor: "America/Manaus", rotulo: "Manaus (AM)" },
  { valor: "America/Cuiaba", rotulo: "Cuiabá (MT)" },
  { valor: "America/Campo_Grande", rotulo: "Campo Grande (MS)" },
  { valor: "America/Porto_Velho", rotulo: "Porto Velho (RO)" },
  { valor: "America/Boa_Vista", rotulo: "Boa Vista (RR)" },
  { valor: "America/Rio_Branco", rotulo: "Rio Branco (AC)" },
  { valor: "America/Eirunepe", rotulo: "Eirunepé (AM oeste)" },
  { valor: "America/Noronha", rotulo: "Fernando de Noronha" },
];
