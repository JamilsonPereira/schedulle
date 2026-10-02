"use client";

import {
  Building2,
  CalendarDays,
  DoorOpen,
  LogOut,
  Menu,
  Stethoscope,
  UserCog,
  UserRound,
  Users,
  X,
} from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import * as React from "react";
import { Button } from "@/components/ui/button";
import { sair } from "@/features/auth/acoes";
import { usePode, useSessao } from "@/features/auth/contexto";
import { NOME_PAPEL, pode, type Permissao } from "@/lib/auth/permissoes";
import { cn } from "@/lib/utils";

interface ItemMenu {
  href: string;
  rotulo: string;
  icone: React.ComponentType<{ className?: string }>;
  permissao?: Permissao;
}

const MENU: { titulo?: string; itens: ItemMenu[] }[] = [
  {
    itens: [
      { href: "/agenda", rotulo: "Agenda", icone: CalendarDays },
      { href: "/pacientes", rotulo: "Pacientes", icone: Users, permissao: "pacientes.ver" },
    ],
  },
  {
    titulo: "Configurações",
    itens: [
      { href: "/configuracoes/clinica", rotulo: "Clínica", icone: Building2, permissao: "configuracoes.ver" },
      { href: "/configuracoes/salas", rotulo: "Salas", icone: DoorOpen, permissao: "configuracoes.ver" },
      {
        href: "/configuracoes/profissionais",
        rotulo: "Profissionais",
        icone: Stethoscope,
        permissao: "configuracoes.ver",
      },
      { href: "/configuracoes/usuarios", rotulo: "Usuários", icone: UserCog, permissao: "usuarios.gerenciar" },
    ],
  },
];

export function Moldura({ children, minutosInatividade }: { children: React.ReactNode; minutosInatividade: number }) {
  const { usuario, clinica } = useSessao();
  const pathname = usePathname();
  const [menuAberto, setMenuAberto] = React.useState(false);
  const podeAgendar = usePode("agenda.editar");
  useLogoutPorInatividade(minutosInatividade);

  return (
    <div className="flex min-h-screen">
      <a
        href="#conteudo"
        className="focus:bg-superficie sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:px-3 focus:py-2"
      >
        Pular para o conteúdo
      </a>
      <aside
        className={cn(
          "border-borda bg-superficie fixed inset-y-0 left-0 z-30 flex w-60 flex-col border-r transition-transform lg:static lg:translate-x-0",
          menuAberto ? "translate-x-0" : "-translate-x-full",
        )}
        aria-label="Menu principal"
      >
        <div className="border-borda flex h-14 items-center justify-between border-b px-4">
          <div className="min-w-0">
            <p className="text-primaria text-xs font-medium">Agenda Fono</p>
            <p className="truncate text-sm font-semibold" title={clinica.nome}>
              {clinica.nome}
            </p>
          </div>
          <button className="rounded p-1 lg:hidden" onClick={() => setMenuAberto(false)} aria-label="Fechar menu">
            <X className="size-4" />
          </button>
        </div>
        <nav className="flex-1 overflow-y-auto p-3">
          {MENU.map((grupo, i) => {
            const itens = grupo.itens.filter((it) => !it.permissao || pode(usuario.papeis, it.permissao));
            if (itens.length === 0) return null;
            return (
              <div key={i} className="mb-4">
                {grupo.titulo && (
                  <p className="text-texto-suave mb-1 px-2 text-xs font-semibold tracking-wide uppercase">
                    {grupo.titulo}
                  </p>
                )}
                <ul className="flex flex-col gap-0.5">
                  {itens.map((it) => {
                    const ativo = pathname === it.href || pathname.startsWith(`${it.href}/`);
                    const Icone = it.icone;
                    return (
                      <li key={it.href}>
                        <Link
                          href={it.href}
                          aria-current={ativo ? "page" : undefined}
                          onClick={() => setMenuAberto(false)}
                          className={cn(
                            "rounded-padrao flex items-center gap-2 px-2 py-2 text-sm",
                            ativo ? "bg-primaria-suave text-primaria font-medium" : "text-texto hover:bg-superficie-2",
                          )}
                        >
                          <Icone className="size-4" />
                          {it.rotulo}
                        </Link>
                      </li>
                    );
                  })}
                </ul>
              </div>
            );
          })}
        </nav>
        <MenuUsuario />
      </aside>
      {menuAberto && (
        <div className="fixed inset-0 z-20 bg-black/30 lg:hidden" onClick={() => setMenuAberto(false)} aria-hidden />
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-borda bg-superficie sticky top-0 z-10 flex h-14 items-center gap-3 border-b px-4">
          <button className="rounded p-1 lg:hidden" onClick={() => setMenuAberto(true)} aria-label="Abrir menu">
            <Menu className="size-5" />
          </button>
          <div className="flex-1" />
          {podeAgendar && !pathname.startsWith("/agenda") && (
            <Button asChild tamanho="pequeno">
              <Link href="/agenda?novo=1">Novo agendamento</Link>
            </Button>
          )}
        </header>
        <main id="conteudo" className="flex-1 p-4 lg:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}

function MenuUsuario() {
  const { usuario } = useSessao();
  const router = useRouter();
  const [saindo, setSaindo] = React.useState(false);

  async function encerrar() {
    setSaindo(true);
    try {
      await sair();
    } finally {
      router.replace("/login?motivo=saiu");
      router.refresh();
    }
  }

  return (
    <div className="border-borda border-t p-3">
      <div className="mb-2 flex items-center gap-2 px-2">
        <UserRound className="text-texto-suave size-4" aria-hidden />
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">{usuario.nome}</p>
          <p className="text-texto-suave truncate text-xs">{usuario.papeis.map((p) => NOME_PAPEL[p]).join(", ")}</p>
        </div>
      </div>
      <div className="flex flex-col gap-0.5">
        <Link href="/conta" className="rounded-padrao hover:bg-superficie-2 px-2 py-1.5 text-sm">
          Minha conta
        </Link>
        <button
          onClick={encerrar}
          disabled={saindo}
          className="rounded-padrao hover:bg-superficie-2 flex items-center gap-2 px-2 py-1.5 text-left text-sm"
        >
          <LogOut className="size-4" /> Sair
        </button>
      </div>
    </div>
  );
}

/** Sai sozinho após N minutos sem mouse, teclado ou toque (computadores de recepção são compartilhados). */
function useLogoutPorInatividade(minutos: number) {
  const router = useRouter();
  React.useEffect(() => {
    let ultima = Date.now();
    const marcar = () => {
      ultima = Date.now();
    };
    const eventos = ["mousemove", "mousedown", "keydown", "touchstart", "scroll"] as const;
    eventos.forEach((e) => window.addEventListener(e, marcar, { passive: true }));
    const timer = window.setInterval(async () => {
      if (Date.now() - ultima < minutos * 60_000) return;
      window.clearInterval(timer);
      await sair().catch(() => undefined);
      router.replace("/login?motivo=inatividade");
      router.refresh();
    }, 30_000);
    return () => {
      eventos.forEach((e) => window.removeEventListener(e, marcar));
      window.clearInterval(timer);
    };
  }, [minutos, router]);
}
