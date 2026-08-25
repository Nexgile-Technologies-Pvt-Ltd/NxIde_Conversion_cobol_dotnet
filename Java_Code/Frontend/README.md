# CardDemo frontend

Angular 20 application using standalone components, signals and lazy-loaded routes. Each screen is
the web form of one CICS/BMS map and keeps that map's transaction id, program name and function-key
bar so it stays recognisable to someone who used the 3270 application.

See [`../README.md`](../README.md) for the full picture and
[`../CONVERSION-MAP.md`](../CONVERSION-MAP.md) for the program-by-program mapping.

## Run

```powershell
npm install
npm start                    # http://localhost:4200
npm run build:prod           # dist/carddemo-frontend
```

The backend must be running on `http://localhost:8080`. Change
`src/environments/environment.ts` if it listens elsewhere; the production build swaps in
`environment.prod.ts`, which uses a same-origin `/api` base.

## Layout

| Path | Contents |
|---|---|
| `src/app/core` | `AuthService` (session signals), `ApiService` (every REST call), JWT interceptor, route guards, transport models |
| `src/app/layout` | `ShellComponent` — the navigation bar and routed outlet |
| `src/app/shared` | Screen header, message line, amount pipe |
| `src/app/features/auth` | Sign on, sign up, change password |
| `src/app/features/menu` | Main and administrator menus |
| `src/app/features/dashboard` | Portfolio overview |
| `src/app/features/accounts` | Account view and update |
| `src/app/features/cards` | Card list, view and update |
| `src/app/features/transactions` | Transaction list, view, add and bill payment |
| `src/app/features/reports` | Report requests and statements |
| `src/app/features/reference` | Reference data browser |
| `src/app/features/admin` | User administration, transaction types, batch console, audit |

## Conventions

- **Screen header.** `<cd-screen-header>` renders the transaction id, program name, title and the
  COBOL source the screen came from.
- **Message line.** `<cd-message>` is the single place a screen shows feedback, matching the BMS
  `ERRMSG` field. Backend messages are shown verbatim so the original wording survives.
- **Field highlighting.** The backend returns the offending `field` with each validation failure and
  the screen adds `cd-invalid` to that input, which is the web form of the legacy cursor placement.
- **Function keys.** The `cd-pfkeys` bar labels each action with the key the BMS footer advertised —
  F3 return, F4 clear, F5 save or copy, F7/F8 paging, F12 cancel.
- **Styling.** One design system in `src/styles.scss`; components carry no private stylesheets.

## Authorisation

`authGuard` protects every screen, `adminGuard` protects the administrator area and
`anonymousGuard` keeps a signed-on user off the sign-on and sign-up pages. These guards only avoid
rendering a screen that would fail: the backend enforces the same rules on every endpoint, so route
guards are never the only control.
