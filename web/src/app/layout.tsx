import type { Metadata } from "next";
import { Provedores } from "@/components/provedores";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: "Agenda Fono", template: "%s · Agenda Fono" },
  description: "Painel da clínica: agenda, pacientes e configurações.",
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className="h-full antialiased">
      <body className="min-h-full">
        <Provedores>{children}</Provedores>
      </body>
    </html>
  );
}
