# StockSim

An educational stock market simulator built with **JavaFX + MySQL + JDBC**.
Practise investing in 10 real NSE stocks using **virtual money only** — no real
funds are involved at any point.



---

## What it does

Register → get ₹1,00,000 of virtual capital → research stocks on real historical
price charts → buy and sell → advance simulated time → watch the portfolio
re-value → manage risk, goals and alerts → export reports.

---

## Requirements

| Tool | Version used |
|---|---|
| JDK | 17 or newer (built and run on 25/27) |
| Maven | 3.9+ |
| MySQL | 8.0 or newer |
| Kaggle CSV | `stocks_df.csv` — "India Stock Market (daily updated)" by Larxel |

---

## First-time setup

```bash
cd ~/Documents/Projects/StockSim

# 1. Create the database, tables, the 10 stocks and the scripted market events
mysql -u root -p < src/main/resources/database.sql

# 2. Check the two paths in src/main/resources/application.properties
#      db.user / db.password   -> your MySQL credentials
#      csv.path                -> where stocks_df.csv lives

# 3. Compile
mvn clean compile

# 4. Run. On the very first launch DataSeeder imports the CSV into MySQL
#    (about 2 seconds); after that the CSV is never opened again.
mvn javafx:run
```

## Every time after

```bash
mvn javafx:run
```

## Running the tests

```bash
mvn test
```

90 tests run against the real MySQL database and a real JavaFX toolkit:

| Suite | What it covers |
|---|---|
| `UnitBasicsTest` | models, validation, hashing, generics, formatting |
| `DatabaseAndDaoTest` | schema, seeded data, all 8 DAOs |
| `ServiceLayerTest` | auth, trading, portfolio, analysis, risk, simulation, alerts, exports, serialization |
| `UiScreenTest` | every FXML screen loads, wires up and renders real data |
| `UserJourneyTest` | the full documented journey end to end, in order |

Tests create their own throwaway users and delete them again, so your own
account and portfolio are never touched.

---

## How the simulation works

Real daily closing prices from the NSE (2008-01-02 to 2022-01-11) are imported
into `stock_history` on first launch. **Simulation day N is the same real
calendar date for all 10 stocks**, which is what makes portfolio values
meaningful.

The 10 stocks do not all start trading on the same date — HINDUNILVR's data only
begins in 2008, TCS in 2004, the rest in the 1990s. So `DataSeeder` keeps only
the trading dates that **all ten** stocks share, sorts them, and numbers those
1…3466. If it instead numbered each stock's own rows, "day 100" would be a
different date for every stock and every portfolio total would be wrong.

Clicking **NEXT DAY / WEEK / MONTH**:

1. moves the user's `sim_day` forward (clamped at day 3466, the last day with data)
2. repoints every `stocks.current_price` at that day's real historical close
3. fires any scripted market events in the range
4. checks the user's price alerts across **every** intervening day, so a month
   jump cannot skip over an alert that was only satisfied mid-month

---

## Project layout

```
src/main/java/com/stocksim/
├── Main.java              JavaFX Application: init() → start() → stop()
├── model/                 User, Stock, Holding, Transaction, Goal, PriceAlert, …
│                          plus abstract FinancialEntity and the
│                          Calculable / Tradable interfaces
├── dao/                   one DAO per table, all PreparedStatement
├── service/               business logic (auth, trading, portfolio, analysis,
│                          risk, simulation, alerts, reports, state)
├── exception/             4 custom checked exceptions
├── util/                  DBConnection, ConfigLoader, DataSeeder, Validator,
│                          PasswordUtil, UserSession, DataCache<T>, SceneManager,
│                          Formats, TableSupport
└── controller/            one controller per screen, sharing BaseController

src/main/resources/
├── com/stocksim/fxml/     12 screens
├── styles/main.css        dark finance theme
├── application.properties configuration
└── database.sql           full schema + seed data
```

---

## Changing the text size

Every font size, padding and row height in the app is expressed in `em`, relative
to a single base size at the top of `src/main/resources/styles/main.css`:

```css
.root {
    -fx-font-size: 17px;     /* <-- change this one number */
}
```

| Value | Result |
|---|---|
| `15px` | compact |
| `17px` | **default** |
| `19px` | large |
| `21px` | extra large |
| `23px` | presentation / projector |

Change it, run `mvn javafx:run`, and headings, cards, tables, buttons, charts and
dialogs all scale together. Table columns share the table's width rather than
using fixed pixel widths, so nothing gets clipped at larger sizes, and the window
sizes itself against whatever screen it opens on.

---

## Notes worth knowing

- **Passwords** are stored as SHA-256 hashes; plain text never reaches MySQL.
- **Every trade is a JDBC transaction.** A buy updates the balance, upserts the
  holding and inserts the transaction row; `setAutoCommit(false)` then
  `commit()`, or `rollback()` if any step fails.
- **`condition` is a reserved word in MySQL.** In the `alerts` table it is
  backtick-quoted in every statement in `AlertDAO`.
- **Reports** are written to `reports/` as UTF-8 plain text via `BufferedWriter`.
- **`simulation_state.ser`** is written by `ObjectOutputStream` from the
  dashboard's SAVE STATE button and read back by LOAD STATE.
- Prices are **split-adjusted**, which is why TCS shows around ₹253 in January
  2008 rather than its headline price at the time.
