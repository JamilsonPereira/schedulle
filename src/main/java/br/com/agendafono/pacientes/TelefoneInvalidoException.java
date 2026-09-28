package br.com.agendafono.pacientes;

public class TelefoneInvalidoException extends PacientesException {

    public TelefoneInvalidoException(String ignorado) {
        super("telefone-invalido", "Telefone inválido. Informe DDD e número, ex.: (11) 99999-0000");
    }
}
