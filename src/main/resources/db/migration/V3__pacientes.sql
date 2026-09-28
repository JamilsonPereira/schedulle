-- =====================================================================
-- V3 - Pacientes: consentimento LGPD, anexos, anonimização e
--      integridade entre clínicas (nenhuma linha aponta para dado de outra clínica).
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------
-- Responsável: consentimento atual, controle otimista e anonimização
-- ---------------------------------------------------------------------
ALTER TABLE responsavel RENAME COLUMN consentimento_wamid TO consentimento_evidencia;

ALTER TABLE responsavel
    ALTER COLUMN telefone_e164 DROP NOT NULL,              -- anonimização apaga o telefone
    ADD COLUMN consentimento_canal text,
    ADD COLUMN versao          integer     NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em   timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN anonimizado_em  timestamptz,
    ADD CONSTRAINT responsavel_consentimento_canal_ck
        CHECK (consentimento_canal IS NULL OR consentimento_canal IN ('WHATSAPP', 'PAINEL')),
    ADD CONSTRAINT responsavel_telefone_obrigatorio_ck
        CHECK (telefone_e164 IS NOT NULL OR anonimizado_em IS NOT NULL),
    ADD CONSTRAINT responsavel_id_clinica_uk UNIQUE (id, clinica_id);

-- ---------------------------------------------------------------------
-- Paciente
-- ---------------------------------------------------------------------
ALTER TABLE paciente
    ADD COLUMN versao         integer     NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em  timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN anonimizado_em timestamptz,
    ADD CONSTRAINT paciente_id_clinica_uk UNIQUE (id, clinica_id),
    ADD CONSTRAINT paciente_responsavel_mesma_clinica_fk
        FOREIGN KEY (responsavel_id, clinica_id) REFERENCES responsavel (id, clinica_id);

CREATE INDEX paciente_busca_nome_idx ON paciente USING gin (lower(nome) gin_trgm_ops);
CREATE INDEX paciente_clinica_nome_idx ON paciente (clinica_id, ativo, lower(nome));

-- Sessões, séries e lista de espera só podem apontar para paciente da mesma clínica
ALTER TABLE sessao
    ADD CONSTRAINT sessao_paciente_mesma_clinica_fk
        FOREIGN KEY (paciente_id, clinica_id) REFERENCES paciente (id, clinica_id);
ALTER TABLE serie
    ADD CONSTRAINT serie_paciente_mesma_clinica_fk
        FOREIGN KEY (paciente_id, clinica_id) REFERENCES paciente (id, clinica_id);
ALTER TABLE lista_espera
    ADD CONSTRAINT lista_espera_paciente_mesma_clinica_fk
        FOREIGN KEY (paciente_id, clinica_id) REFERENCES paciente (id, clinica_id);

-- ---------------------------------------------------------------------
-- Histórico de consentimento (prova do aceite e da revogação; nunca é apagado)
-- ---------------------------------------------------------------------
CREATE TABLE consentimento (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id      uuid        NOT NULL,
    responsavel_id  uuid        NOT NULL,
    acao            text        NOT NULL,
    versao_texto    text        NOT NULL,
    canal           text        NOT NULL,
    evidencia       text        NOT NULL,
    registrado_em   timestamptz NOT NULL,
    CONSTRAINT consentimento_responsavel_fk
        FOREIGN KEY (responsavel_id, clinica_id) REFERENCES responsavel (id, clinica_id),
    CONSTRAINT consentimento_acao_ck CHECK (acao IN ('CONCEDIDO', 'REVOGADO')),
    CONSTRAINT consentimento_canal_ck CHECK (canal IN ('WHATSAPP', 'PAINEL'))
);
CREATE INDEX consentimento_responsavel_idx ON consentimento (responsavel_id, registrado_em);

-- ---------------------------------------------------------------------
-- Anexos (pedido médico, documentos). O arquivo fica no armazenamento; aqui só metadados.
-- ---------------------------------------------------------------------
CREATE TABLE anexo (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id           uuid        NOT NULL,
    paciente_id          uuid        NOT NULL,
    tipo                 text        NOT NULL,
    nome_arquivo         text        NOT NULL,
    content_type         text        NOT NULL,
    tamanho_bytes        bigint      NOT NULL,
    sha256               text        NOT NULL,
    chave_armazenamento  text        NOT NULL UNIQUE,
    origem               text        NOT NULL,
    criado_em            timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT anexo_paciente_fk
        FOREIGN KEY (paciente_id, clinica_id) REFERENCES paciente (id, clinica_id),
    CONSTRAINT anexo_tipo_ck CHECK (tipo IN ('PEDIDO_MEDICO', 'DOCUMENTO', 'OUTRO')),
    CONSTRAINT anexo_origem_ck CHECK (origem IN ('WHATSAPP', 'PAINEL')),
    CONSTRAINT anexo_content_type_ck CHECK (content_type IN ('image/jpeg', 'image/png', 'application/pdf')),
    CONSTRAINT anexo_tamanho_ck CHECK (tamanho_bytes > 0 AND tamanho_bytes <= 10485760)
);
CREATE INDEX anexo_paciente_idx ON anexo (paciente_id, criado_em);
