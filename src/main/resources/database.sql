-- ===============================================================
-- StockSim - complete database schema
-- Run once:  mysql -u root -p < src/main/resources/database.sql
-- ===============================================================

DROP DATABASE IF EXISTS stocksim;
CREATE DATABASE stocksim CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE stocksim;

-- ---------------------------------------------------------------
-- 1. users
-- ---------------------------------------------------------------
CREATE TABLE users (
    user_id     INT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    username    VARCHAR(50)   NOT NULL UNIQUE,
    email       VARCHAR(100)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,              -- SHA-256 hash
    balance     DECIMAL(15,2) NOT NULL DEFAULT 100000.00,
    sim_day     INT           NOT NULL DEFAULT 1,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 2. stocks
-- ---------------------------------------------------------------
CREATE TABLE stocks (
    stock_id      INT AUTO_INCREMENT PRIMARY KEY,
    symbol        VARCHAR(20)   NOT NULL UNIQUE,
    company_name  VARCHAR(100)  NOT NULL,
    sector        VARCHAR(50)   NOT NULL,
    current_price DECIMAL(10,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 3. stock_history  (populated from the Kaggle CSV by DataSeeder)
-- ---------------------------------------------------------------
CREATE TABLE stock_history (
    history_id     INT AUTO_INCREMENT PRIMARY KEY,
    stock_id       INT           NOT NULL,
    price          DECIMAL(10,2) NOT NULL,
    recorded_at    DATE          NOT NULL,
    simulation_day INT           NOT NULL,
    CONSTRAINT fk_history_stock FOREIGN KEY (stock_id)
        REFERENCES stocks(stock_id) ON DELETE CASCADE,
    UNIQUE KEY uq_stock_day (stock_id, simulation_day),
    INDEX idx_history_stock_day (stock_id, simulation_day)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 4. holdings
-- ---------------------------------------------------------------
CREATE TABLE holdings (
    holding_id        INT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT           NOT NULL,
    stock_id          INT           NOT NULL,
    quantity          INT           NOT NULL,
    average_buy_price DECIMAL(10,2) NOT NULL,
    CONSTRAINT fk_holding_user  FOREIGN KEY (user_id)  REFERENCES users(user_id)   ON DELETE CASCADE,
    CONSTRAINT fk_holding_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE,
    UNIQUE KEY uq_user_stock (user_id, stock_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 5. transactions
-- ---------------------------------------------------------------
CREATE TABLE transactions (
    transaction_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT                 NOT NULL,
    stock_id         INT                 NOT NULL,
    transaction_type ENUM('BUY','SELL')  NOT NULL,
    quantity         INT                 NOT NULL,
    price            DECIMAL(10,2)       NOT NULL,
    total_amount     DECIMAL(15,2)       NOT NULL,
    transaction_date TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    simulation_day   INT                 NOT NULL,
    CONSTRAINT fk_txn_user  FOREIGN KEY (user_id)  REFERENCES users(user_id)   ON DELETE CASCADE,
    CONSTRAINT fk_txn_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE,
    INDEX idx_txn_user (user_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 6. goals
-- ---------------------------------------------------------------
CREATE TABLE goals (
    goal_id       INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT           NOT NULL,
    goal_name     VARCHAR(100)  NOT NULL,
    target_amount DECIMAL(15,2) NOT NULL,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 7. alerts
-- NOTE: `condition` is a reserved word in MySQL, so it is always backtick-quoted.
-- ---------------------------------------------------------------
CREATE TABLE alerts (
    alert_id     INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT                   NOT NULL,
    stock_id     INT                   NOT NULL,
    `condition`  ENUM('ABOVE','BELOW') NOT NULL,
    target_price DECIMAL(10,2)         NOT NULL,
    triggered    BOOLEAN               NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_alert_user  FOREIGN KEY (user_id)  REFERENCES users(user_id)   ON DELETE CASCADE,
    CONSTRAINT fk_alert_stock FOREIGN KEY (stock_id) REFERENCES stocks(stock_id) ON DELETE CASCADE,
    INDEX idx_alert_user (user_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------
-- 8. market_events
-- ---------------------------------------------------------------
CREATE TABLE market_events (
    event_id       INT AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    sector         VARCHAR(50),
    simulation_day INT          NOT NULL,
    impact         VARCHAR(100),
    INDEX idx_event_day (simulation_day)
) ENGINE=InnoDB;

-- ===============================================================
-- Seed: the 10 tradable NSE stocks (current_price is filled in by DataSeeder)
-- ===============================================================
INSERT INTO stocks (symbol, company_name, sector) VALUES
    ('TCS',        'Tata Consultancy Services', 'IT'),
    ('INFY',       'Infosys',                   'IT'),
    ('RELIANCE',   'Reliance Industries',       'Energy'),
    ('HDFCBANK',   'HDFC Bank',                 'Banking'),
    ('ITC',        'ITC Limited',               'FMCG'),
    ('WIPRO',      'Wipro',                     'IT'),
    ('ICICIBANK',  'ICICI Bank',                'Banking'),
    ('TATAMOTORS', 'Tata Motors',               'Auto'),
    ('SBIN',       'State Bank of India',       'Banking'),
    ('HINDUNILVR', 'Hindustan Unilever',        'FMCG');

-- ===============================================================
-- Seed: scripted market events shown as news cards on the dashboard
-- ===============================================================
INSERT INTO market_events (title, description, sector, simulation_day, impact) VALUES
    ('IT sector reports strong quarterly growth',
     'TCS, Infosys and Wipro all beat revenue estimates on the back of large deal wins.',
     'IT', 5, 'Positive'),
    ('RBI holds repo rate steady',
     'The central bank keeps policy rates unchanged, easing pressure on lenders.',
     'Banking', 12, 'Positive'),
    ('Crude oil prices spike on supply concerns',
     'Higher crude costs squeeze refining margins across the energy sector.',
     'Energy', 20, 'Negative'),
    ('Monsoon forecast above normal',
     'A strong monsoon is expected to lift rural demand for FMCG companies.',
     'FMCG', 30, 'Positive'),
    ('Auto sales slow on weak festive demand',
     'Passenger vehicle dispatches fall month on month across major manufacturers.',
     'Auto', 45, 'Negative'),
    ('Banking sector asset quality improves',
     'Gross NPA ratios decline for the third straight quarter at leading private banks.',
     'Banking', 60, 'Positive'),
    ('Rupee weakens against the dollar',
     'A weaker rupee lifts realisations for export-heavy IT services firms.',
     'IT', 75, 'Positive'),
    ('New GST slab announced for consumer goods',
     'Revised indirect tax rates change the pricing outlook for packaged goods.',
     'FMCG', 90, 'Neutral'),
    ('Reliance announces major retail expansion',
     'A large capital expenditure programme is announced for the retail arm.',
     'Energy', 120, 'Positive'),
    ('Global markets fall on recession fears',
     'Broad based selling hits every sector as global growth estimates are cut.',
     'ALL', 150, 'Negative'),
    ('Electric vehicle policy boosts auto makers',
     'Government incentives are extended for EV manufacturing and battery plants.',
     'Auto', 180, 'Positive'),
    ('IT hiring slows amid global uncertainty',
     'Headcount additions drop sharply as clients defer discretionary technology spend.',
     'IT', 210, 'Negative'),
    ('Record quarterly profit at State Bank of India',
     'Public sector banking profitability hits an all time high on treasury gains.',
     'Banking', 250, 'Positive'),
    ('Inflation cools to a two year low',
     'Softening inflation raises hopes of rate cuts and lifts broad market sentiment.',
     'ALL', 300, 'Positive'),
    ('FMCG margins pressured by input costs',
     'Palm oil and packaging costs rise, compressing gross margins.',
     'FMCG', 365, 'Negative');

SELECT 'StockSim schema created.' AS status;
SELECT COUNT(*) AS stocks_seeded FROM stocks;
SELECT COUNT(*) AS events_seeded FROM market_events;
