-- ============================================
-- Mosco Database Schema for MySQL
-- ============================================
-- Run this file once to initialize the database:
--   mysql -u root -p < schema-mysql.sql
-- After that, Hibernate (ddl-auto=update) manages schema evolution.

CREATE DATABASE IF NOT EXISTS mosco_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE mosco_db;

-- ── Users ────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    username       VARCHAR(255)  NULL,
    email          VARCHAR(255)  NOT NULL,
    password_hash  VARCHAR(255)  NOT NULL,
    coins          BIGINT        NOT NULL DEFAULT 0,
    diamonds       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Shop Items (gacha_items catalog) ─────────
CREATE TABLE IF NOT EXISTS shop_items (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    product_code   VARCHAR(255)  NOT NULL,
    name           VARCHAR(255)  NOT NULL,
    description    TEXT          NULL,
    type           VARCHAR(50)   NOT NULL,
    price_coins    BIGINT        NOT NULL DEFAULT 0,
    price_diamonds BIGINT        NOT NULL DEFAULT 0,
    image_uri      VARCHAR(512)  NULL,
    end_time       BIGINT        NOT NULL DEFAULT -1,
    metadata       TEXT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_items_code (product_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── User Items (Inventory) ───────────────────
CREATE TABLE IF NOT EXISTS user_items (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    item_code  VARCHAR(255) NOT NULL,
    quantity   INT          NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_items_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── User Cards (Collection) ──────────────────
CREATE TABLE IF NOT EXISTS user_cards (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL,
    collection_id  VARCHAR(255) NOT NULL,
    upgrade_level  INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_cards_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── User Mails ───────────────────────────────
CREATE TABLE IF NOT EXISTS user_mails (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    user_id   BIGINT       NOT NULL,
    title     VARCHAR(255) NOT NULL,
    content   TEXT         NULL,
    item_code VARCHAR(255) NULL,
    quantity  INT          NOT NULL DEFAULT 0,
    is_read   BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_mails_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ── Gacha History (NEW — Task 1) ─────────────
CREATE TABLE IF NOT EXISTS gacha_history (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    item_id    VARCHAR(255) NOT NULL,
    rarity     VARCHAR(50)  NOT NULL,
    quantity   INT          NOT NULL DEFAULT 1,
    rolled_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    pack_code  VARCHAR(100) NULL,
    source     VARCHAR(50)  NOT NULL DEFAULT 'GACHA_ROLL',
    PRIMARY KEY (id),
    CONSTRAINT fk_gacha_history_user FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_gacha_history_user (user_id),
    INDEX idx_gacha_history_rolled (rolled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
