"use client";

import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input, Select } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { Carregando, MensagemErro } from "@/components/ui/diversos";
import { useFuso } from "@/features/auth/contexto";
import { useAgendar, useDisponibilidade } from "@/features/agenda/consultas";
import { BuscaPaciente, type PacienteEscolhido } from "@/features/pacientes/busca-paciente";
import { DialogoNovoPaciente } from "@/features/pacientes/dialogo-novo-paciente";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import type { DataIso, HorarioLivre, Profissional, Sessao, TipoSessao } from "@/lib/api/tipos";
import { deFusoClinica, formatarDataHora, formatarHora, hojeNoFuso } from "@/lib/datas";
import { NOME_TIPO_SESSAO, TIPOS_SESSAO } from "@/lib/rotulos";
import { cn } from "@/lib/utils";

export interface InicialAgendamento {
  profissionalId?: string;
  data?: DataIso;
  hora?: string; // HH:mm no fuso da clínica
  paciente?: PacienteEscolhido;
}

/**
 * Novo agendamento (SDD Web, 7.1): paciente → tipo → fono → data → horário livre (GET /disponibilidade).
 * O botão fica desabilitado enquanto a disponibilidade carrega e durante o envio (evita sessão duplicada
 * por clique duplo). Conflito (409) mostra o aviso e recarrega os horários; nunca tenta de novo sozinho.
 */
export function DialogoNovoAgendamento({
  profissionais,
  inicial,
  aoFechar,
  aoAgendar,
}: {
  profissionais: Profissional[];
  inicial: InicialAgendamento;
  aoFechar: () => void;
  aoAgendar?: (s: Sessao) => void;
}) {
  const fuso = useFuso();
  const agendar = useAgendar();
  const ativos = profissionais.filter((p) => p.ativo);
  const [paciente, setPaciente] = React.useState<PacienteEscolhido | null>(inicial.paciente ?? null);
  const [cadastrando, setCadastrando] = React.useState<string | null>(null);
  const [tipo, setTipo] = React.useState<TipoSessao>("TERAPIA");
  const [profissionalId, setProfissionalId] = React.useState(inicial.profissionalId ?? ativos[0]?.id ?? "");
  const [data, setData] = React.useState<DataIso>(inicial.data ?? hojeNoFuso(fuso));
  const profissional = ativos.find((p) => p.id === profissionalId);
  const [duracao, setDuracao] = React.useState<number>(profissional?.duracaoPadraoMin ?? 40);
  const [escolhidoManual, setEscolhido] = React.useState<HorarioLivre | null>(null);
  const [inicialDescartado, setInicialDescartado] = React.useState(false);
  const [encaixe, setEncaixe] = React.useState(false);
  const [horaEncaixe, setHoraEncaixe] = React.useState(inicial.hora ?? "");
  const [erros, setErros] = React.useState<Record<string, string>>({});
  const [erroGeral, setErroGeral] = React.useState<string | null>(null);

  const disponibilidade = useDisponibilidade(profissionalId || null, data, duracao);
  const horarios = disponibilidade.data ?? [];

  // Pré-seleciona o horário clicado na agenda, se estiver livre, até a pessoa mudar algo.
  const preSelecionado =
    !inicialDescartado && inicial.hora && data === inicial.data
      ? (horarios.find((h) => formatarHora(h.inicio, fuso) === inicial.hora) ?? null)
      : null;
  const escolhido = escolhidoManual ?? preSelecionado;

  function trocarProfissional(id: string) {
    setProfissionalId(id);
    setEscolhido(null);
    setInicialDescartado(true);
    const p = ativos.find((x) => x.id === id);
    if (p) setDuracao(p.duracaoPadraoMin);
  }

  function enviar() {
    const e: Record<string, string> = {};
    if (!paciente) e.paciente = "Escolha o paciente";
    if (!profissionalId) e.profissional = "Escolha o profissional";
    if (!encaixe && !escolhido) e.horario = "Escolha um horário livre";
    if (encaixe && !/^\d{2}:\d{2}$/.test(horaEncaixe)) e.horaEncaixe = "Informe o horário";
    if (!Number.isInteger(duracao) || duracao < 10 || duracao > 240) e.duracao = "Entre 10 e 240 minutos";
    setErros(e);
    setErroGeral(null);
    if (Object.keys(e).length || !paciente) return;

    const inicio = encaixe ? deFusoClinica(data, horaEncaixe, fuso) : escolhido!.inicio;
    agendar.mutate(
      {
        pacienteId: paciente.id,
        profissionalId,
        tipo,
        inicio,
        duracaoMin: duracao,
        recursoId: encaixe ? null : (escolhido?.recursoId ?? null),
        permitirForaDaGrade: encaixe,
      },
      {
        onSuccess: (s) => {
          toast.success(`Sessão de ${paciente.nome} agendada para ${formatarDataHora(s.inicio, fuso)}.`);
          aoAgendar?.(s);
          aoFechar();
        },
        onError: (err) => {
          setEscolhido(null);
          setInicialDescartado(true);
          if (err instanceof ErroApi && err.codigo === "horario-indisponivel") {
            toast.warning(err.message);
            setErroGeral("Esse horário acabou de ser ocupado. Escolha outro na lista atualizada.");
          } else {
            setErroGeral(mensagemDoErro(err));
          }
        },
      },
    );
  }

  if (cadastrando !== null) {
    return (
      <DialogoNovoPaciente
        nomeInicial={cadastrando}
        aoFechar={() => setCadastrando(null)}
        aoCriar={(p) => {
          setPaciente({ id: p.id, nome: p.nome });
          setCadastrando(null);
        }}
      />
    );
  }

  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo="Novo agendamento"
      largura="max-w-2xl"
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button onClick={enviar} carregando={agendar.isPending} disabled={!encaixe && disponibilidade.isFetching}>
            Agendar
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {erroGeral && <MensagemErro>{erroGeral}</MensagemErro>}
        <BuscaPaciente
          valor={paciente}
          aoEscolher={setPaciente}
          aoCadastrar={(t) => setCadastrando(t)}
          erro={erros.paciente}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Campo id="ag-tipo" rotulo="Tipo">
            <Select value={tipo} onChange={(e) => setTipo(e.target.value as TipoSessao)}>
              {TIPOS_SESSAO.map((t) => (
                <option key={t} value={t}>
                  {NOME_TIPO_SESSAO[t]}
                </option>
              ))}
            </Select>
          </Campo>
          <Campo id="ag-profissional" rotulo="Profissional" erro={erros.profissional}>
            <Select value={profissionalId} onChange={(e) => trocarProfissional(e.target.value)}>
              {ativos.length === 0 && <option value="">Nenhum profissional ativo</option>}
              {ativos.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.nome}
                </option>
              ))}
            </Select>
          </Campo>
          <Campo id="ag-data" rotulo="Data">
            <Input
              type="date"
              min={hojeNoFuso(fuso)}
              value={data}
              onChange={(e) => {
                if (e.target.value) {
                  setData(e.target.value);
                  setEscolhido(null);
                  setInicialDescartado(true);
                }
              }}
            />
          </Campo>
          <Campo id="ag-duracao" rotulo="Duração (min)" erro={erros.duracao}>
            <Input
              type="number"
              min={10}
              max={240}
              step={5}
              value={duracao}
              onChange={(e) => {
                setDuracao(Number(e.target.value));
                setEscolhido(null);
                setInicialDescartado(true);
              }}
            />
          </Campo>
        </div>

        {!encaixe && (
          <fieldset>
            <legend className="mb-1.5 text-sm font-medium">Horários livres</legend>
            {disponibilidade.isFetching && <Carregando texto="Buscando horários…" className="py-2" />}
            {disponibilidade.error && <MensagemErro>{mensagemDoErro(disponibilidade.error)}</MensagemErro>}
            {!disponibilidade.isFetching && disponibilidade.data && horarios.length === 0 && (
              <p className="text-texto-suave text-sm">
                Nenhum horário livre nesta data. Tente outro dia ou use o encaixe.
              </p>
            )}
            {!disponibilidade.isFetching && horarios.length > 0 && (
              <div className="grid grid-cols-4 gap-2 sm:grid-cols-6" role="group" aria-label="Horários livres">
                {horarios.map((h) => {
                  const ativo = escolhido?.inicio === h.inicio;
                  return (
                    <button
                      key={h.inicio}
                      type="button"
                      aria-pressed={ativo}
                      onClick={() => setEscolhido(h)}
                      className={cn(
                        "rounded-padrao border px-2 py-1.5 text-sm",
                        ativo
                          ? "border-primaria bg-primaria text-primaria-texto"
                          : "border-borda hover:bg-superficie-2",
                      )}
                    >
                      {formatarHora(h.inicio, fuso)}
                    </button>
                  );
                })}
              </div>
            )}
            {erros.horario && (
              <p className="text-perigo mt-1 text-xs" role="alert">
                {erros.horario}
              </p>
            )}
          </fieldset>
        )}

        <div className="rounded-padrao border-borda border p-3">
          <Checkbox
            id="ag-encaixe"
            rotulo="Encaixe fora da grade (horário livre na agenda, mas fora do expediente do profissional)"
            checked={encaixe}
            onChange={(e) => setEncaixe(e.target.checked)}
          />
          {encaixe && (
            <Campo id="ag-hora-encaixe" rotulo="Horário" erro={erros.horaEncaixe} className="mt-3 max-w-40">
              <Input type="time" step={300} value={horaEncaixe} onChange={(e) => setHoraEncaixe(e.target.value)} />
            </Campo>
          )}
        </div>
      </div>
    </Dialogo>
  );
}
