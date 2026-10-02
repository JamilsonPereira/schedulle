"use client";

import { Check, Copy, KeyRound, Plus } from "lucide-react";
import * as React from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Campo, Checkbox, Input } from "@/components/ui/campos";
import { Dialogo } from "@/components/ui/dialog";
import { Badge, Cabecalho, Carregando, MensagemErro, Tabela, Td, Th } from "@/components/ui/diversos";
import { useSessao } from "@/features/auth/contexto";
import {
  useAtualizarUsuario,
  useCriarUsuario,
  useRedefinirSenha,
  useUsuarios,
} from "@/features/configuracoes/consultas";
import { SomenteAdmin } from "@/features/configuracoes/somente-admin";
import { NOME_PAPEL } from "@/lib/auth/permissoes";
import { ErroApi, mensagemDoErro } from "@/lib/api/erros";
import { PAPEIS, type Papel, type Usuario, type UsuarioComSenhaTemporaria } from "@/lib/api/tipos";
import { formatarDataHora } from "@/lib/datas";

export function PaginaUsuarios() {
  return (
    <SomenteAdmin>
      <Conteudo />
    </SomenteAdmin>
  );
}

function Conteudo() {
  const { clinica } = useSessao();
  const { data, isLoading, error } = useUsuarios();
  const redefinir = useRedefinirSenha();
  const [editando, setEditando] = React.useState<Usuario | "novo" | null>(null);
  const [senha, setSenha] = React.useState<UsuarioComSenhaTemporaria | null>(null);
  const [confirmarReset, setConfirmarReset] = React.useState<Usuario | null>(null);

  return (
    <>
      <Cabecalho
        titulo="Usuários"
        descricao="Quem acessa o painel e com quais perfis. Usuários desativados mantêm o histórico."
        acoes={
          <Button onClick={() => setEditando("novo")}>
            <Plus /> Novo usuário
          </Button>
        }
      />
      {isLoading && <Carregando />}
      {error && <MensagemErro>{mensagemDoErro(error)}</MensagemErro>}
      {data && (
        <Tabela>
          <thead>
            <tr>
              <Th>Nome</Th>
              <Th>E-mail</Th>
              <Th>Perfis</Th>
              <Th>Último acesso</Th>
              <Th>Situação</Th>
              <Th>
                <span className="sr-only">Ações</span>
              </Th>
            </tr>
          </thead>
          <tbody>
            {data.map((u) => (
              <tr key={u.id}>
                <Td className="font-medium">{u.nome}</Td>
                <Td>{u.email}</Td>
                <Td className="text-xs">{u.papeis.map((p) => NOME_PAPEL[p]).join(", ")}</Td>
                <Td className="text-xs">
                  {u.ultimoLoginEm ? formatarDataHora(u.ultimoLoginEm, clinica.fuso) : "Nunca"}
                </Td>
                <Td>
                  <div className="flex flex-wrap gap-1">
                    {u.ativo ? <Badge tom="sucesso">Ativo</Badge> : <Badge>Desativado</Badge>}
                    {u.bloqueado && <Badge tom="perigo">Bloqueado por tentativas</Badge>}
                    {u.precisaTrocarSenha && u.ativo && <Badge tom="aviso">Senha temporária</Badge>}
                  </div>
                </Td>
                <Td className="whitespace-nowrap">
                  <Button variante="fantasma" tamanho="pequeno" onClick={() => setEditando(u)}>
                    Editar
                  </Button>
                  <Button variante="fantasma" tamanho="pequeno" onClick={() => setConfirmarReset(u)}>
                    <KeyRound /> Nova senha
                  </Button>
                </Td>
              </tr>
            ))}
          </tbody>
        </Tabela>
      )}

      {editando && (
        <DialogoUsuario
          usuario={editando === "novo" ? null : editando}
          aoFechar={() => setEditando(null)}
          aoCriar={(r) => {
            setEditando(null);
            setSenha(r);
          }}
        />
      )}

      {confirmarReset && (
        <Dialogo
          aberto
          aoMudar={(a) => !a && setConfirmarReset(null)}
          titulo={`Gerar senha temporária para ${confirmarReset.nome}?`}
          descricao="As sessões abertas dessa pessoa serão encerradas e ela terá de definir uma senha nova no próximo acesso."
          rodape={
            <>
              <Button variante="secundaria" onClick={() => setConfirmarReset(null)}>
                Voltar
              </Button>
              <Button
                carregando={redefinir.isPending}
                onClick={() =>
                  redefinir.mutate(confirmarReset.id, {
                    onSuccess: (r) => {
                      setConfirmarReset(null);
                      setSenha(r);
                    },
                  })
                }
              >
                Gerar senha
              </Button>
            </>
          }
        >
          <p className="text-sm">Use quando a pessoa esqueceu a senha ou ficou bloqueada.</p>
        </Dialogo>
      )}

      {senha && <DialogoSenhaTemporaria resultado={senha} aoFechar={() => setSenha(null)} />}
    </>
  );
}

function DialogoUsuario({
  usuario,
  aoFechar,
  aoCriar,
}: {
  usuario: Usuario | null;
  aoFechar: () => void;
  aoCriar: (r: UsuarioComSenhaTemporaria) => void;
}) {
  const { usuario: eu } = useSessao();
  const criar = useCriarUsuario();
  const atualizar = useAtualizarUsuario();
  const [nome, setNome] = React.useState(usuario?.nome ?? "");
  const [email, setEmail] = React.useState(usuario?.email ?? "");
  const [papeis, setPapeis] = React.useState<Papel[]>(usuario?.papeis ?? []);
  const [ativo, setAtivo] = React.useState(usuario?.ativo ?? true);
  const [erros, setErros] = React.useState<Record<string, string>>({});
  const [erroGeral, setErroGeral] = React.useState<string | null>(null);
  const souEu = usuario?.id === eu.id;

  function enviar(ev?: React.FormEvent) {
    ev?.preventDefault();
    const e: Record<string, string> = {};
    if (nome.trim().length < 2) e.nome = "Informe o nome";
    if (!usuario && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) e.email = "E-mail inválido";
    if (papeis.length === 0) e.papeis = "Escolha ao menos um perfil";
    setErros(e);
    setErroGeral(null);
    if (Object.keys(e).length) return;

    const aoErrar = (err: Error) => {
      if (err instanceof ErroApi && err.codigo === "email-ja-cadastrado") setErros({ email: err.message });
      else setErroGeral(mensagemDoErro(err));
    };
    if (usuario) {
      atualizar.mutate(
        { id: usuario.id, nome: nome.trim(), papeis, ativo, versao: usuario.versao },
        {
          onSuccess: () => {
            toast.success("Usuário atualizado.");
            aoFechar();
          },
          onError: aoErrar,
        },
      );
    } else {
      criar.mutate({ nome: nome.trim(), email: email.trim(), papeis }, { onSuccess: aoCriar, onError: aoErrar });
    }
  }

  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo={usuario ? "Editar usuário" : "Novo usuário"}
      descricao={usuario ? undefined : "Uma senha temporária será gerada para o primeiro acesso."}
      rodape={
        <>
          <Button variante="secundaria" onClick={aoFechar}>
            Cancelar
          </Button>
          <Button onClick={() => enviar()} carregando={criar.isPending || atualizar.isPending}>
            Salvar
          </Button>
        </>
      }
    >
      <form onSubmit={enviar} className="flex flex-col gap-4" noValidate>
        {erroGeral && <MensagemErro>{erroGeral}</MensagemErro>}
        <Campo id="usr-nome" rotulo="Nome" erro={erros.nome}>
          <Input value={nome} onChange={(e) => setNome(e.target.value)} autoFocus />
        </Campo>
        <Campo
          id="usr-email"
          rotulo="E-mail"
          erro={erros.email}
          dica={usuario ? "O e-mail não pode ser alterado." : undefined}
        >
          <Input type="email" value={email} disabled={Boolean(usuario)} onChange={(e) => setEmail(e.target.value)} />
        </Campo>
        <fieldset>
          <legend className="mb-1.5 text-sm font-medium">Perfis</legend>
          <div className="flex flex-col gap-1.5">
            {PAPEIS.map((p) => (
              <Checkbox
                key={p}
                id={`papel-${p}`}
                rotulo={NOME_PAPEL[p]}
                checked={papeis.includes(p)}
                disabled={souEu && p === "ADMIN"}
                onChange={(e) =>
                  setPapeis((atual) => (e.target.checked ? [...atual, p] : atual.filter((x) => x !== p)))
                }
              />
            ))}
          </div>
          {erros.papeis && (
            <p className="text-perigo mt-1 text-xs" role="alert">
              {erros.papeis}
            </p>
          )}
          <p className="text-texto-suave mt-2 text-xs">
            Para o fonoaudiólogo ver a própria agenda, vincule este login ao profissional em Configurações ›
            Profissionais.
          </p>
        </fieldset>
        {usuario && (
          <Checkbox
            id="usr-ativo"
            rotulo="Ativo (pode entrar no painel)"
            checked={ativo}
            disabled={souEu}
            onChange={(e) => setAtivo(e.target.checked)}
          />
        )}
        <button type="submit" hidden />
      </form>
    </Dialogo>
  );
}

function DialogoSenhaTemporaria({
  resultado,
  aoFechar,
}: {
  resultado: UsuarioComSenhaTemporaria;
  aoFechar: () => void;
}) {
  const [copiado, setCopiado] = React.useState(false);
  async function copiar() {
    await navigator.clipboard.writeText(resultado.senhaTemporaria);
    setCopiado(true);
  }
  return (
    <Dialogo
      aberto
      aoMudar={(a) => !a && aoFechar()}
      titulo="Senha temporária"
      descricao={`Entregue a senha para ${resultado.usuario.nome} (${resultado.usuario.email}). Ela não será mostrada de novo.`}
      rodape={<Button onClick={aoFechar}>Entendi</Button>}
    >
      <div className="flex items-center gap-2">
        <code
          className="rounded-padrao bg-superficie-2 flex-1 px-3 py-2 font-mono text-lg tracking-wider"
          aria-label="Senha temporária"
        >
          {resultado.senhaTemporaria}
        </code>
        <Button variante="secundaria" onClick={copiar} aria-label="Copiar senha">
          {copiado ? <Check /> : <Copy />} {copiado ? "Copiada" : "Copiar"}
        </Button>
      </div>
      <p className="text-texto-suave mt-3 text-sm">
        No primeiro acesso, o painel pede para a pessoa definir uma senha própria.
      </p>
    </Dialogo>
  );
}
