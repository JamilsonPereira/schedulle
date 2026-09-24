-- =====================================================================
-- V1 - Schema inicial do agenda-fono
-- Multi-tenant por coluna: toda tabela de negócio tem clinica_id.
-- MVP apenas particular: convênio, guias e carteirinha entram numa migration futura.
-- Horários sempre em timestamptz (UTC); exibição no fuso da clínica.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------------
-- Clínica (tenant) e usuários do painel
-- ---------------------------------------------------------------------
CREATE TABLE clinica (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    nome                     text        NOT NULL,
    fuso                     text        NOT NULL DEFAULT 'America/Sao_Paulo',
    whatsapp_phone_number_id text UNIQUE,
    waba_id                  text,
    politica                 jsonb       NOT NULL DEFAULT '{}'::jsonb,
    criado_em                timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id  uuid        NOT NULL REFERENCES clinica (id),
    nome        text        NOT NULL,
    email       text        NOT NULL,
    senha_hash  text        NOT NULL,
    papeis      text[]      NOT NULL,
    ativo       boolean     NOT NULL DEFAULT true,
    criado_em   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT usuario_email_uk UNIQUE (email),
    CONSTRAINT usuario_papeis_ck CHECK (papeis <@ ARRAY['ADMIN', 'RECEPCAO', 'FONO']::text[] AND cardinality(papeis) > 0)
);

-- ---------------------------------------------------------------------
-- Cadastros da clínica
-- ---------------------------------------------------------------------
CREATE TABLE recurso (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id  uuid    NOT NULL REFERENCES clinica (id),
    nome        text    NOT NULL,
    tipo        text    NOT NULL,
    ativo       boolean NOT NULL DEFAULT true,
    CONSTRAINT recurso_tipo_ck CHECK (tipo IN ('SALA', 'CABINE'))
);

CREATE TABLE profissional (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id          uuid    NOT NULL REFERENCES clinica (id),
    usuario_id          uuid REFERENCES usuario (id),
    nome                text    NOT NULL,
    registro_crfa       text,
    subareas            text[]  NOT NULL DEFAULT '{}',
    duracao_padrao_min  integer NOT NULL DEFAULT 40,
    ativo               boolean NOT NULL DEFAULT true,
    CONSTRAINT profissional_duracao_ck CHECK (duracao_padrao_min BETWEEN 10 AND 240)
);

-- Grade semanal: dia_semana no padrão ISO (1 = segunda ... 7 = domingo)
CREATE TABLE grade_semanal (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    profissional_id uuid     NOT NULL REFERENCES profissional (id) ON DELETE CASCADE,
    dia_semana      smallint NOT NULL,
    hora_inicio     time     NOT NULL,
    hora_fim        time     NOT NULL,
    recurso_id      uuid REFERENCES recurso (id),
    CONSTRAINT grade_dia_ck CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT grade_horario_ck CHECK (hora_fim > hora_inicio)
);
CREATE INDEX grade_profissional_idx ON grade_semanal (profissional_id, dia_semana);

-- Bloqueio de agenda; profissional_id nulo = clínica inteira (ex.: feriado)
CREATE TABLE bloqueio (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id      uuid        NOT NULL REFERENCES clinica (id),
    profissional_id uuid REFERENCES profissional (id),
    periodo         tstzrange   NOT NULL,
    motivo          text,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT bloqueio_periodo_ck CHECK (NOT isempty(periodo) AND NOT lower_inf(periodo) AND NOT upper_inf(periodo))
);
CREATE INDEX bloqueio_periodo_idx ON bloqueio USING gist (clinica_id, periodo);

-- ---------------------------------------------------------------------
-- Responsáveis e pacientes
-- ---------------------------------------------------------------------
CREATE TABLE responsavel (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id            uuid        NOT NULL REFERENCES clinica (id),
    nome                  text,
    telefone_e164         text        NOT NULL,
    consentimento_em      timestamptz,
    consentimento_versao  text,
    consentimento_wamid   text,
    criado_em             timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT responsavel_telefone_uk UNIQUE (clinica_id, telefone_e164),
    CONSTRAINT responsavel_telefone_ck CHECK (telefone_e164 ~ '^\+[1-9][0-9]{7,14}$')
);

CREATE TABLE paciente (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL REFERENCES clinica (id),
    responsavel_id   uuid        NOT NULL REFERENCES responsavel (id),
    nome             text        NOT NULL,
    data_nascimento  date,
    demanda          text,
    ativo            boolean     NOT NULL DEFAULT true,
    criado_em        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT paciente_demanda_ck CHECK (demanda IS NULL OR demanda IN
        ('LINGUAGEM', 'GAGUEIRA', 'VOZ', 'DEGLUTICAO', 'MOTRICIDADE_OROFACIAL', 'AUDICAO', 'OUTRO'))
);
CREATE INDEX paciente_responsavel_idx ON paciente (responsavel_id);

-- ---------------------------------------------------------------------
-- Séries recorrentes e sessões
-- ---------------------------------------------------------------------
-- dias_semana no padrão ISO (1 = segunda ... 7 = domingo)
CREATE TABLE serie (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL REFERENCES clinica (id),
    paciente_id      uuid        NOT NULL REFERENCES paciente (id),
    profissional_id  uuid        NOT NULL REFERENCES profissional (id),
    recurso_id       uuid REFERENCES recurso (id),
    dias_semana      smallint[]  NOT NULL,
    hora             time        NOT NULL,
    duracao_min      integer     NOT NULL,
    inicio           date        NOT NULL,
    fim              date,
    status           text        NOT NULL DEFAULT 'ATIVA',
    criado_em        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT serie_status_ck CHECK (status IN ('ATIVA', 'PAUSADA', 'ENCERRADA')),
    CONSTRAINT serie_dias_ck CHECK (cardinality(dias_semana) > 0 AND dias_semana <@ ARRAY[1,2,3,4,5,6,7]::smallint[]),
    CONSTRAINT serie_duracao_ck CHECK (duracao_min BETWEEN 10 AND 240),
    CONSTRAINT serie_fim_ck CHECK (fim IS NULL OR fim >= inicio)
);

CREATE TABLE sessao (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL REFERENCES clinica (id),
    paciente_id      uuid        NOT NULL REFERENCES paciente (id),
    profissional_id  uuid        NOT NULL REFERENCES profissional (id),
    recurso_id       uuid REFERENCES recurso (id),
    serie_id         uuid REFERENCES serie (id),
    tipo             text        NOT NULL,
    periodo          tstzrange   NOT NULL,
    status           text        NOT NULL,
    expira_em        timestamptz,
    lembrete_enviado_em timestamptz,
    versao           integer     NOT NULL DEFAULT 0,
    criado_em        timestamptz NOT NULL DEFAULT now(),
    atualizado_em    timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT sessao_tipo_ck CHECK (tipo IN ('AVALIACAO', 'TERAPIA', 'REPOSICAO', 'DEVOLUTIVA')),
    CONSTRAINT sessao_status_ck CHECK (status IN
        ('RESERVADA', 'AGENDADA', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'FALTA_AVISADA', 'FALTA_SEM_AVISO')),
    CONSTRAINT sessao_periodo_ck CHECK (NOT isempty(periodo) AND NOT lower_inf(periodo) AND NOT upper_inf(periodo)),
    CONSTRAINT sessao_reserva_ck CHECK (status <> 'RESERVADA' OR expira_em IS NOT NULL),

    -- RN-03: um profissional não tem duas sessões ativas sobrepostas
    CONSTRAINT sessao_sem_conflito_prof EXCLUDE USING gist (
        profissional_id WITH =, periodo WITH &&
    ) WHERE (status IN ('RESERVADA', 'AGENDADA', 'CONFIRMADA')),

    -- RN-03: uma sala/cabine não tem duas sessões ativas sobrepostas
    CONSTRAINT sessao_sem_conflito_recurso EXCLUDE USING gist (
        recurso_id WITH =, periodo WITH &&
    ) WHERE (recurso_id IS NOT NULL AND status IN ('RESERVADA', 'AGENDADA', 'CONFIRMADA'))
);
CREATE INDEX sessao_agenda_idx ON sessao (clinica_id, profissional_id, lower(periodo));
CREATE INDEX sessao_paciente_idx ON sessao (paciente_id, lower(periodo));
CREATE INDEX sessao_reserva_exp_idx ON sessao (expira_em) WHERE status = 'RESERVADA';
CREATE INDEX sessao_serie_idx ON sessao (serie_id) WHERE serie_id IS NOT NULL;

CREATE TABLE lista_espera (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id       uuid        NOT NULL REFERENCES clinica (id),
    paciente_id      uuid        NOT NULL REFERENCES paciente (id),
    profissional_id  uuid REFERENCES profissional (id),
    preferencias     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    ativo            boolean     NOT NULL DEFAULT true,
    criado_em        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX lista_espera_fila_idx ON lista_espera (clinica_id, profissional_id, criado_em) WHERE ativo;

-- ---------------------------------------------------------------------
-- Conversas no WhatsApp
-- ---------------------------------------------------------------------
CREATE TABLE conversa (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    clinica_id      uuid        NOT NULL REFERENCES clinica (id),
    responsavel_id  uuid        NOT NULL REFERENCES responsavel (id),
    estado          text        NOT NULL DEFAULT 'INICIO',
    contexto        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    modo            text        NOT NULL DEFAULT 'BOT',
    tentativas_falhas smallint  NOT NULL DEFAULT 0,
    ultima_msg_em   timestamptz,
    versao          integer     NOT NULL DEFAULT 0,
    CONSTRAINT conversa_responsavel_uk UNIQUE (responsavel_id),
    CONSTRAINT conversa_modo_ck CHECK (modo IN ('BOT', 'HUMANO'))
);

-- Deduplicação de eventos do webhook (a Meta pode reentregar o mesmo wamid)
CREATE TABLE mensagem_processada (
    wamid        text PRIMARY KEY,
    clinica_id   uuid REFERENCES clinica (id),
    recebido_em  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mensagem_processada_recebido_idx ON mensagem_processada (recebido_em);
