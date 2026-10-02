import { cva, type VariantProps } from "class-variance-authority";
import { Slot } from "radix-ui";
import * as React from "react";
import { cn } from "@/lib/utils";

const variantes = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-padrao text-sm font-medium transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variante: {
        primaria: "bg-primaria text-primaria-texto hover:opacity-90",
        secundaria: "border border-borda bg-superficie text-texto hover:bg-superficie-2",
        fantasma: "text-texto hover:bg-superficie-2",
        perigo: "bg-perigo text-white hover:opacity-90",
        link: "text-primaria underline-offset-4 hover:underline px-0",
      },
      tamanho: {
        normal: "h-9 px-4",
        pequeno: "h-8 px-3 text-[13px]",
        icone: "size-9",
      },
    },
    defaultVariants: { variante: "primaria", tamanho: "normal" },
  },
);

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof variantes> {
  asChild?: boolean;
  carregando?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variante, tamanho, asChild, carregando, disabled, children, ...props }, ref) => {
    const Comp = asChild ? Slot.Root : "button";
    return (
      <Comp
        ref={ref}
        className={cn(variantes({ variante, tamanho }), className)}
        disabled={disabled || carregando}
        aria-busy={carregando || undefined}
        {...props}
      >
        {children}
      </Comp>
    );
  },
);
Button.displayName = "Button";
