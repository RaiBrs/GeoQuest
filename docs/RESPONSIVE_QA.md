# Responsive browser verification

Checked on 2026-10-28 with the dark application shell, using desktop-browser viewport overrides.

| Viewport (CSS px) | Layout checked |
| :--- | :--- |
| 1440 × 900 | Desktop with visible sidebar |
| 1024 × 768 | Tablet landscape, drawer closed by default |
| 768 × 1024 | Tablet portrait, drawer closed by default |
| 480 × 900 | Wide phone |
| 375 × 812 | Phone portrait |
| 320 × 568 | Small phone, additional entry/game check |
| 667 × 375 | Phone landscape, additional game check |

Entry, hub, ranking with long names, game, and expanded mistake review were checked at the five
requested widths (1440, 1024, 768, 480, 375). They had no horizontal overflow. Visible main controls
(buttons, inputs, selects, and disclosure summaries) measured at least 44 × 44 px. Narrow screens
stack mode cards and alternatives; landscape phones retain two answer columns when space permits.

Functional browser checks:

- Visitor entry, complete ten-question country round in Portuguese, feedback, results, mistake review,
  returning to the hub, and a capital-question Survival round in English.
- Keyboard A to answer, Space to continue/open the result, and R to restart a finished round.
- Timed mode and automatic server-confirmed expiry; language stays disabled during the round/result.
- Ranking filters, long unbroken names, literal `<script>` text, and clearing old rows on filter change.
  Ranking fixtures existed only in the disposable local database and were removed after verification.
- Drawer opening, Escape, outside click, Tab/Shift+Tab focus containment/restoration, background
  inertness, and remaining closable during a deliberately slow local request.
- Guest account area and returning to the entry view. Authenticated profile, logout, DELETE confirmation,
  and mutation blocking have automated frontend and Java coverage.

At 375 px the alternatives kept the same vertical position when the final warning appeared;
the counter displayed two-digit seconds. Expiry also completed while the drawer was open,
without stealing focus from its close button.

The local preview uses placeholder OAuth client registrations. Both provider entry routes and the
existing OAuth/security tests were checked; real Google/GitHub consent was not repeated. No production
credentials, database records, or Render configuration were changed for this redesign.

Dark tokens keep semantic feedback colors. Normal text/primary button contrast was checked, visible
focus remains available, and the global prefers-reduced-motion rule is retained. Device-native keyboard,
zoom, cutouts, touch focus, and OAuth return navigation still need checking on physical iOS/iPadOS and
Android devices; viewport overrides do not reproduce all of those behaviors.

## Interaction polish

The welcome view with its globe and three-step guide was checked at 320, 375, 480, 768, 1024,
and 1440 px without horizontal overflow. Hovering each mode card highlighted only its own button;
the other two remained gray. Keyboard focus uses the same blue emphasis. Correct/wrong answer
effects were checked after real local API responses, including reset on the next question and the
Space shortcut. The existing global reduced-motion override covers all added animations.
Computed font families matched for headings, language/question selects, and the header user label.
The former entry card has been replaced by the separate login screen; profile initials remain unchanged.

## Separate entry and Home

Login and the How to play Home view were checked at 320, 375, 480, 768, 1024, and 1440 px without
horizontal overflow. The sidebar stayed hidden on login at every width. Continue as a guest entered
Home without an input field or a name request. A local visitor round used Visitante, accepted an
answer, continued with Space, and returned to login on Logout. Browser console contained no errors.
Frontend tests also cover restored provider identity, guest-name translation, re-entry, and drawer
behavior across login and application views. Actual provider consent was not repeated.
