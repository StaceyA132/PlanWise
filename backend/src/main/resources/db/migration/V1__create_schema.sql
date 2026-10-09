-- PlanWise schema. All money columns are NUMERIC(12,2): exact decimals, never floating point.

CREATE TABLE users (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email          VARCHAR(255)  NOT NULL UNIQUE,
    password_hash  VARCHAR(255)  NOT NULL,
    monthly_income NUMERIC(12,2) CHECK (monthly_income >= 0)
);

CREATE TABLE purchases (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    item_name  VARCHAR(200)  NOT NULL,
    amount     NUMERIC(12,2) NOT NULL CHECK (amount > 0 AND amount <= 10000),
    -- Filled in later by the AI insights feature, so it may be empty at first.
    category   VARCHAR(20)   CHECK (category IN ('ELECTRONICS', 'CLOTHING', 'HOME', 'TRAVEL', 'OTHER')),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_purchases_user_id ON purchases (user_id);

CREATE TABLE plans (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id    BIGINT        NOT NULL UNIQUE REFERENCES purchases (id),
    num_payments   INT           NOT NULL CHECK (num_payments > 0),
    frequency      VARCHAR(10)   NOT NULL CHECK (frequency IN ('BIWEEKLY', 'MONTHLY')),
    apr            NUMERIC(5,2)  NOT NULL CHECK (apr >= 0),
    total_interest NUMERIC(12,2) NOT NULL CHECK (total_interest >= 0),
    total_cost     NUMERIC(12,2) NOT NULL CHECK (total_cost > 0),
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('ACTIVE', 'PAID_OFF'))
);

CREATE TABLE payments (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id        BIGINT        NOT NULL REFERENCES plans (id),
    payment_number INT           NOT NULL CHECK (payment_number > 0),
    due_date       DATE          NOT NULL,
    amount         NUMERIC(12,2) NOT NULL CHECK (amount >= 0),
    paid_at        TIMESTAMPTZ,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('UPCOMING', 'PAID', 'LATE')),
    UNIQUE (plan_id, payment_number)
);
