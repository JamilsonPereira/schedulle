package br.com.agendafono.pacientes;

/** LGPD arts. 11 e 14: sem consentimento do responsável não se cadastra dado de saúde do paciente. */
public class ConsentimentoAusenteException extends PacientesException {

    public ConsentimentoAusenteException() {
        super("consentimento-ausente", "O responsável ainda não deu consentimento para o tratamento dos dados");
    }
}
