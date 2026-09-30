package br.com.agendafono.compartilhado.seguranca;

/** Nomes dos claims próprios do JWT de acesso. Nenhum dado pessoal vai no token. */
public final class ClaimsJwt {

    public static final String CLINICA = "cid";
    public static final String PAPEIS = "roles";
    public static final String PROFISSIONAL = "pid";

    private ClaimsJwt() {
    }
}
