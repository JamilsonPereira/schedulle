"use client";

import { ChevronLeft, ChevronRight, Plus, Search } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/ui/button";
import { Input, Select } from "@/components/ui/campos";
import { Badge, Cabecalho, Carregando, MensagemErro, Tabela, Td, Th, Vazio } from "@/components/ui/diversos";
import { usePode } from "@/features/auth/contexto";
import { usePacientes } from "@/features/pacientes/consultas";
import { DialogoNovoPaciente } from "@/features/pacientes/dialogo-novo-paciente";
import { mensagemDoErro } from "@/lib/api/erros";
import { NOME_DEMANDA } from "@/lib/rotulos";
import { useDebounce } from "@/lib/use-debounce";

const TAMANHO = 25;

/** Lista com busca por nome ou telefone; filtros e página ficam na URL. Telefone sempre mascarado. */
export function ListaPacientes() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const podeEditar = usePode("pacientes.editar");
  const [texto, setTexto] = React.useState(params.get("busca") ?? "");
  const busca = useDebounce(texto.trim(), 300);
  const pagina = Math.max(0, Number(params.get("pagina") ?? 0) || 0);
  const situacao = params.get("situacao") ?? "ativos";
  const ativo = situacao === "ativos" ? true : situacao === "inativos" ? false : null;
  const [novo, setNovo] = React.useState(false);

  const atualizar = React.useCallback(
    (mudancas: Record<string, string | null>) => {
      const p = new URLSearchParams(params.toString());
      for (const [k, v] of Object.entries(mudancas)) {
        if (v === null || v === "") p.delete(k);
        else p.set(k, v);
      }
      router.replace(`${pathname}?${p.toString()}`, { scroll: false });
    },
    [params, pathname, router],
  );

  const buscaNaUrl = params.get("busca") ?? "";
  React.useEffect(() => {
    if (busca !== buscaNaUrl) atualizar({ busca, pagina: null });
  }, [busca, buscaNaUrl, atualizar]);

  const { data, isLoading, isFetching, error } = usePacientes({ busca, ativo, pagina, tamanho: TAMANHO });
  const totalPaginas = data ? Math.max(1, Math.ceil(data.totalElementos / TAMANHO)) : 1;

  return (
    <div>
      <Cabecalho
        titulo="Pacientes"
        descricao={data ? `${data.totalElementos} paciente(s)` : undefined}
        acoes={
          podeEditar && (
            <Button onClick={() => setNovo(true)}>
              <Plus /> Novo paciente
            </Button>
          )
        }
      />
      <div className="mb-4 flex flex-wrap gap-2">
        <div className="relative min-w-64 flex-1">
          <Search
            className="text-texto-suave pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2"
            aria-hidden
          />
          <Input
            className="pl-9"
            placeholder="Buscar por nome do paciente, do responsável ou telefone"
            aria-label="Buscar pacientes"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
          />
        </div>
        <Select
          aria-label="Situação"
          className="w-40"
          value={situacao}
          onChange={(e) => atualizar({ situacao: e.target.value === "ativos" ? null : e.target.value, pagina: null })}
        >
          <option value="ativos">Ativos</option>
          <option value="inativos">Inativos</option>
          <option value="todos">Todos</option>
        </Select>
      </div>

      {isLoading && <Carregando />}
      {error && <MensagemErro>{mensagemDoErro(error)}</MensagemErro>}
      {data && data.conteudo.length === 0 && (
        <Vazio titulo={busca ? "Nenhum paciente encontrado" : "Nenhum paciente cadastrado"}>
          {busca
            ? "Confira a grafia ou busque pelo telefone."
            : "Pacientes entram pelo WhatsApp ou pelo botão Novo paciente."}
        </Vazio>
      )}
      {data && data.conteudo.length > 0 && (
        <>
          <Tabela aria-busy={isFetching}>
            <thead>
              <tr>
                <Th>Paciente</Th>
                <Th>Idade</Th>
                <Th>Demanda</Th>
                <Th>Responsável</Th>
                <Th>Telefone</Th>
                <Th>Situação</Th>
              </tr>
            </thead>
            <tbody>
              {data.conteudo.map((p) => (
                <tr key={p.id} className="hover:bg-superficie-2">
                  <Td>
                    <Link href={`/pacientes/${p.id}`} className="text-primaria font-medium hover:underline">
                      {p.nome}
                    </Link>
                  </Td>
                  <Td>{p.idade !== null ? `${p.idade} anos` : "—"}</Td>
                  <Td>{p.demanda ? NOME_DEMANDA[p.demanda] : "—"}</Td>
                  <Td>{p.responsavelNome ?? "—"}</Td>
                  <Td className="font-mono text-xs">{p.telefoneMascarado}</Td>
                  <Td>{p.ativo ? <Badge tom="sucesso">Ativo</Badge> : <Badge>Inativo</Badge>}</Td>
                </tr>
              ))}
            </tbody>
          </Tabela>
          <nav className="mt-3 flex items-center justify-end gap-2 text-sm" aria-label="Paginação">
            <span className="text-texto-suave">
              Página {pagina + 1} de {totalPaginas}
            </span>
            <Button
              variante="secundaria"
              tamanho="icone"
              aria-label="Página anterior"
              disabled={pagina === 0}
              onClick={() => atualizar({ pagina: pagina - 1 > 0 ? String(pagina - 1) : null })}
            >
              <ChevronLeft />
            </Button>
            <Button
              variante="secundaria"
              tamanho="icone"
              aria-label="Próxima página"
              disabled={pagina + 1 >= totalPaginas}
              onClick={() => atualizar({ pagina: String(pagina + 1) })}
            >
              <ChevronRight />
            </Button>
          </nav>
        </>
      )}
      {novo && (
        <DialogoNovoPaciente aoFechar={() => setNovo(false)} aoCriar={(p) => router.push(`/pacientes/${p.id}`)} />
      )}
    </div>
  );
}
