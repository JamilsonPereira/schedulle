"use client";

import { ChevronLeft, ChevronRight, Lock, Plus, RefreshCw } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Checkbox, Input, Select } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { Carregando, MensagemErro, Vazio } from "@/components/ui/diversos";
import { usePode, useSessao, useSomenteFono } from "@/features/auth/contexto";
import { useBloqueios, useRemarcar, useRemoverBloqueio, useSessoes } from "@/features/agenda/consultas";
import { DialogoBloqueio } from "@/features/agenda/dialogo-bloqueio";
import { DialogoNovoAgendamento, type InicialAgendamento } from "@/features/agenda/dialogo-novo-agendamento";
import { GradeAgenda } from "@/features/agenda/grade-agenda";
import { ListaAgenda } from "@/features/agenda/lista-agenda";
import { IconeStatus } from "@/features/agenda/bloco-sessao";
import { PainelSessao } from "@/features/agenda/painel-sessao";
import { dentroDaGrade, faixaVisivel, type Coluna } from "@/features/agenda/posicionamento";
import { useProfissionais, useRecursos } from "@/features/configuracoes/consultas";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import type { Bloqueio, DataIso, Profissional, Sessao, StatusSessao } from "@/lib/api/tipos";
import {
  dataEMinutos,
  deFusoClinica,
  formatarDataHora,
  formatarDataLonga,
  hojeNoFuso,
  horaDosMinutos,
  inicioDaSemana,
  inicioDoDia,
  somarDias,
} from "@/lib/datas";
import { NOME_STATUS } from "@/lib/rotulos";
import { cn } from "@/lib/utils";

type Visao = "dia" | "semana";

/** Estado da tela na URL (SDD Web, 8): /agenda?data=2026-10-05&visao=dia&fonos=a,b */
function useEstadoNaUrl(fuso: string) {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const data = params.get("data") ?? hojeNoFuso(fuso);
  const visao: Visao = params.get("visao") === "semana" ? "semana" : "dia";
  const fonos = (params.get("fonos") ?? "").split(",").filter(Boolean);
  const canceladas = params.get("canceladas") === "1";
  const novo = params.get("novo") === "1";

  const atualizar = React.useCallback(
    (mudancas: Record<string, string | null>) => {
      const p = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(mudancas)) {
        if (v === null || v === "") p.delete(k);
        else p.set(k, v);
      }
      router.replace(`${pathname}?${p.toString()}`, { scroll: false });
    },
    [params, router, pathname],
  );
  return { data, visao, fonos, canceladas, novo, atualizar };
}

export function PaginaAgenda() {
  const { usuario, clinica } = useSessao();
  const fuso = clinica.fuso;
  const soFono = useSomenteFono();
  const podeEditar = usePode("agenda.editar");
  const podeBloquear = usePode("bloqueios.criar");
  const bloqueiaTudo = usePode("bloqueios.clinicaInteira");
  const estado = useEstadoNaUrl(fuso);
  const profissionaisQ = useProfissionais(true);
  const recursosQ = useRecursos();
  const remarcar = useRemarcar();
  const removerBloqueio = useRemoverBloqueio();

  const [novo, setNovo] = React.useState<InicialAgendamento | null>(null);
  const [bloqueio, setBloqueio] = React.useState<{ profissionalId?: string; data?: DataIso; hora?: string } | null>(
    null,
  );
  const [sessaoAbertaId, setSessaoAbertaId] = React.useState<string | null>(null);
  const [bloqueioAberto, setBloqueioAberto] = React.useState<Bloqueio | null>(null);
  const [arraste, setArraste] = React.useState<{ sessao: Sessao; novoInicio: string; foraDaGrade: boolean } | null>(
    null,
  );
  const [destacarId, setDestacarId] = React.useState<string | null>(null);
  const [agora, setAgora] = React.useState(() => new Date());

  React.useEffect(() => {
    const t = setInterval(() => setAgora(new Date()), 60_000);
    return () => clearInterval(t);
  }, []);

  // "Novo agendamento" do topo (/agenda?novo=1) e atalho N.
  const dialogoNovo = novo ?? (estado.novo && podeEditar ? {} : null);
  function fecharNovo() {
    setNovo(null);
    if (estado.novo) estado.atualizar({ novo: null });
  }
  React.useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      const alvo = e.target as HTMLElement;
      if (["INPUT", "TEXTAREA", "SELECT"].includes(alvo.tagName) || alvo.isContentEditable) return;
      if (e.key.toLowerCase() === "n" && !e.ctrlKey && !e.metaKey && !e.altKey && podeEditar) {
        e.preventDefault();
        setNovo({});
      }
    };
    window.addEventListener("keydown", aoTeclar);
    return () => window.removeEventListener("keydown", aoTeclar);
  }, [podeEditar]);

  // Profissionais visíveis: FONO só a própria agenda; nos demais, filtro da URL.
  const todos = React.useMemo(() => profissionaisQ.data ?? [], [profissionaisQ.data]);
  const visiveis: Profissional[] = React.useMemo(() => {
    if (soFono) return todos.filter((p) => p.id === usuario.profissionalId);
    if (estado.visao === "semana") {
      const escolhido = todos.find((p) => p.id === estado.fonos[0]) ?? todos[0];
      return escolhido ? [escolhido] : [];
    }
    const filtrados = todos.filter((p) => estado.fonos.includes(p.id));
    return filtrados.length ? filtrados : todos;
  }, [soFono, todos, usuario.profissionalId, estado.visao, estado.fonos]);

  const primeiroDia = estado.visao === "semana" ? inicioDaSemana(estado.data) : estado.data;
  const qtdDias = estado.visao === "semana" ? 7 : 1;
  const de = inicioDoDia(primeiroDia, fuso);
  const ate = inicioDoDia(somarDias(primeiroDia, qtdDias), fuso);
  const filtroProfissional = visiveis.length === 1 ? visiveis[0].id : null;
  const sessoesQ = useSessoes(de, ate, filtroProfissional);
  const bloqueiosQ = useBloqueios(de, ate);

  const sessoes = React.useMemo(
    () => (sessoesQ.data ?? []).filter((s) => estado.canceladas || s.status !== "CANCELADA"),
    [sessoesQ.data, estado.canceladas],
  );

  const colunas: Coluna[] = React.useMemo(() => {
    if (estado.visao === "dia") return visiveis.map((p) => ({ chave: p.id, data: estado.data, profissional: p }));
    const p = visiveis[0];
    if (!p) return [];
    const dias = Array.from({ length: 7 }, (_, i) => somarDias(primeiroDia, i));
    // Domingo só aparece se houver grade ou sessão nele.
    return dias
      .filter(
        (d, i) =>
          i < 6 ||
          p.grade.some((g) => g.dia === "SUNDAY") ||
          sessoes.some((s) => dataEMinutos(s.inicio, fuso).data === d),
      )
      .map((d) => ({ chave: d, data: d, profissional: p }));
  }, [estado.visao, estado.data, visiveis, primeiroDia, sessoes, fuso]);

  const faixa = faixaVisivel(colunas, sessoes, fuso);
  const sessaoAberta = sessaoAbertaId ? (sessoesQ.data ?? []).find((s) => s.id === sessaoAbertaId) : null;
  const nomeProfissional = (id: string) => todos.find((p) => p.id === id)?.nome ?? "Profissional";
  const nomeSala = (id: string | null) => (id ? (recursosQ.data?.find((r) => r.id === id)?.nome ?? null) : null);

  function navegar(dias: number) {
    estado.atualizar({ data: somarDias(estado.data, dias) });
  }

  function aoClicarHorario(coluna: Coluna, minuto: number) {
    const hora = horaDosMinutos(minuto);
    if (podeEditar) {
      setNovo({ profissionalId: coluna.profissional.id, data: coluna.data, hora });
    } else if (podeBloquear) {
      setBloqueio({ profissionalId: coluna.profissional.id, data: coluna.data, hora });
    }
  }

  function aoSoltarSessao(sessao: Sessao, coluna: Coluna, minuto: number) {
    if (coluna.profissional.id !== sessao.profissionalId) {
      toast.info("Para trocar de profissional, cancele a sessão e agende de novo.");
      return;
    }
    const novoInicio = deFusoClinica(coluna.data, horaDosMinutos(minuto), fuso);
    if (Date.parse(novoInicio) === Date.parse(sessao.inicio)) return;
    const duracao = (Date.parse(sessao.fim) - Date.parse(sessao.inicio)) / 60_000;
    setArraste({ sessao, novoInicio, foraDaGrade: !dentroDaGrade(coluna.profissional, coluna.data, minuto, duracao) });
  }

  function confirmarArraste() {
    if (!arraste) return;
    const { sessao, novoInicio, foraDaGrade } = arraste;
    setArraste(null);
    remarcar.mutate(
      { sessao, novoInicio, permitirForaDaGrade: foraDaGrade },
      {
        onSuccess: (s) => {
          toast.success(`Sessão movida para ${formatarDataHora(s.inicio, fuso)}.`);
          setDestacarId(s.id);
          setTimeout(() => setDestacarId(null), 2000);
        },
        onError: (e) =>
          toast.error(
            e instanceof ErroApi && e.codigo === "horario-indisponivel"
              ? "Esse horário acabou de ser ocupado. A agenda foi atualizada."
              : mensagemDoErro(e),
          ),
      },
    );
  }

  const podeRemoverBloqueio = (b: Bloqueio) =>
    bloqueiaTudo || (b.profissionalId !== null && b.profissionalId === usuario.profissionalId);

  if (soFono && !usuario.profissionalId) {
    return (
      <Vazio titulo="Seu login ainda não está ligado a um profissional">
        Peça à administração para vincular seu usuário em Configurações › Profissionais.
      </Vazio>
    );
  }

  const tituloPeriodo =
    estado.visao === "dia"
      ? formatarDataLonga(estado.data)
      : `Semana de ${primeiroDia.split("-").reverse().slice(0, 2).join("/")} a ${somarDias(primeiroDia, 6).split("-").reverse().slice(0, 2).join("/")}`;

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <h1 className="mr-2 text-xl font-semibold first-letter:uppercase">{tituloPeriodo}</h1>
        <div className="flex items-center gap-1">
          <Button variante="secundaria" tamanho="pequeno" onClick={() => estado.atualizar({ data: null })}>
            Hoje
          </Button>
          <Button variante="fantasma" tamanho="icone" aria-label="Período anterior" onClick={() => navegar(-qtdDias)}>
            <ChevronLeft />
          </Button>
          <Button variante="fantasma" tamanho="icone" aria-label="Próximo período" onClick={() => navegar(qtdDias)}>
            <ChevronRight />
          </Button>
          <Input
            type="date"
            aria-label="Ir para a data"
            className="h-8 w-40"
            value={estado.data}
            onChange={(e) => e.target.value && estado.atualizar({ data: e.target.value })}
          />
        </div>
        <div className="rounded-padrao border-borda flex border p-0.5" role="group" aria-label="Visão">
          {(["dia", "semana"] as Visao[]).map((v) => (
            <button
              key={v}
              aria-pressed={estado.visao === v}
              onClick={() => estado.atualizar({ visao: v === "dia" ? null : v })}
              className={cn(
                "rounded px-3 py-1 text-sm",
                estado.visao === v ? "bg-primaria text-primaria-texto" : "text-texto hover:bg-superficie-2",
              )}
            >
              {v === "dia" ? "Dia" : "Semana"}
            </button>
          ))}
        </div>
        <div className="flex-1" />
        <Button
          variante="fantasma"
          tamanho="icone"
          aria-label="Atualizar agenda"
          onClick={() => {
            sessoesQ.refetch();
            bloqueiosQ.refetch();
          }}
        >
          <RefreshCw className={cn(sessoesQ.isFetching && "animate-spin")} />
        </Button>
        {podeBloquear && (
          <Button variante="secundaria" tamanho="pequeno" onClick={() => setBloqueio({ data: estado.data })}>
            <Lock /> Bloquear horário
          </Button>
        )}
        {podeEditar && (
          <Button tamanho="pequeno" onClick={() => setNovo({ data: estado.data })} title="Atalho: N">
            <Plus /> Novo agendamento
          </Button>
        )}
      </div>

      {!soFono && todos.length > 0 && (
        <FiltroProfissionais
          visao={estado.visao}
          todos={todos}
          selecionados={visiveis.map((p) => p.id)}
          aoMudar={(ids) => estado.atualizar({ fonos: ids.length === todos.length ? null : ids.join(",") })}
        />
      )}

      <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2">
        <Checkbox
          id="mostrar-canceladas"
          rotulo="Mostrar canceladas"
          checked={estado.canceladas}
          onChange={(e) => estado.atualizar({ canceladas: e.target.checked ? "1" : null })}
        />
        <Legenda />
      </div>

      {(profissionaisQ.isLoading || (sessoesQ.isLoading && !sessoesQ.data)) && (
        <Carregando texto="Carregando agenda…" />
      )}
      {(profissionaisQ.error || sessoesQ.error) && (
        <MensagemErro
          acao={
            <Button variante="secundaria" tamanho="pequeno" onClick={() => sessoesQ.refetch()}>
              Tentar de novo
            </Button>
          }
        >
          {mensagemDoErro(profissionaisQ.error ?? sessoesQ.error)}
        </MensagemErro>
      )}
      {profissionaisQ.data && todos.length === 0 && (
        <Vazio titulo="Nenhum profissional ativo">
          Cadastre os profissionais e a grade em Configurações › Profissionais.
        </Vazio>
      )}
      {colunas.length > 0 && sessoesQ.data && (
        <div className="md:hidden">
          <ListaAgenda colunas={colunas} sessoes={sessoes} fuso={fuso} aoAbrirSessao={(s) => setSessaoAbertaId(s.id)} />
        </div>
      )}
      {colunas.length > 0 && sessoesQ.data && (
        <div className="hidden md:block">
          <GradeAgenda
            colunas={colunas}
            faixa={faixa}
            sessoes={sessoes}
            bloqueios={bloqueiosQ.data ?? []}
            fuso={fuso}
            agora={agora}
            podeArrastar={podeEditar}
            destacarId={destacarId}
            aoClicarHorario={aoClicarHorario}
            aoAbrirSessao={(s) => setSessaoAbertaId(s.id)}
            aoAbrirBloqueio={setBloqueioAberto}
            aoSoltarSessao={aoSoltarSessao}
          />
        </div>
      )}

      {dialogoNovo && (
        <DialogoNovoAgendamento
          profissionais={todos}
          inicial={dialogoNovo}
          aoFechar={fecharNovo}
          aoAgendar={(s) => {
            const data = dataEMinutos(s.inicio, fuso).data;
            if (data !== estado.data && estado.visao === "dia") estado.atualizar({ data });
            setDestacarId(s.id);
            setTimeout(() => setDestacarId(null), 2000);
          }}
        />
      )}
      {bloqueio && <DialogoBloqueio profissionais={todos} inicial={bloqueio} aoFechar={() => setBloqueio(null)} />}
      {sessaoAberta && (
        <PainelSessao
          sessao={sessaoAberta}
          nomeProfissional={nomeProfissional(sessaoAberta.profissionalId)}
          nomeSala={nomeSala(sessaoAberta.recursoId)}
          aoFechar={() => setSessaoAbertaId(null)}
        />
      )}
      {arraste && (
        <Dialogo
          aberto
          aoMudar={(a) => !a && setArraste(null)}
          titulo="Mover sessão?"
          rodape={
            <>
              <Button variante="secundaria" onClick={() => setArraste(null)}>
                Não mover
              </Button>
              <Button onClick={confirmarArraste} autoFocus>
                Mover
              </Button>
            </>
          }
        >
          <p className="text-sm">
            Mover a sessão de <strong>{arraste.sessao.pacienteNome ?? "paciente"}</strong> de{" "}
            {formatarDataHora(arraste.sessao.inicio, fuso)} para{" "}
            <strong>{formatarDataHora(arraste.novoInicio, fuso)}</strong>?
          </p>
          <p className="text-texto-suave mt-2 text-xs">O responsável deve ser avisado da mudança.</p>
        </Dialogo>
      )}
      {bloqueioAberto && (
        <Dialogo
          aberto
          aoMudar={(a) => !a && setBloqueioAberto(null)}
          titulo={
            bloqueioAberto.profissionalId
              ? `Bloqueio de ${nomeProfissional(bloqueioAberto.profissionalId)}`
              : "Clínica fechada"
          }
          rodape={
            <>
              <Button variante="secundaria" onClick={() => setBloqueioAberto(null)}>
                Fechar
              </Button>
              {podeRemoverBloqueio(bloqueioAberto) && (
                <Button
                  variante="perigo"
                  carregando={removerBloqueio.isPending}
                  onClick={() =>
                    removerBloqueio.mutate(bloqueioAberto.id, {
                      onSuccess: () => {
                        toast.success("Bloqueio removido.");
                        setBloqueioAberto(null);
                      },
                    })
                  }
                >
                  Remover bloqueio
                </Button>
              )}
            </>
          }
        >
          <p className="text-sm">
            De {formatarDataHora(bloqueioAberto.inicio, fuso)} até {formatarDataHora(bloqueioAberto.fim, fuso)}
            {bloqueioAberto.motivo ? ` · ${bloqueioAberto.motivo}` : ""}.
          </p>
        </Dialogo>
      )}
    </div>
  );
}

function FiltroProfissionais({
  visao,
  todos,
  selecionados,
  aoMudar,
}: {
  visao: Visao;
  todos: Profissional[];
  selecionados: string[];
  aoMudar: (ids: string[]) => void;
}) {
  if (visao === "semana") {
    return (
      <div className="mb-3 flex items-center gap-2">
        <label htmlFor="fono-semana" className="text-sm font-medium">
          Profissional
        </label>
        <Select
          id="fono-semana"
          className="w-64"
          value={selecionados[0] ?? ""}
          onChange={(e) => aoMudar([e.target.value])}
        >
          {todos.map((p) => (
            <option key={p.id} value={p.id}>
              {p.nome}
            </option>
          ))}
        </Select>
      </div>
    );
  }
  return (
    <fieldset className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-1">
      <legend className="sr-only">Profissionais visíveis</legend>
      {todos.map((p) => (
        <Checkbox
          key={p.id}
          id={`fono-${p.id}`}
          rotulo={p.nome}
          checked={selecionados.includes(p.id)}
          onChange={(e) => {
            const proximos = e.target.checked ? [...selecionados, p.id] : selecionados.filter((id) => id !== p.id);
            aoMudar(proximos.length ? proximos : todos.map((x) => x.id));
          }}
        />
      ))}
    </fieldset>
  );
}

function Legenda() {
  const itens: StatusSessao[] = ["RESERVADA", "AGENDADA", "CONFIRMADA", "ATENDIDA", "FALTA_AVISADA", "FALTA_SEM_AVISO"];
  return (
    <ul className="text-texto-suave flex flex-wrap gap-3 text-xs" aria-label="Legenda">
      {itens.map((s) => (
        <li key={s} className="flex items-center gap-1">
          <IconeStatus status={s} /> {NOME_STATUS[s]}
        </li>
      ))}
      <li className="flex items-center gap-1">
        <span className="hachurado border-borda inline-block size-3 rounded-sm border" aria-hidden /> Fora da grade ou
        bloqueado
      </li>
    </ul>
  );
}
