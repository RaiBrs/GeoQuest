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

- 🌍 Fetch countries from the REST Countries API
- 🏛️ Ask capital-city questions using real API data
- 🎯 Five questions per round
- 📈 Track the score as the player answers correctly
- 🛑 End the round after the first wrong answer
- 🏆 Persist a local Top 10 ranking in `data/ranking.json`
- 👤 Keep one best result per player, ignoring capitalization in player names
- 📋 Provide a menu to play, view the ranking, log out, or exit
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

The client makes one request per round and requests only the country name and capital fields.

## 🏆 Local ranking

After choosing a player name, the application keeps that identity until the player selects
**Logout**. Each completed round updates the local ranking at `data/ranking.json`.

- The ranking displays the Top 10 results.
- A player has only one entry; a later better score replaces the previous one.
- Tied scores are ordered by the most recent round.
- `data/` is ignored by Git because it contains local game data.

## 🧪 Tests

Tests use JUnit 6 and are organized by package under `src/test/java`.

```bash
mvn test
```

## 🎮 How it works

```text
API request
    ↓
JSON parser
    ↓
Country list
    ↓
QuestionService shuffles and creates five questions
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
- The API currently requires a valid key and network access.
- A player name is remembered only while the application is running.

## 🧭 Next steps

- Add more question types and multiple-choice questions.
- Add Portuguese translations with `ResourceBundle`.
- Allow a logged-in player to delete their ranking entry.

---

Built with ☕ Java, real API data, and a little curiosity.
