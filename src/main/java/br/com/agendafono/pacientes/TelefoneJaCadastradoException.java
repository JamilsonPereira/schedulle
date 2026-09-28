package br.com.agendafono.pacientes;

public class TelefoneJaCadastradoException extends PacientesException {

    public TelefoneJaCadastradoException() {
        super("telefone-ja-cadastrado", "Já existe um responsável com este telefone nesta clínica");
    }
}
