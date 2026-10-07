-- =====================================================================
-- V5 - Bot de agendamento (Passo 4)
-- * evento_saida aceita mensagens interativas (botões e listas da Cloud API)
-- * conversa: estados válidos, coerência modo/estado, mesma clínica do responsável e fila da recepção
-- =====================================================================

-- payload de INTERACTIVE = objeto "interactive" da Cloud API ({"type":"button"|"list", "body":..., "action":...})
ALTER TABLE evento_saida
    DROP CONSTRAINT evento_saida_tipo_ck,
    ADD CONSTRAINT evento_saida_tipo_ck CHECK (tipo IN ('TEXT', 'INTERACTIVE')),
    ADD CONSTRAINT evento_saida_interactive_ck
        CHECK (tipo <> 'INTERACTIVE' OR coalesce(payload ->> 'type', '') IN ('button', 'list'));

ALTER TABLE conversa
    ADD COLUMN criado_em     timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT conversa_estado_ck CHECK (estado IN ('INICIO', 'CONSENTIMENTO', 'MENU', 'PARA_QUEM', 'NOVO_NOME',
        'NOVO_NASCIMENTO', 'NOVA_DEMANDA', 'ESCOLHER_HORARIO', 'CONFIRMAR', 'HUMANO')),
    ADD CONSTRAINT conversa_modo_estado_ck CHECK ((modo = 'HUMANO') = (estado = 'HUMANO')),
    ADD CONSTRAINT conversa_tentativas_ck CHECK (tentativas_falhas >= 0),
    ADD CONSTRAINT conversa_responsavel_mesma_clinica_fk
        FOREIGN KEY (responsavel_id, clinica_id) REFERENCES responsavel (id, clinica_id);

-- Fila da recepção: conversas em atendimento humano por clínica
CREATE INDEX conversa_humano_idx ON conversa (clinica_id, ultima_msg_em) WHERE modo = 'HUMANO';
