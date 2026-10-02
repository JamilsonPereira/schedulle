"use client";

import { Copy, Plus, Trash2 } from "lucide-react";
import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input, Select } from "@/components/ui/campos";
import { PainelLateral } from "@/components/ui/dialog";
import { Badge, Cabecalho, Carregando, MensagemErro, Tabela, Td, Th, Vazio } from "@/components/ui/diversos";
import {
  useAlternarProfissional,
  useProfissionais,
  useRecursos,
  useSalvarProfissional,
  useUsuarios,
} from "@/features/configuracoes/consultas";
import { horaCurta, resumirGrade, validarGrade, type LinhaGrade } from "@/features/configuracoes/grade";
import { SomenteAdmin } from "@/features/configuracoes/somente-admin";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import { DIAS_SEMANA, type DiaSemana, type Profissional, type Subarea } from "@/lib/api/tipos";
import { NOME_DIA } from "@/lib/datas";
import { NOME_SUBAREA, SUBAREAS } from "@/lib/rotulos";

export function PaginaProfissionais() {
  return (
    <SomenteAdmin>
      <Conteudo />
    </SomenteAdmin>
  );
}

function Conteudo() {
  const [mostrarInativos, setMostrarInativos] = React.useState(false);
  const { data, isLoading, error } = useProfissionais(!mostrarInativos);
  const usuarios = useUsuarios();
  const alternar = useAlternarProfissional();
  const [editando, setEditando] = React.useState<Profissional | "novo" | null>(null);
  const nomeUsuario = (id: string | null) => usuarios.data?.find((u) => u.id === id)?.nome;

  return (
    <>
      <Cabecalho
        titulo="Profissionais"
        descricao="Fonoaudiólogos, subáreas atendidas e grade semanal de atendimento."
        acoes={
          <Button onClick={() => setEditando("novo")}>
            <Plus /> Novo profissional
          </Button>
        }
      />
      <Checkbox
        id="mostrar-inativos"
        rotulo="Mostrar inativos"
        className="mb-3"
        checked={mostrarInativos}
        onChange={(e) => setMostrarInativos(e.target.checked)}
      />
      {isLoading && <Carregando />}
      {error && <MensagemErro>{mensagemDoErro(error)}</MensagemErro>}
      {data && data.length === 0 && (
        <Vazio titulo="Nenhum profissional cadastrado">
          Cadastre os fonos e a grade de cada um para abrir a agenda.
        </Vazio>
      )}
      {data && data.length > 0 && (
        <Tabela>
          <thead>
            <tr>
              <Th>Nome</Th>
              <Th>Subáreas</Th>
              <Th>Sessão</Th>
              <Th>Grade</Th>
              <Th>Login</Th>
              <Th>Situação</Th>
              <Th>
                <span className="sr-only">Ações</span>
              </Th>
            </tr>
          </thead>
          <tbody>
            {data.map((p) => (
              <tr key={p.id}>
                <Td className="font-medium">
                  {p.nome}
                  {p.registroCrfa && <div className="text-texto-suave text-xs font-normal">{p.registroCrfa}</div>}
                </Td>
                <Td className="text-xs">{p.subareas.map((s) => NOME_SUBAREA[s]).join(", ") || "—"}</Td>
                <Td>{p.duracaoPadraoMin} min</Td>
                <Td className="max-w-72 text-xs">{resumirGrade(p.grade)}</Td>
                <Td className="text-xs">{p.usuarioId ? (nomeUsuario(p.usuarioId) ?? "Vinculado") : "—"}</Td>
                <Td>{p.ativo ? <Badge tom="sucesso">Ativo</Badge> : <Badge>Inativo</Badge>}</Td>
                <Td className="whitespace-nowrap">
                  <Button variante="fantasma" tamanho="pequeno" onClick={() => setEditando(p)}>
                    Editar
                  </Button>
                  <Button
                    variante="fantasma"
                    tamanho="pequeno"
                    onClick={() =>
                      alternar.mutate(p, {
                        onSuccess: (r) =>
                          toast.success(r.ativo ? "Profissional reativado." : "Profissional inativado."),
                      })
                    }
                  >
                    {p.ativo ? "Inativar" : "Reativar"}
                  </Button>
                </Td>
              </tr>
            ))}
          </tbody>
        </Tabela>
      )}
      {editando && (
        <FormularioProfissional
          profissional={editando === "novo" ? null : editando}
          vinculados={new Set((data ?? []).map((p) => p.usuarioId).filter((id): id is string => Boolean(id)))}
          aoFechar={() => setEditando(null)}
        />
      )}
    </>
  );
}

let contador = 0;
const novaChave = () => `l${++contador}`;

function FormularioProfissional({
  profissional,
  vinculados,
  aoFechar,
}: {
  profissional: Profissional | null;
  vinculados: Set<string>;
  aoFechar: () => void;
}) {
  const salvar = useSalvarProfissional();
  const recursos = useRecursos();
  const usuarios = useUsuarios();
  const [nome, setNome] = React.useState(profissional?.nome ?? "");
  const [registro, setRegistro] = React.useState(profissional?.registroCrfa ?? "");
  const [subareas, setSubareas] = React.useState<Subarea[]>(profissional?.subareas ?? []);
  const [duracao, setDuracao] = React.useState(String(profissional?.duracaoPadraoMin ?? 40));
  const [usuarioId, setUsuarioId] = React.useState(profissional?.usuarioId ?? "");
  const [linhas, setLinhas] = React.useState<LinhaGrade[]>(() =>
    (profissional?.grade ?? []).map((g) => ({
      ...g,
      inicio: horaCurta(g.inicio),
      fim: horaCurta(g.fim),
      chave: novaChave(),
    })),
  );
  const [erros, setErros] = React.useState<Record<string, string>>({});
  const [erroGeral, setErroGeral] = React.useState<string | null>(null);

  const fonos = (usuarios.data ?? []).filter(
    (u) => u.papeis.includes("FONO") && u.ativo && (!vinculados.has(u.id) || u.id === profissional?.usuarioId),
  );
  const salas = (recursos.data ?? []).filter((r) => r.ativo);

  function validar(): boolean {
    const e: Record<string, string> = {};
    if (nome.trim().length < 2) e.nome = "Informe o nome (mínimo 2 caracteres)";
    if (subareas.length === 0) e.subareas = "Escolha ao menos uma subárea";
    const d = Number(duracao);
    if (!Number.isInteger(d) || d < 10 || d > 240) e.duracao = "Entre 10 e 240 minutos";
    if (registro.trim() && registro.trim().length < 3) e.registro = "Registro muito curto";
    Object.assign(e, validarGrade(linhas));
    setErros(e);
    return Object.keys(e).length === 0;
  }

  function enviar(ev: React.FormEvent) {
    ev.preventDefault();
    setErroGeral(null);
    if (!validar()) return;
    salvar.mutate(
      {
        id: profissional?.id,
        versao: profissional?.versao,
        dados: {
          nome: nome.trim(),
          registroCrfa: registro.trim() || null,
          subareas,
          duracaoPadraoMin: Number(duracao),
          usuarioId: usuarioId || null,
        },
        grade: linhas.map(({ dia, inicio, fim, recursoId }) => ({ dia, inicio, fim, recursoId: recursoId || null })),
      },
      {
        onSuccess: () => {
          toast.success(profissional ? "Profissional atualizado." : "Profissional cadastrado.");
          aoFechar();
        },
        onError: (e) =>
          setErroGeral(
            e instanceof ErroApi && e.codigo === "grade-sobreposta"
              ? "A grade tem intervalos sobrepostos no mesmo dia."
              : mensagemDoErro(e),
          ),
      },
    );
  }

  const adicionar = (dia: DiaSemana) =>
    setLinhas((ls) => {
      const doDia = ls.filter((l) => l.dia === dia);
      const ultimo = doDia.at(-1);
      const inicio = ultimo ? ultimo.fim : "08:00";
      const [h] = inicio.split(":").map(Number);
      const fim = `${String(Math.min(h + 4, 23)).padStart(2, "0")}:00`;
      return [...ls, { chave: novaChave(), dia, inicio, fim, recursoId: ultimo?.recursoId ?? null }];
    });

  const alterar = (chave: string, campo: "inicio" | "fim" | "recursoId", valor: string) =>
    setLinhas((ls) =>
      ls.map((l) => (l.chave === chave ? { ...l, [campo]: valor || (campo === "recursoId" ? null : "") } : l)),
    );

  const copiarSegunda = () =>
    setLinhas((ls) => {
      const segunda = ls.filter((l) => l.dia === "MONDAY");
      const dias: DiaSemana[] = ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
      return [
        ...ls.filter((l) => !dias.includes(l.dia)),
        ...dias.flatMap((dia) => segunda.map((l) => ({ ...l, dia, chave: novaChave() }))),
      ];
    });

  return (
    <PainelLateral
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo={profissional ? `Editar ${profissional.nome}` : "Novo profissional"}
      largura="max-w-xl"
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button type="submit" form="form-profissional" carregando={salvar.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <form id="form-profissional" onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        {erroGeral && <MensagemErro>{erroGeral}</MensagemErro>}
        <Campo id="prof-nome" rotulo="Nome" erro={erros.nome}>
          <Input value={nome} onChange={(e) => setNome(e.target.value)} autoFocus />
        </Campo>
        <div className="grid grid-cols-2 gap-3">
          <Campo id="prof-registro" rotulo="Registro (CRFa)" erro={erros.registro}>
            <Input value={registro} onChange={(e) => setRegistro(e.target.value)} placeholder="2-12345" />
          </Campo>
          <Campo id="prof-duracao" rotulo="Duração padrão (min)" erro={erros.duracao}>
            <Input inputMode="numeric" value={duracao} onChange={(e) => setDuracao(e.target.value)} />
          </Campo>
        </div>
        <fieldset aria-describedby={erros.subareas ? "prof-subareas-erro" : undefined}>
          <legend className="mb-1.5 text-sm font-medium">Subáreas atendidas</legend>
          <div className="grid grid-cols-2 gap-1.5">
            {SUBAREAS.map((s) => (
              <Checkbox
                key={s}
                id={`sub-${s}`}
                rotulo={NOME_SUBAREA[s]}
                checked={subareas.includes(s)}
                onChange={(e) =>
                  setSubareas((atual) => (e.target.checked ? [...atual, s] : atual.filter((x) => x !== s)))
                }
              />
            ))}
          </div>
          {erros.subareas && (
            <p id="prof-subareas-erro" className="text-perigo mt-1 text-xs" role="alert">
              {erros.subareas}
            </p>
          )}
        </fieldset>
        <Campo
          id="prof-usuario"
          rotulo="Login do profissional"
          dica="Usuário com perfil Fonoaudiólogo(a). Com o vínculo, ele vê a própria agenda."
        >
          <Select value={usuarioId} onChange={(e) => setUsuarioId(e.target.value)}>
            <option value="">Sem login vinculado</option>
            {fonos.map((u) => (
              <option key={u.id} value={u.id}>
                {u.nome} ({u.email})
              </option>
            ))}
          </Select>
        </Campo>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <h3 className="text-sm font-semibold">Grade semanal</h3>
            <Button type="button" variante="link" tamanho="pequeno" onClick={copiarSegunda}>
              <Copy /> Copiar segunda para ter–sex
            </Button>
          </div>
          <div className="flex flex-col gap-3">
            {DIAS_SEMANA.map((dia) => {
              const doDia = linhas.filter((l) => l.dia === dia);
              return (
                <div key={dia} className="rounded-padrao border-borda border p-2">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">{NOME_DIA[dia]}</span>
                    <Button type="button" variante="fantasma" tamanho="pequeno" onClick={() => adicionar(dia)}>
                      <Plus /> Intervalo
                    </Button>
                  </div>
                  {doDia.length === 0 && <p className="text-texto-suave text-xs">Não atende</p>}
                  {doDia.map((l) => (
                    <div key={l.chave} className="mt-1.5">
                      <div className="flex items-center gap-1.5">
                        <Input
                          type="time"
                          step={300}
                          aria-label={`${NOME_DIA[dia]}: início`}
                          className="w-32"
                          value={l.inicio}
                          aria-invalid={erros[l.chave] ? true : undefined}
                          onChange={(e) => alterar(l.chave, "inicio", e.target.value)}
                        />
                        <span aria-hidden>–</span>
                        <Input
                          type="time"
                          step={300}
                          aria-label={`${NOME_DIA[dia]}: fim`}
                          className="w-32"
                          value={l.fim}
                          aria-invalid={erros[l.chave] ? true : undefined}
                          onChange={(e) => alterar(l.chave, "fim", e.target.value)}
                        />
                        <Select
                          aria-label={`${NOME_DIA[dia]}: sala`}
                          value={l.recursoId ?? ""}
                          onChange={(e) => alterar(l.chave, "recursoId", e.target.value)}
                        >
                          <option value="">Sem sala</option>
                          {salas.map((r) => (
                            <option key={r.id} value={r.id}>
                              {r.nome}
                            </option>
                          ))}
                        </Select>
                        <Button
                          type="button"
                          variante="fantasma"
                          tamanho="icone"
                          aria-label="Remover intervalo"
                          onClick={() => setLinhas((ls) => ls.filter((x) => x.chave !== l.chave))}
                        >
                          <Trash2 />
                        </Button>
                      </div>
                      {erros[l.chave] && (
                        <p className="text-perigo mt-0.5 text-xs" role="alert">
                          {erros[l.chave]}
                        </p>
                      )}
                    </div>
                  ))}
                </div>
              );
            })}
          </div>
        </div>
      </form>
    </PainelLateral>
  );
}
