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

- [Features](#features)
- [Run locally](#run-locally)
- [API key](#api-key)
- [Tests](#tests)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Current limitations](#current-limitations)
- [Next steps](#next-steps)

</details>

## ✨ Features

- 🌍 Fetch countries from the REST Countries API
- 🏛️ Ask capital-city questions using real API data
- 🎯 Five questions per round
- 📈 Track the score as the player answers correctly
- 🛑 End the round after the first wrong answer
- 🔁 Ask whether the player wants to try again
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
│           │   └── Question.java            # Question and answer rule
│           ├── service/
│           │   └── QuestionService.java     # Question creation and shuffling
│           └── ui/
│               └── ConsoleUi.java           # Terminal input and output
└── test/
    └── java/
        └── com/raibrs/geoquest/            # Unit tests by package
```

## ⚠️ Current limitations

- Only capital questions are available.
- A wrong answer ends the current round.
- The score is kept only during the current session.
- The API currently requires a valid key and network access.
- The application does not yet have persistent ranking storage.

## 🧭 Next steps

- Validate domain objects more strictly.
- Add timeout and stronger error handling to the HTTP client.
- Expand parser tests for malformed API responses.
- Add more question types and multiple-choice questions.
- Add a ranking feature with local storage.
- Add Portuguese translations with `ResourceBundle`.

---

Built with ☕ Java, real API data, and a little curiosity.
