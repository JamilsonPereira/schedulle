import type { HorarioLivre, ProblemDetail } from "@/lib/api/tipos";

/** Erro devolvido pela API (RFC 9457), já com uma mensagem pronta para a tela. */
export class ErroApi extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly problema: ProblemDetail;

  constructor(status: number, problema: ProblemDetail) {
    super(mensagemAmigavel(status, problema));
    this.name = "ErroApi";
    this.status = status;
    this.problema = problema;
    this.codigo = codigoDo(problema) ?? `http-${status}`;
  }

  get alternativas(): HorarioLivre[] {
    return this.problema.alternativas ?? [];
  }
}

/** "/erros/horario-indisponivel" → "horario-indisponivel" */
export function codigoDo(problema: ProblemDetail): string | null {
  const type = problema.type;
  if (!type || type === "about:blank") return null;
  const partes = type.split("/").filter(Boolean);
  return partes.at(-1) ?? null;
}

/** Mensagens fixas para erros cujo texto da API não é o ideal para a recepção. */
const MENSAGENS: Record<string, string> = {
  "horario-indisponivel": "Esse horário acabou de ser ocupado. A agenda foi atualizada.",
  "versao-desatualizada": "Outra pessoa alterou este registro. Recarregamos os dados; confira e tente de novo.",
  "credenciais-invalidas": "E-mail ou senha inválidos, ou acesso temporariamente bloqueado.",
  "sessao-expirada": "Sua sessão expirou. Entre novamente.",
  "consentimento-ausente": "Falta o consentimento do responsável (LGPD). Confirme que o termo foi assinado.",
};

const MOTIVOS_FORA_DA_AGENDA: Record<string, string> = {
  FORA_DA_GRADE: "O horário está fora da grade do profissional.",
  BLOQUEADO: "O horário está bloqueado na agenda.",
  ANTECEDENCIA_MINIMA: "O horário não respeita a antecedência mínima.",
  ALEM_DA_JANELA: "O horário passa da janela máxima de agendamento.",
  NO_PASSADO: "O horário já passou.",
};

export function mensagemAmigavel(status: number, problema: ProblemDetail): string {
  const codigo = codigoDo(problema);
  if (codigo === "horario-fora-da-agenda" && problema.motivo) {
    return MOTIVOS_FORA_DA_AGENDA[problema.motivo] ?? problema.detail ?? "Horário fora da agenda.";
  }
  if (codigo && MENSAGENS[codigo]) return MENSAGENS[codigo];
  if (status === 401) return "Sua sessão expirou. Entre novamente.";
  if (status === 403) return "Você não tem permissão para esta ação.";
  if (status === 404) return problema.detail ?? "Registro não encontrado.";
  if (status >= 500) return "O sistema está instável. Tente de novo em instantes.";
  if (problema.detail && !problema.detail.startsWith("Invalid request")) return problema.detail;
  return "Não foi possível concluir. Confira os dados e tente de novo.";
}

/** Falha de rede (sem resposta da API). */
export class ErroRede extends Error {
  constructor() {
    super("Sem conexão com o servidor. Verifique a internet e tente de novo.");
    this.name = "ErroRede";
  }
}

export function mensagemDoErro(erro: unknown): string {
  if (erro instanceof ErroApi || erro instanceof ErroRede) return erro.message;
  return "Algo deu errado. Tente de novo.";
}
