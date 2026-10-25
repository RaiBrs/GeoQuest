<div align="center">

# 🌍 GeoQuest

### A minimal terminal geography quiz built in Java

<p>
  <img src="https://img.shields.io/badge/Java-25-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 25">
  <img src="https://img.shields.io/badge/Interface-Terminal-1F2937?style=for-the-badge&logo=windowsterminal&logoColor=white" alt="Terminal interface">
  <img src="https://img.shields.io/badge/API-REST%20Countries-2563EB?style=for-the-badge" alt="REST Countries API">
  <img src="https://img.shields.io/badge/Status-Learning%20Project-7C3AED?style=for-the-badge" alt="Learning project">
</p>

<sub>✨ Learn Java by building something real.</sub>

</div>

---

<details>
<summary>🗂️ <b>Table of contents</b></summary>

<br>

- [Features](#-features)
- [Run locally](#-run-locally)
- [API key](#-api-key)
- [Tests](#-tests)
- [How it works](#-how-it-works)
- [Project structure](#-project-structure)
- [Current limitations](#-current-limitations)
- [Next steps](#-next-steps)

</details>

## ✨ Features

- 🌍 Fetch the complete paginated country catalog from the REST Countries API
- 🏛️ Ask capital-city questions using real API data
- 🎯 Ten multiple-choice questions per round
- 🔀 Show each capital only once per round to keep alternatives varied
- 📈 Track the score as the player answers correctly
- 🛑 End the round after the first wrong answer
- 🏆 Persist each player's best result in PostgreSQL and display the Top 10
- 👤 Keep one best result per player, ignoring capitalization in player names
- 📋 Provide a menu to play, view the ranking, log out, or exit
- 🗑️ Allow the logged-in player to delete their own ranking entry after confirmation
- 🧪 JUnit tests for the API, parser, service, session, and terminal UI

## 🚀 Run locally

**Requirements:** Java 25, Maven, PostgreSQL (tested with 18), and a REST Countries API key.

### Create the database

Connect as a PostgreSQL administrator, then create a dedicated login and database:

```sql
CREATE ROLE geoquest LOGIN;
\password geoquest
CREATE DATABASE geoquest OWNER geoquest;
```

`\password` is a psql command that prompts for the password without embedding it in SQL history.
Apply the initial schema once from the project directory, connecting as the new login:

```bash
psql -h localhost -U geoquest -d geoquest --single-transaction -v ON_ERROR_STOP=1 -f src/main/resources/db/migration/V1__create_ranking_entries.sql
```

On Windows, if psql is not on PATH, use the executable under your PostgreSQL installation,
for example `C:\Program Files\PostgreSQL\18\bin\psql.exe`.
The application does not create databases or run DDL at startup.
If you already created the database in IntelliJ, skip database creation and apply the schema there.
Set DATABASE_URL to the actual database name, which may differ from the IntelliJ connection label.

### Configure the API key

The application reads `API_KEY`, `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD`
from environment variables. Set your chosen database password locally; never commit it.
`DATABASE_URL` uses JDBC syntax. Keep credentials in their separate variables rather than in the URL.

**PowerShell**

```powershell
$env:API_KEY="your-api-key"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/geoquest"
$env:DATABASE_USER="geoquest"
$env:DATABASE_PASSWORD="your-database-password"
mvn compile exec:java -Dexec.mainClass=com.raibrs.geoquest.App
```

**Bash**

```bash
export API_KEY="your-api-key"
export DATABASE_URL="jdbc:postgresql://localhost:5432/geoquest"
export DATABASE_USER="geoquest"
export DATABASE_PASSWORD="your-database-password"
mvn compile exec:java -Dexec.mainClass=com.raibrs.geoquest.App
```

## 🔑 API key

The API key is required by REST Countries v5. Never commit it to Git or place it directly in Java files.

On the first round, the client loads all catalog pages, requesting only country names and capitals
in batches of 80 records. After the complete catalog is loaded, it is reused in memory until the
application exits. Each round creates a new shuffled set of questions.

If a page fails to load, the incomplete catalog is discarded. The application returns to the menu;
choosing Play again starts a fresh attempt. There are no automatic retries.

## 🏆 PostgreSQL ranking

After choosing a player name, the application keeps that identity until the player selects
**Logout**. Each completed round updates the `ranking_entries` table through JDBC.

- The ranking displays the Top 10 results.
- A player has only one entry; a later better score replaces the previous one.
- The database retains players outside the displayed Top 10.
- Tied scores are ordered by the most recent round.
- A tied result from the same player updates the saved timestamp.
- If both score and timestamp match, player identity provides a deterministic display order.
- An atomic `INSERT ... ON CONFLICT` protects each player's best result from concurrent writes.
- Constraints reject missing names, invalid scores, question counts, and timestamps.
- Timestamps are stored as epoch milliseconds, matching the Java model.
- Names are normalized in Java using the existing case-insensitive identity rule. The normalized
  `player_key` is a primary key; display names retain their capitalization and accents.
- JDBC connections, statements, and result sets are closed after each operation.
- Connection and socket timeouts bound database failures; they return to the menu.

## 🧪 Tests

Tests use JUnit 6 and are organized by package under `src/test/java`.
HTTP tests use a local server with a fake key; no real REST Countries requests are made.
Application and service tests use an in-memory repository.
These unit tests do not need PostgreSQL or production credentials:

```bash
mvn test
```

Integration tests exercise the actual PostgreSQL repository, constraints, Unicode identity,
concurrent results, and preservation of saved results after failed updates. They create and remove only their own randomly named schema.
Use a separate test database and a login allowed to create schemas:

```powershell
$env:TEST_DATABASE_URL="jdbc:postgresql://localhost:5432/geoquest_test"
$env:TEST_DATABASE_USER="geoquest_test"
$env:TEST_DATABASE_PASSWORD="your-test-password"
mvn -Ppostgres-integration test
```

The integration profile fails clearly if test connection settings are missing.
On Windows, the following helper starts a separate temporary PostgreSQL instance bound to loopback
on a free port, runs the full integration profile, and stops that instance in `finally`:

```powershell
.\scripts\test-postgres.ps1
# Other installation path:
.\scripts\test-postgres.ps1 -PostgresBin "C:\Program Files\PostgreSQL\18\bin"
```

It uses only dummy test credentials and trust authentication for this disposable instance.
It does not touch the installed PostgreSQL service or existing databases. Data and diagnostic logs
are left under `target/postgres-test-*`, which is ignored by Git.

## 🎮 How it works

```text
Paginated API requests (first round; cached after complete loading)
    ↓
JSON parser
    ↓
Country list
    ↓
QuestionService shuffles and creates ten questions
    ↓
ConsoleUi reads the answer
    ↓
GameSession updates the score
    ↓
RankingService keeps the player's best result
    ↓
PostgresRankingRepository atomically saves the result and queries the Top 10
    ↓
ConsoleUi displays the updated ranking or menu
```

## 📁 Project structure

```text
src/
├── main/
│   └── java/
│       └── com/raibrs/geoquest/
│           ├── App.java                    # Application flow and dependency wiring
│           ├── api/
│           │   ├── CountriesClient.java    # HTTP client for REST Countries
│           │   └── CountryJsonParser.java  # JSON response parsing
│           ├── model/
│           │   ├── Country.java             # Country and capital data
│           │   ├── GameSession.java         # Current question and score
│           │   ├── Question.java            # Question and answer rule
│           │   └── RankingEntry.java        # One persisted player result
│           ├── config/
│           │   └── DatabaseConfig.java      # PostgreSQL environment settings
│           ├── repository/
│           │   ├── RankingRepository.java   # Persistence contract
│           │   └── PostgresRankingRepository.java # JDBC implementation
│           ├── service/
│           │   ├── QuestionService.java     # Question creation and shuffling
│           │   └── RankingService.java      # Ranking rules and ordering
│           └── ui/
│               └── ConsoleUi.java           # Terminal input and output
└── test/
    └── java/
        └── com/raibrs/geoquest/            # Unit tests by package
```

## ⚠️ Current limitations

- Only capital questions are available.
- A wrong answer ends the current round.
- Loading the country catalog requires a valid key and network access.
- The in-memory country catalog is refreshed only when the application restarts.
- A player name is remembered only while the application is running.
- Ranking operations require an available PostgreSQL database and the initialized schema.
- Player names identify local players; this is not user authentication for a public website.

Recoverable API and ranking-storage failures return to the menu. Closed input or an interrupted
request ends the current application flow. Technical error details are written through Java's
`System.Logger`; request headers and API keys are not logged by the application.

## 🧭 Next steps

- Add an HTTP backend before connecting a browser front-end; keep database credentials server-side.
- Add more question types beyond capital-city questions.
- Add Portuguese translations with `ResourceBundle`.

---

Built with ☕ Java, real API data, and a little curiosity.
