import { useSyncExternalStore } from "react";

const assinar = () => () => {};

/** true só depois que o React assumiu a página no navegador (antes disso, botões não têm handlers). */
export function useHidratado(): boolean {
  return useSyncExternalStore(
    assinar,
    () => true,
    () => false,
  );
}
