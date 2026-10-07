CREATE TABLE app_users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    password VARCHAR(200) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT normalized_email CHECK (email = lower(btrim(email)) AND length(email) > 0),
    CONSTRAINT valid_display_name CHECK (length(btrim(display_name)) BETWEEN 1 AND 100),
    CONSTRAINT valid_password CHECK (length(password) BETWEEN 8 AND 200)
);
