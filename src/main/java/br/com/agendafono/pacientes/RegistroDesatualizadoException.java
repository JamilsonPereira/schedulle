package br.com.agendafono.pacientes;

/** Outra pessoa alterou o registro depois da leitura (controle otimista pela coluna versao). */
public class RegistroDesatualizadoException extends PacientesException {

    public RegistroDesatualizadoException() {
        super("versao-desatualizada", "O registro foi alterado por outra pessoa; recarregue e tente de novo");
    }
}
