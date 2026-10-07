package br.com.agendafono.bot.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Conversa de um responsável com o bot (tabela {@code conversa}). Um estado por vez. */
public final class Conversa {

    public enum Modo { BOT, HUMANO }

    /** Sem mensagem por esse tempo, a próxima interação recomeça do início (Especificação, seção 6). */
    public static final Duration REINICIO = Duration.ofMinutes(30);
    /** Na segunda resposta não entendida, a conversa vai para a recepção. */
    public static final int LIMITE_FALHAS = 2;

    private final UUID id;
    private final UUID clinicaId;
    private final UUID responsavelId;
    private EstadoConversa estado;
    private ContextoConversa contexto;
    private Modo modo;
    private int tentativasFalhas;
    private Instant ultimaMensagemEm;
    private int versao;
    private boolean nova;

    private Conversa(UUID id, UUID clinicaId, UUID responsavelId, EstadoConversa estado, ContextoConversa contexto,
                     Modo modo, int tentativasFalhas, Instant ultimaMensagemEm, int versao, boolean nova) {
        this.id = Objects.requireNonNull(id);
        this.clinicaId = Objects.requireNonNull(clinicaId);
        this.responsavelId = Objects.requireNonNull(responsavelId);
        this.estado = estado;
        this.contexto = contexto;
        this.modo = modo;
        this.tentativasFalhas = tentativasFalhas;
        this.ultimaMensagemEm = ultimaMensagemEm;
        this.versao = versao;
        this.nova = nova;
    }

    public static Conversa nova(UUID id, UUID clinicaId, UUID responsavelId) {
        return new Conversa(id, clinicaId, responsavelId, EstadoConversa.INICIO, ContextoConversa.vazio(), Modo.BOT,
                0, null, 0, true);
    }

    public static Conversa reconstituir(UUID id, UUID clinicaId, UUID responsavelId, EstadoConversa estado,
                                        ContextoConversa contexto, Modo modo, int tentativasFalhas,
                                        Instant ultimaMensagemEm, int versao) {
        return new Conversa(id, clinicaId, responsavelId, estado, contexto, modo, tentativasFalhas, ultimaMensagemEm,
                versao, false);
    }

    /** Recomeça do INICIO se ficou parada por mais de 30 minutos. */
    public void reiniciarSeParada(Instant agora) {
        if (ultimaMensagemEm != null && Duration.between(ultimaMensagemEm, agora).compareTo(REINICIO) > 0
                && estado != EstadoConversa.HUMANO) {
            recomecar();
        }
    }

    /** Volta ao INICIO, sem contexto (ex.: o consentimento foi revogado no meio da conversa). */
    public void recomecar() {
        estado = EstadoConversa.INICIO;
        contexto = ContextoConversa.vazio();
        tentativasFalhas = 0;
    }

    public void registrarMensagem(Instant agora) {
        ultimaMensagemEm = agora;
    }

    /** Aplica a transição. Devolve true se a conversa deve ir para a recepção. */
    public boolean aplicar(Transicao t) {
        contexto = t.contexto();
        switch (t.resultado()) {
            case NAO_ENTENDI -> {
                tentativasFalhas++;
                if (tentativasFalhas >= LIMITE_FALHAS) {
                    passarParaRecepcao();
                    return true;
                }
            }
            case TRANSBORDAR -> {
                passarParaRecepcao();
                return true;
            }
            case OK -> {
                estado = t.proximo();
                tentativasFalhas = 0;
            }
        }
        return false;
    }

    public void passarParaRecepcao() {
        modo = Modo.HUMANO;
        estado = EstadoConversa.HUMANO;
        tentativasFalhas = 0;
    }

    /** A recepção encerrou o atendimento no painel: o bot volta a responder, do menu. */
    public void devolverAoBot() {
        modo = Modo.BOT;
        estado = EstadoConversa.INICIO;
        contexto = ContextoConversa.vazio();
        tentativasFalhas = 0;
    }

    public void incrementarVersao() {
        versao++;
    }

    /** Chamado pelo repositório depois do INSERT. */
    public void marcarPersistida() {
        nova = false;
    }

    public UUID id() { return id; }
    public UUID clinicaId() { return clinicaId; }
    public UUID responsavelId() { return responsavelId; }
    public EstadoConversa estado() { return estado; }
    public ContextoConversa contexto() { return contexto; }
    public Modo modo() { return modo; }
    public int tentativasFalhas() { return tentativasFalhas; }
    public Instant ultimaMensagemEm() { return ultimaMensagemEm; }
    public int versao() { return versao; }
    public boolean nova() { return nova; }
}
