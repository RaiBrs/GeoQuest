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
- 🏆 Persist each player's best result in `data/ranking.json` and display the Top 10
- 👤 Keep one best result per player, ignoring capitalization in player names
- 📋 Provide a menu to play, view the ranking, log out, or exit
- 🗑️ Allow the logged-in player to delete their own ranking entry after confirmation
- 🧪 JUnit tests for the API, parser, service, session, and terminal UI

## 🚀 Run locally

**Requirements:** Java 25, Maven, and a REST Countries API key.

### Configure the API key

The application reads the key from the `API_KEY` environment variable. It is never stored in the source code.

**PowerShell**

```powershell
$env:API_KEY="your-api-key"
mvn compile exec:java -Dexec.mainClass=com.raibrs.geoquest.App
```

**Bash**

```bash
export API_KEY="your-api-key"
mvn compile exec:java -Dexec.mainClass=com.raibrs.geoquest.App
```

## 🔑 API key

The API key is required by REST Countries v5. Never commit it to Git or place it directly in Java files.

On the first round, the client loads all catalog pages, requesting only country names and capitals
in batches of 80 records. After the complete catalog is loaded, it is reused in memory until the
application exits. Each round creates a new shuffled set of questions.

If a page fails to load, the incomplete catalog is discarded. The application returns to the menu;
choosing Play again starts a fresh attempt. There are no automatic retries.

## 🏆 Local ranking

After choosing a player name, the application keeps that identity until the player selects
**Logout**. Each completed round updates the local ranking at `data/ranking.json`.
This path is relative to the working directory used to launch the application.

- The ranking displays the Top 10 results.
- A player has only one entry; a later better score replaces the previous one.
- The file retains players outside the displayed Top 10.
- Tied scores are ordered by the most recent round.
- A tied result from the same player updates the saved timestamp.
- Writes prepare a temporary file before requesting atomic replacement. If the filesystem
  cannot perform the replacement, saving fails instead of falling back to a non-atomic write.
- Invalid ranking files are reported and preserved for diagnosis.
- `data/` is ignored by Git because it contains local game data.

## 🧪 Tests

Tests use JUnit 6 and are organized by package under `src/test/java`.
HTTP tests use a local server with a fake key; no real REST Countries requests are made.
Persistence tests use temporary directories, and application-flow tests use scripted input.

```bash
mvn test
```

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
│           ├── repository/
│           │   └── RankingRepository.java   # Ranking JSON file access
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

Recoverable API and ranking-storage failures return to the menu. Closed input or an interrupted
request ends the current application flow. Technical error details are written through Java's
`System.Logger`; request headers and API keys are not logged by the application.

## 🧭 Next steps

- Migrate ranking persistence from JSON files to PostgreSQL.
- Add more question types beyond capital-city questions.
- Add Portuguese translations with `ResourceBundle`.

---

Built with ☕ Java, real API data, and a little curiosity.
