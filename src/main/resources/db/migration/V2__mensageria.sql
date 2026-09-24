-- =====================================================================
-- V2 - Mensageria WhatsApp
-- Fila de entrada, outbox de envio e historico curto da conversa.
-- =====================================================================

CREATE TABLE evento_entrada (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    wamid                 text        NOT NULL,
    clinica_id            uuid        NOT NULL REFERENCES clinica (id),
    telefone              text        NOT NULL,
    phone_number_id       text        NOT NULL,
    tipo                  text        NOT NULL,
    texto                 text,
    payload               jsonb       NOT NULL,
    status                text        NOT NULL DEFAULT 'PENDENTE',
    tentativas            integer     NOT NULL DEFAULT 0,
    proxima_tentativa_em  timestamptz NOT NULL DEFAULT now(),
    recebido_em           timestamptz NOT NULL DEFAULT now(),
    processado_em         timestamptz,
    erro                  text,
    CONSTRAINT evento_entrada_wamid_uk UNIQUE (wamid),
    CONSTRAINT evento_entrada_status_ck CHECK (status IN ('PENDENTE', 'PROCESSANDO', 'PROCESSADO', 'MORTO')),
    CONSTRAINT evento_entrada_tipo_ck CHECK (tipo IN ('TEXT', 'INTERACTIVE', 'BUTTON', 'UNKNOWN')),
    CONSTRAINT evento_entrada_telefone_ck CHECK (telefone ~ '^[0-9]{8,15}$')
);
CREATE INDEX evento_entrada_fila_idx ON evento_entrada (status, proxima_tentativa_em, recebido_em);
CREATE INDEX evento_entrada_clinica_idx ON evento_entrada (clinica_id, recebido_em);

CREATE TABLE evento_saida (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id            uuid        NOT NULL REFERENCES clinica (id),
    phone_number_id       text        NOT NULL,
    telefone              text        NOT NULL,
    tipo                  text        NOT NULL,
    texto                 text        NOT NULL,
    payload               jsonb       NOT NULL DEFAULT '{}'::jsonb,
    status                text        NOT NULL DEFAULT 'PENDENTE',
    tentativas            integer     NOT NULL DEFAULT 0,
    proxima_tentativa_em  timestamptz NOT NULL DEFAULT now(),
    criado_em             timestamptz NOT NULL DEFAULT now(),
    enviado_em            timestamptz,
    erro                  text,
    CONSTRAINT evento_saida_status_ck CHECK (status IN ('PENDENTE', 'ENVIANDO', 'ENVIADO', 'MORTO')),
    CONSTRAINT evento_saida_tipo_ck CHECK (tipo IN ('TEXT')),
    CONSTRAINT evento_saida_telefone_ck CHECK (telefone ~ '^[0-9]{8,15}$')
);
CREATE INDEX evento_saida_fila_idx ON evento_saida (status, proxima_tentativa_em, criado_em);
CREATE INDEX evento_saida_clinica_idx ON evento_saida (clinica_id, criado_em);

CREATE TABLE mensagem (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL REFERENCES clinica (id),
    telefone         text        NOT NULL,
    direcao          text        NOT NULL,
    tipo             text        NOT NULL,
    texto            text,
    wamid            text,
    status_entrega   text,
    criada_em        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT mensagem_direcao_ck CHECK (direcao IN ('ENTRADA', 'SAIDA')),
    CONSTRAINT mensagem_tipo_ck CHECK (tipo IN ('TEXT', 'INTERACTIVE', 'BUTTON', 'UNKNOWN')),
    CONSTRAINT mensagem_telefone_ck CHECK (telefone ~ '^[0-9]{8,15}$')
);
CREATE INDEX mensagem_conversa_idx ON mensagem (clinica_id, telefone, criada_em);
