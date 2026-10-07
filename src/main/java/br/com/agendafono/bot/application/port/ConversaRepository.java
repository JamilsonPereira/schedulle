package br.com.agendafono.bot.application.port;

import br.com.agendafono.bot.domain.Conversa;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversaRepository {

    /** Conversa do responsável, travada (SELECT ... FOR UPDATE) até o fim da transação. */
    Optional<Conversa> doResponsavel(UUID clinicaId, UUID responsavelId);

    Optional<Conversa> buscar(UUID clinicaId, UUID conversaId);

    /**
     * Insere ou atualiza (controle otimista por versão).
     *
     * @throws org.springframework.dao.OptimisticLockingFailureException se outra transação alterou antes
     */
    void salvar(Conversa conversa);

    /** Apaga a conversa do responsável (anonimização a pedido do titular). */
    void apagarDoResponsavel(UUID clinicaId, UUID responsavelId);

    /** Conversas em modo HUMANO, da última mensagem mais antiga para a mais recente. */
    List<Conversa> emModoHumano(UUID clinicaId);
}
