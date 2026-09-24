package br.com.agendafono.mensageria;

public interface ProvedorWhatsApp {

    void enviarTexto(String phoneNumberId, String telefone, String texto);
}
