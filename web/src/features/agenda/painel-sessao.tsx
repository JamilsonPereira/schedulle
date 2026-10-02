"use client";

import Link from "next/link";
import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input } from "@/components/ui/campos";
import { PainelLateral } from "@/components/ui/dialog";
import { MensagemErro } from "@/components/ui/diversos";
import { usePode, useSessao } from "@/features/auth/contexto";
import { useAlterarStatus, useRemarcar } from "@/features/agenda/consultas";
import { acoesDaSessao, type AcaoDisponivel } from "@/features/agenda/status";
import { IconeStatus } from "@/features/agenda/bloco-sessao";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import type { AcaoStatus, Sessao } from "@/lib/api/tipos";
import { dataEMinutos, deFusoClinica, formatarDataHora, formatarHora, hojeNoFuso, horaDosMinutos } from "@/lib/datas";
import { NOME_STATUS, NOME_TIPO_SESSAO } from "@/lib/rotulos";

const CONFIRMACOES: Partial<Record<AcaoStatus, string>> = {
  CANCELAR: "Cancelar esta sessão? O horário fica livre para outro paciente.",
  AVISAR_FALTA: "Registrar que o responsável avisou a falta? A reposição segue a política da clínica.",
  REGISTRAR_FALTA_SEM_AVISO: "Registrar falta sem aviso?",
};

export function PainelSessao({
  sessao,
  nomeProfissional,
  nomeSala,
  aoFechar,
}: {
  sessao: Sessao;
  nomeProfissional: string;
  nomeSala: string | null;
  aoFechar: () => void;
}) {
  const { usuario, clinica } = useSessao();
  const fuso = clinica.fuso;
  const verPacientes = usePode("pacientes.ver");
  const alterar = useAlterarStatus();
  const [confirmando, setConfirmando] = React.useState<AcaoStatus | null>(null);
  const [remarcando, setRemarcando] = React.useState(false);
  const acoes = acoesDaSessao(sessao, usuario.papeis);

  function executar(a: AcaoDisponivel) {
    if (a.acao === "REMARCAR") {
      setRemarcando(true);
      return;
    }
    if (CONFIRMACOES[a.acao] && confirmando !== a.acao) {
      setConfirmando(a.acao);
      return;
    }
    setConfirmando(null);
    const acao = a.acao;
    alterar.mutate(
      { sessao, acao },
      {
        onSuccess: (s) => {
          toast.success(`Sessão: ${NOME_STATUS[s.status].toLowerCase()}.`);
          if (acao === "CANCELAR") aoFechar();
        },
      },
    );
  }

  return (
    <PainelLateral
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo={sessao.pacienteNome ?? "Sessão"}
      descricao={`${NOME_TIPO_SESSAO[sessao.tipo]} · ${formatarDataHora(sessao.inicio, fuso)}–${formatarHora(sessao.fim, fuso)}`}
    >
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-texto-suave">Situação</dt>
        <dd className="flex items-center gap-1.5 font-medium">
          <IconeStatus status={sessao.status} /> {NOME_STATUS[sessao.status]}
        </dd>
        <dt className="text-texto-suave">Profissional</dt>
        <dd>{nomeProfissional}</dd>
        <dt className="text-texto-suave">Sala</dt>
        <dd>{nomeSala ?? "—"}</dd>
        {sessao.status === "RESERVADA" && sessao.expiraEm && (
          <>
            <dt className="text-texto-suave">Reserva</dt>
            <dd>O bot aguarda a confirmação do responsável até {formatarHora(sessao.expiraEm, fuso)}.</dd>
          </>
        )}
      </dl>

      {verPacientes && (
        <Button asChild variante="link" className="mt-3">
          <Link href={`/pacientes/${sessao.pacienteId}`}>Abrir ficha do paciente</Link>
        </Button>
      )}

      {acoes.length > 0 && !remarcando && (
        <div className="mt-6 flex flex-col gap-2">
          <h3 className="text-sm font-semibold">Ações</h3>
          {acoes.map((a) => (
            <div key={a.acao}>
              <Button
                variante={a.perigosa ? "perigo" : a.acao === "REMARCAR" ? "secundaria" : "primaria"}
                className="w-full"
                carregando={alterar.isPending && alterar.variables?.acao === a.acao}
                disabled={alterar.isPending}
                onClick={() => executar(a)}
              >
                {confirmando === a.acao ? `Confirmar: ${a.rotulo.toLowerCase()}` : a.rotulo}
              </Button>
              {confirmando === a.acao && (
                <p className="text-texto-suave mt-1 text-xs" role="status">
                  {CONFIRMACOES[a.acao as AcaoStatus]} Clique de novo para confirmar.
                </p>
              )}
            </div>
          ))}
        </div>
      )}
      {acoes.length === 0 && (
        <p className="text-texto-suave mt-6 text-sm">
          {sessao.status === "AGENDADA" || sessao.status === "CONFIRMADA"
            ? "A presença pode ser registrada a partir do início da sessão."
            : "Sem ações disponíveis nesta situação."}
        </p>
      )}

      {remarcando && <FormularioRemarcar sessao={sessao} aoConcluir={() => setRemarcando(false)} />}
    </PainelLateral>
  );
}

function FormularioRemarcar({ sessao, aoConcluir }: { sessao: Sessao; aoConcluir: () => void }) {
  const fuso = useSessao().clinica.fuso;
  const remarcar = useRemarcar();
  const atual = dataEMinutos(sessao.inicio, fuso);
  const [data, setData] = React.useState(atual.data);
  const [hora, setHora] = React.useState(horaDosMinutos(atual.minutos));
  const [encaixe, setEncaixe] = React.useState(false);
  const [erro, setErro] = React.useState<string | null>(null);

  function enviar(e: React.FormEvent) {
    e.preventDefault();
    setErro(null);
    remarcar.mutate(
      { sessao, novoInicio: deFusoClinica(data, hora, fuso), permitirForaDaGrade: encaixe },
      {
        onSuccess: (s) => {
          toast.success(`Sessão remarcada para ${formatarDataHora(s.inicio, fuso)}.`);
          aoConcluir();
        },
        onError: (err) =>
          setErro(
            err instanceof ErroApi && err.codigo === "horario-indisponivel"
              ? "Esse horário está ocupado. Escolha outro."
              : mensagemDoErro(err),
          ),
      },
    );
  }

  return (
    <form onSubmit={enviar} className="rounded-padrao border-borda mt-6 flex flex-col gap-3 border p-3" noValidate>
      <h3 className="text-sm font-semibold">Remarcar</h3>
      {erro && <MensagemErro>{erro}</MensagemErro>}
      <div className="grid grid-cols-2 gap-3">
        <Campo id="rem-data" rotulo="Nova data">
          <Input type="date" min={hojeNoFuso(fuso)} value={data} onChange={(e) => setData(e.target.value)} required />
        </Campo>
        <Campo id="rem-hora" rotulo="Novo horário">
          <Input type="time" step={300} value={hora} onChange={(e) => setHora(e.target.value)} required />
        </Campo>
      </div>
      <Checkbox
        id="rem-encaixe"
        rotulo="Permitir fora da grade (encaixe)"
        checked={encaixe}
        onChange={(e) => setEncaixe(e.target.checked)}
      />
      <div className="flex justify-end gap-2">
        <Button type="button" variante="secundaria" onClick={aoConcluir}>
          Voltar
        </Button>
        <Button type="submit" carregando={remarcar.isPending}>
          Remarcar
        </Button>
      </div>
    </form>
  );
}
