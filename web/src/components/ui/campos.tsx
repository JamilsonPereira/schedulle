import * as React from "react";
import { cn } from "@/lib/utils";

const base =
  "w-full rounded-padrao border border-borda bg-superficie px-3 text-sm text-texto placeholder:text-texto-suave disabled:opacity-60 aria-[invalid=true]:border-perigo";

export const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, ...props }, ref) => <input ref={ref} className={cn(base, "h-9", className)} {...props} />,
);
Input.displayName = "Input";

export const Textarea = React.forwardRef<HTMLTextAreaElement, React.TextareaHTMLAttributes<HTMLTextAreaElement>>(
  ({ className, ...props }, ref) => <textarea ref={ref} className={cn(base, "min-h-20 py-2", className)} {...props} />,
);
Textarea.displayName = "Textarea";

/** Select nativo: acessível por padrão e leve. */
export const Select = React.forwardRef<HTMLSelectElement, React.SelectHTMLAttributes<HTMLSelectElement>>(
  ({ className, ...props }, ref) => <select ref={ref} className={cn(base, "h-9 pr-8", className)} {...props} />,
);
Select.displayName = "Select";

export function Label({ className, ...props }: React.LabelHTMLAttributes<HTMLLabelElement>) {
  return <label className={cn("text-texto text-sm font-medium", className)} {...props} />;
}

/** Rótulo + controle + dica + erro, com os ids de acessibilidade ligados. */
export function Campo({
  id,
  rotulo,
  erro,
  dica,
  children,
  className,
}: {
  id: string;
  rotulo: React.ReactNode;
  erro?: string;
  dica?: React.ReactNode;
  children: React.ReactElement<Record<string, unknown>>;
  className?: string;
}) {
  const descricao = [dica ? `${id}-dica` : null, erro ? `${id}-erro` : null].filter(Boolean).join(" ") || undefined;
  return (
    <div className={cn("flex flex-col gap-1.5", className)}>
      <Label htmlFor={id}>{rotulo}</Label>
      {React.cloneElement(children, {
        id,
        "aria-invalid": erro ? true : undefined,
        "aria-describedby": descricao,
      })}
      {dica && !erro && (
        <p id={`${id}-dica`} className="text-texto-suave text-xs">
          {dica}
        </p>
      )}
      {erro && (
        <p id={`${id}-erro`} className="text-perigo text-xs" role="alert">
          {erro}
        </p>
      )}
    </div>
  );
}

export function Checkbox({
  id,
  rotulo,
  className,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { id: string; rotulo: React.ReactNode }) {
  return (
    <label htmlFor={id} className={cn("inline-flex items-start gap-2 text-sm", className)}>
      <input id={id} type="checkbox" className="mt-0.5 size-4 accent-[var(--primaria)]" {...props} />
      <span>{rotulo}</span>
    </label>
  );
}
