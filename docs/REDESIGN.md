# GeoQuest application shell

The redesign preserves HTML, CSS, ES modules, the existing game controller, and Spring Boot.
There are no new data endpoints, authentication mechanisms, dependencies, or game rules.

## Structure and navigation

- `styles/tokens.css` owns semantic dark colors, spacing-related frame dimensions, and radius.
- `styles/layout.css` owns header, sidebar/drawer, content frame, and responsive layout.
- `styles/components.css` owns shared controls, profile panels, and the ranking table.
- `styles/pages/hub.css` separates challenge cards from entry-screen styles.
- `view/shell-view.js` owns active navigation, drawer focus, Escape, outside click, and inert backgrounds.
- `view/pages/account-view.js` renders account and ranking data without HTTP calls.
- Controllers keep the original services and screen flow: login → Home → hub → game → result.

Ranking and account are additional views of the same document, separated from the challenge hub.
Home in the sidebar opens How to play after entry. The separate login screen has Google/GitHub
and Continue as a guest, with no name form or application sidebar. Guests are greeted as Guest /
Visitante; personal names come from providers only. Guest Logout and account sign-out return to
login. Restoring an authenticated session opens Home automatically.
Leaving a round stops its client timer, as before. Navigation is blocked during mutations; drawer
open/close controls remain available. Language remains fixed during game and result views.

The sidebar is visible by default from 1100 px and collapsible through its header button.
Below that width it starts closed, behaves as a modal drawer, traps Tab/Shift+Tab, restores focus
on Escape/close/overlay, and preserves the destination heading's focus after navigation.

## Cleanup and corrections

Replaced the previous blue/light color literals with semantic tokens. Removed obsolete card shadows,
screen-entry animations, promotional sizing, and overlapping mobile rules. Moved hub-only styles
out of welcome.css; shared buttons, forms, badges, and panels have one definition.
Removed unused translations for the retired promotional copy and previous ranking row format.

Extracted account DOM rendering from its controller and consolidated repeated round cleanup into
`leaveRound`. No duplicated business rule was rewritten. Ranking still displays the existing top-20
response, with score and submitted-answer count; names and server text use textContent.

Corrected stale ranking rows after filter changes. Added scroll margins so keyboard focus targets
remain below the fixed header. Drawer controls are exempt from the general pending-request lock,
preventing the new navigation from trapping users during slow requests. Countdown seconds use two
digits to keep the timer width stable. The reserved final-warning space is retained.

Only the static-file allowlist and its test changed in Java, to serve the three new CSS/JS files.
Spring Security, OAuth callbacks, cookies, CSRF, DELETE confirmation, session persistence, and
server-side answer/deadline validation remain unchanged.

## Future learning area

Learn and Explore are noninteractive entries with Coming soon labels. They do not load a screen
or issue requests. Existing tokens, frame, navigation items, and view separation can support real
learning screens later; no content model, account progress, router, or learning API was introduced.

## Validation

See [responsive checks](RESPONSIVE_QA.md). Frontend tests cover safe text rendering, keyboard flow,
retries, scoring requests with CSRF, error review, navigation, modal drawer behavior, translations,
unique IDs, and ranking invalidation. Java tests retain HTTP, game, OAuth/security, and database
coverage. Real provider consent and physical mobile-device testing require a separate manual check.

## Files involved

| Group | Modified files |
| :--- | :--- |
| Documents and markup | `README.md`, `frontend/index.html`, `frontend/privacy.html` |
| Controllers | `frontend/src/controller/game-controller.js`, `frontend/src/controller/account-controller.js` |
| Views and language | `frontend/src/view/shell-view.js`, `frontend/src/view/pages/hub-view.js`, `frontend/src/view/pages/game-view.js`, `frontend/src/i18n.js` |
| Shared styles | `frontend/styles.css`, `frontend/styles/base.css`, `frontend/styles/layout.css`, `frontend/styles/components.css` |
| Screen styles | `frontend/styles/pages/welcome.css`, `frontend/styles/pages/game.css`, `frontend/styles/pages/result.css` |
| Existing frontend tests | `frontend/tests/app.test.js`, `frontend/tests/account-controller.test.js` |
| Static delivery | `src/main/java/com/raibrs/geoquest/http/StaticFileHandler.java`, `src/test/java/com/raibrs/geoquest/http/StaticFileHandlerTest.java` |

New files: `frontend/styles/tokens.css`, `frontend/styles/pages/hub.css`,
`frontend/src/view/pages/account-view.js`, `frontend/tests/shell-view.test.js`,
`frontend/tests/i18n.test.js`, this document, `docs/RESPONSIVE_QA.md`, and the two
screenshots under `docs/assets/redesign/`. Responsive changes from the preceding work are retained.

Test results: **12 frontend tests passed; 130 Java tests passed**, including the five PostgreSQL
integration tests run separately against a disposable local database. The first sandboxed Java run
could not open loopback sockets; the same suite passed with local networking available.
