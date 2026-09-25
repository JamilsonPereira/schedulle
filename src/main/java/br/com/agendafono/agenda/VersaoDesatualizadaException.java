package br.com.agendafono.agenda;

/** Outra pessoa alterou a sessão depois da leitura (controle otimista pela coluna versao). */
public class VersaoDesatualizadaException extends AgendaException {

    public VersaoDesatualizadaException() {
        super("versao-desatualizada", "A sessão foi alterada por outra pessoa; recarregue e tente de novo");
    }
}
