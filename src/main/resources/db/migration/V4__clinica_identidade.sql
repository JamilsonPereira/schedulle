-- =====================================================================
-- V4 - Módulo clínica: identidade (usuários, refresh token), auditoria,
--      controle otimista, integridade entre clínicas e grade sem sobreposição.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Clínica
-- ---------------------------------------------------------------------
ALTER TABLE clinica
    ADD COLUMN versao        integer     NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT clinica_nome_ck CHECK (length(trim(nome)) BETWEEN 2 AND 120);

-- ---------------------------------------------------------------------
-- Usuário: e-mail sempre em minúsculas, bloqueio por tentativas, troca de senha obrigatória
-- ---------------------------------------------------------------------
ALTER TABLE usuario
    ADD COLUMN versao               integer     NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em        timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN tentativas_falhas    integer     NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_ate        timestamptz,
    ADD COLUMN precisa_trocar_senha boolean     NOT NULL DEFAULT true,
    ADD COLUMN ultimo_login_em      timestamptz,
    ADD CONSTRAINT usuario_email_minusculo_ck CHECK (email = lower(email)),
    ADD CONSTRAINT usuario_id_clinica_uk UNIQUE (id, clinica_id);

-- ---------------------------------------------------------------------
-- Profissional: vínculo opcional com um usuário FONO da mesma clínica
-- ---------------------------------------------------------------------
ALTER TABLE profissional
    ADD COLUMN versao        integer     NOT NULL DEFAULT 0,
    ADD COLUMN atualizado_em timestamptz NOT NULL DEFAULT now(),
    ADD CONSTRAINT profissional_id_clinica_uk UNIQUE (id, clinica_id),
    ADD CONSTRAINT profissional_usuario_mesma_clinica_fk
        FOREIGN KEY (usuario_id, clinica_id) REFERENCES usuario (id, clinica_id);
CREATE UNIQUE INDEX profissional_usuario_uk ON profissional (usuario_id) WHERE usuario_id IS NOT NULL;

-- ---------------------------------------------------------------------
-- Recurso (sala/cabine)
-- ---------------------------------------------------------------------
ALTER TABLE recurso
    ADD COLUMN versao integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT recurso_id_clinica_uk UNIQUE (id, clinica_id),
    ADD CONSTRAINT recurso_nome_uk UNIQUE (clinica_id, nome);

-- ---------------------------------------------------------------------
-- Grade: intervalos do mesmo profissional no mesmo dia não se sobrepõem
-- ---------------------------------------------------------------------
ALTER TABLE grade_semanal
    ADD CONSTRAINT grade_sem_sobreposicao EXCLUDE USING gist (
        profissional_id WITH =,
        dia_semana WITH =,
        tsrange(DATE '2000-01-01' + hora_inicio, DATE '2000-01-01' + hora_fim) WITH &&
    );

-- ---------------------------------------------------------------------
-- Integridade entre clínicas: nada aponta para profissional ou sala de outra clínica
-- ---------------------------------------------------------------------
ALTER TABLE bloqueio
    ADD CONSTRAINT bloqueio_profissional_mesma_clinica_fk
        FOREIGN KEY (profissional_id, clinica_id) REFERENCES profissional (id, clinica_id);
ALTER TABLE sessao
    ADD CONSTRAINT sessao_profissional_mesma_clinica_fk
        FOREIGN KEY (profissional_id, clinica_id) REFERENCES profissional (id, clinica_id),
    ADD CONSTRAINT sessao_recurso_mesma_clinica_fk
        FOREIGN KEY (recurso_id, clinica_id) REFERENCES recurso (id, clinica_id);
ALTER TABLE serie
    ADD CONSTRAINT serie_profissional_mesma_clinica_fk
        FOREIGN KEY (profissional_id, clinica_id) REFERENCES profissional (id, clinica_id);

-- ---------------------------------------------------------------------
-- Refresh token: opaco, guardado só como hash; rotação por família
-- ---------------------------------------------------------------------
CREATE TABLE refresh_token (
    id              uuid PRIMARY KEY,
    clinica_id      uuid        NOT NULL,
    usuario_id      uuid        NOT NULL,
    familia         uuid        NOT NULL,
    hash            text        NOT NULL UNIQUE,
    expira_em       timestamptz NOT NULL,
    usado_em        timestamptz,
    revogado_em     timestamptz,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT refresh_token_usuario_fk FOREIGN KEY (usuario_id, clinica_id) REFERENCES usuario (id, clinica_id)
);
CREATE INDEX refresh_token_familia_idx ON refresh_token (familia);
CREATE INDEX refresh_token_usuario_idx ON refresh_token (usuario_id) WHERE revogado_em IS NULL;

-- ---------------------------------------------------------------------
-- Auditoria (sem dados pessoais no detalhe)
-- ---------------------------------------------------------------------
CREATE TABLE auditoria (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id   uuid,
    usuario_id   uuid,
    acao         text        NOT NULL,
    entidade     text,
    entidade_id  uuid,
    ocorrido_em  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_clinica_idx ON auditoria (clinica_id, ocorrido_em);
CREATE INDEX auditoria_entidade_idx ON auditoria (entidade, entidade_id);
