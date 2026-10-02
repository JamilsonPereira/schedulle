"use client";

import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Input, Select } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { MensagemErro } from "@/components/ui/diversos";
import { usePode, useSessao } from "@/features/auth/contexto";
import { useCriarBloqueio } from "@/features/agenda/consultas";
import { mensagemDoErro } from "@/lib/api/erros";
import type { DataIso, Profissional } from "@/lib/api/tipos";
import { deFusoClinica, hojeNoFuso } from "@/lib/datas";

/** Bloqueio de agenda (férias, congresso, feriado). FONO só bloqueia a própria agenda. */
export function DialogoBloqueio({
  profissionais,
  inicial,
  aoFechar,
}: {
  profissionais: Profissional[];
  inicial: { profissionalId?: string; data?: DataIso; hora?: string };
  aoFechar: () => void;
}) {
  const { usuario, clinica } = useSessao();
  const clinicaInteira = usePode("bloqueios.clinicaInteira");
  const criar = useCriarBloqueio();
  const opcoes = clinicaInteira ? profissionais : profissionais.filter((p) => p.id === usuario.profissionalId);
  const hoje = hojeNoFuso(clinica.fuso);
  const [profissionalId, setProfissionalId] = React.useState(inicial.profissionalId ?? opcoes[0]?.id ?? "");
  const [dataInicio, setDataInicio] = React.useState(inicial.data ?? hoje);
  const [horaInicio, setHoraInicio] = React.useState(inicial.hora ?? "08:00");
  const [dataFim, setDataFim] = React.useState(inicial.data ?? hoje);
  const [horaFim, setHoraFim] = React.useState(inicial.hora ? somarHora(inicial.hora, 1) : "18:00");
  const [motivo, setMotivo] = React.useState("");
  const [erro, setErro] = React.useState<string | null>(null);

  function enviar(e?: React.FormEvent) {
    e?.preventDefault();
    setErro(null);
    const inicio = deFusoClinica(dataInicio, horaInicio, clinica.fuso);
    const fim = deFusoClinica(dataFim, horaFim, clinica.fuso);
    if (Date.parse(fim) <= Date.parse(inicio)) {
      setErro("O fim deve ser depois do início.");
      return;
    }
    criar.mutate(
      { profissionalId: profissionalId || null, inicio, fim, motivo: motivo.trim() || null },
      {
        onSuccess: () => {
          toast.success("Bloqueio criado. Sessões já marcadas nesse período continuam na agenda.");
          aoFechar();
        },
        onError: (err) => setErro(mensagemDoErro(err)),
      },
    );
  }

  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo="Bloquear horário"
      descricao="O bot e o painel deixam de oferecer horários no período."
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button onClick={() => enviar()} carregando={criar.isPending}>
            Bloquear
          </Button>
        </>
      }
    >
      <form onSubmit={enviar} className="grid gap-4 sm:grid-cols-2" noValidate>
        {erro && (
          <div className="sm:col-span-2">
            <MensagemErro>{erro}</MensagemErro>
          </div>
        )}
        <Campo id="bl-prof" rotulo="Agenda" className="sm:col-span-2">
          <Select value={profissionalId} onChange={(e) => setProfissionalId(e.target.value)}>
            {clinicaInteira && <option value="">Clínica inteira (todos os profissionais)</option>}
            {opcoes.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nome}
              </option>
            ))}
          </Select>
        </Campo>
        <Campo id="bl-data-ini" rotulo="Início (data)">
          <Input type="date" value={dataInicio} onChange={(e) => setDataInicio(e.target.value)} />
        </Campo>
        <Campo id="bl-hora-ini" rotulo="Início (hora)">
          <Input type="time" step={300} value={horaInicio} onChange={(e) => setHoraInicio(e.target.value)} />
        </Campo>
        <Campo id="bl-data-fim" rotulo="Fim (data)">
          <Input type="date" value={dataFim} min={dataInicio} onChange={(e) => setDataFim(e.target.value)} />
        </Campo>
        <Campo id="bl-hora-fim" rotulo="Fim (hora)">
          <Input type="time" step={300} value={horaFim} onChange={(e) => setHoraFim(e.target.value)} />
        </Campo>
        <Campo
          id="bl-motivo"
          rotulo="Motivo (opcional)"
          className="sm:col-span-2"
          dica="Sem dados de pacientes. Ex.: férias, congresso."
        >
          <Input maxLength={200} value={motivo} onChange={(e) => setMotivo(e.target.value)} />
        </Campo>
        <button type="submit" hidden />
      </form>
    </Dialogo>
  );
}

function somarHora(hora: string, horas: number): string {
  const [h, m] = hora.split(":").map(Number);
  return `${String(Math.min(h + horas, 23)).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}
