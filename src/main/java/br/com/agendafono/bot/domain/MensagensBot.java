package br.com.agendafono.bot.domain;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Catálogo de textos do bot (SDD backend, seção 8). Todo texto que o contato lê sai daqui, para ajustar o tom num
 * lugar só e testar por comparação. IDs de botões e itens são estáveis: o clique volta com eles.
 */
public final class MensagensBot {

    private MensagensBot() {
    }

    // ------------------------------------------------------------------ ids de opções

    public static final String ACEITO = "consentimento:aceito";
    public static final String NAO_ACEITO = "consentimento:recusado";
    public static final String AGENDAR = "menu:agendar";
    public static final String RECEPCAO = "menu:recepcao";
    public static final String NOVO_PACIENTE = "paciente:novo";
    public static final String VER_MAIS = "horario:mais";
    public static final String CONFIRMAR = "reserva:confirmar";
    public static final String TROCAR = "reserva:trocar";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DIA_HORA = DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH:mm", PT_BR);

    /** Ordem e nomes da lista de demandas, como na Especificação do MVP. */
    public static final Map<String, String> DEMANDAS = ordenado(
            "LINGUAGEM", "Fala e linguagem",
            "GAGUEIRA", "Gagueira",
            "VOZ", "Voz",
            "DEGLUTICAO", "Deglutição",
            "MOTRICIDADE_OROFACIAL", "Motricidade orofacial",
            "AUDICAO", "Audição",
            "OUTRO", "Outro");

    // ------------------------------------------------------------------ consentimento e menu

    public static MensagemSaida consentimento(String clinica, String urlPolitica) {
        return new MensagemSaida.Botoes("""
                Olá! Aqui é o atendimento da %s.

                Para agendar, vamos guardar o nome e a data de nascimento do paciente, o motivo da consulta e este \
                telefone. Usamos esses dados só para o atendimento na clínica e você pode pedir a exclusão a \
                qualquer momento.

                Política de privacidade: %s

                Você concorda?""".formatted(clinica, urlPolitica),
                List.of(new Opcao(ACEITO, "Aceito"), new Opcao(NAO_ACEITO, "Não aceito")));
    }

    public static MensagemSaida semConsentimento() {
        return new MensagemSaida.Botoes("""
                Tudo bem. Sem o aceite não conseguimos agendar por aqui, mas a recepção pode te atender.""",
                List.of(new Opcao(ACEITO, "Aceito"), new Opcao(RECEPCAO, "Falar com a recepção")));
    }

    public static MensagemSaida menu(String clinica) {
        return new MensagemSaida.Botoes("Como podemos ajudar? Escolha uma opção:",
                List.of(new Opcao(AGENDAR, "Agendar avaliação"), new Opcao(RECEPCAO, "Falar com a recepção")));
    }

    public static MensagemSaida transbordo() {
        return new MensagemSaida.Texto(
                "Certo! Uma pessoa da recepção vai continuar a conversa por aqui. Aguarde só um pouquinho.");
    }

    public static MensagemSaida naoEntendi() {
        return new MensagemSaida.Texto("Não entendi. Toque numa das opções abaixo, por favor.");
    }

    // ------------------------------------------------------------------ paciente

    public static MensagemSaida paraQuem(List<PacienteDoContato> pacientes) {
        List<Opcao> itens = new ArrayList<>();
        pacientes.forEach(p -> itens.add(new Opcao(p.id(), p.nome(), null)));
        itens.add(new Opcao(NOVO_PACIENTE, "Outra pessoa", "Cadastrar um novo paciente"));
        return new MensagemSaida.Lista("Para quem é a avaliação?", "Escolher", itens);
    }

    public static MensagemSaida perguntarNome() {
        return new MensagemSaida.Texto("Qual é o nome completo do paciente?");
    }

    public static MensagemSaida nomeInvalido() {
        return new MensagemSaida.Texto("Pode mandar o nome completo do paciente? (só o nome, com pelo menos 2 letras)");
    }

    public static MensagemSaida perguntarNascimento(String nome) {
        return new MensagemSaida.Texto("Qual é a data de nascimento de %s? Ex.: 10/03/2019".formatted(primeiroNome(nome)));
    }

    public static MensagemSaida nascimentoInvalido() {
        return new MensagemSaida.Texto("Não consegui entender a data. Mande no formato dia/mês/ano, por exemplo 10/03/2019.");
    }

    public static MensagemSaida perguntarDemanda(String nome) {
        List<Opcao> itens = DEMANDAS.entrySet().stream().map(e -> new Opcao("demanda:" + e.getKey(), e.getValue())).toList();
        return new MensagemSaida.Lista("Qual é o principal motivo da consulta de %s?".formatted(primeiroNome(nome)),
                "Ver opções", itens);
    }

    // ------------------------------------------------------------------ horários

    public static MensagemSaida horarios(String nomePaciente, List<HorarioOferta> horarios, ZoneId fuso,
                                         boolean podeVerMais) {
        List<Opcao> itens = new ArrayList<>();
        for (HorarioOferta h : horarios) {
            itens.add(new Opcao(h.id(), diaHora(h.inicio(), fuso), "com " + h.profissionalNome()));
        }
        if (podeVerMais) {
            itens.add(new Opcao(VER_MAIS, "Ver mais datas", null));
        }
        itens.add(new Opcao(RECEPCAO, "Falar com a recepção", null));
        return new MensagemSaida.Lista("Escolha um horário para a avaliação de %s:".formatted(primeiroNome(nomePaciente)),
                "Ver horários", itens);
    }

    public static MensagemSaida semHorarios() {
        return new MensagemSaida.Texto(
                "Não encontrei horários livres nas próximas semanas. Vou passar para a recepção te ajudar.");
    }

    public static MensagemSaida horarioOcupado() {
        return new MensagemSaida.Texto("Esse horário acabou de ser ocupado. Veja as outras opções:");
    }

    public static MensagemSaida confirmar(String nomePaciente, HorarioOferta h, ZoneId fuso, int minutosParaConfirmar) {
        return new MensagemSaida.Botoes("""
                Confira a avaliação:

                Paciente: %s
                Profissional: %s
                Quando: %s

                O horário fica reservado por %d minutos.""".formatted(nomePaciente, h.profissionalNome(),
                diaHora(h.inicio(), fuso), minutosParaConfirmar),
                List.of(new Opcao(CONFIRMAR, "Confirmar"), new Opcao(TROCAR, "Trocar horário")));
    }

    public static MensagemSaida agendado(String nomePaciente, HorarioOferta h, ZoneId fuso) {
        return new MensagemSaida.Texto("""
                Pronto! A avaliação de %s está agendada para %s com %s.

                Se precisar de algo, é só mandar uma mensagem por aqui.""".formatted(primeiroNome(nomePaciente),
                diaHora(h.inicio(), fuso), h.profissionalNome()));
    }

    public static MensagemSaida reservaVencida() {
        return new MensagemSaida.Texto("O tempo para confirmar acabou e o horário foi liberado. Veja as opções de novo:");
    }

    // ------------------------------------------------------------------ auxiliares

    public static String diaHora(Instant instante, ZoneId fuso) {
        String texto = DIA_HORA.format(instante.atZone(fuso));
        return texto.replace(".", "");
    }

    static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "o paciente";
        }
        return nome.trim().split("\\s+")[0];
    }

    private static Map<String, String> ordenado(String... pares) {
        Map<String, String> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put(pares[i], pares[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
