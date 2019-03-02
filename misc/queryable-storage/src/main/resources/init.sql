DROP TABLE IF EXISTS USERS;
CREATE TABLE USERS (
    US_USERID VARCHAR PRIMARY KEY,
    US_PASSWORD VARCHAR,
    US_EMAIL VARCHAR
);

DROP TABLE IF EXISTS PAGAMENTO;
CREATE TABLE PAGAMENTO (
    PAG_CODICE BIGINT PRIMARY KEY,
    PAG_INT_CODICE BIGINT,
    PAG_IMPORTO NUMERIC(20, 2),
    PAG_DATA_PAGAMENTO DATE,
    PAG_CP_CODICE BIGINT,
    PAG_STATO BIGINT,
    PAG_CC VARCHAR,
    PAG_SISARE_TIPO VARCHAR
);
