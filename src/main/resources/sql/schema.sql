CREATE TABLE users(
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPZ
);

CREATE TABLE products(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    stock       INTEGER NOT NULL CHECK (stock >= 0),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ
);

CREATE TABLE orders(
    id              BIGSERIAL PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    user_id         BIGINT REFERENCES users(id),
    total_amount    BIGINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ
);
