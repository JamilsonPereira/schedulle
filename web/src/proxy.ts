import { NextResponse, type NextRequest } from "next/server";

/**
 * Guarda de rota leve: só verifica se o cookie de sessão existe e manda para /login.
 * A validação de verdade (cookie íntegro, inatividade, tokens) fica no layout do painel e no BFF.
 */
const COOKIE = "agenda_fono_sessao";
const PUBLICAS = ["/login"];

export function proxy(req: NextRequest) {
  const { pathname, search } = req.nextUrl;
  if (PUBLICAS.some((p) => pathname === p || pathname.startsWith(`${p}/`))) return NextResponse.next();
  if (req.cookies.has(COOKIE)) return NextResponse.next();

  const login = new URL("/login", req.url);
  if (pathname !== "/") login.searchParams.set("voltar", pathname + search);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|robots.txt).*)"],
};
