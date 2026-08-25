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
| `src/app/layout` | `ShellComponent` (sidebar plus routed outlet), `NavbarComponent`, and the shared navigation model |
| `src/app/shared` | Screen header, message line, amount pipe, icon set |
| `src/app/features/auth` | Sign on, sign up, change password, and the shared brand panel |
| `src/app/features/menu` | Main and administrator menus |
| `src/app/features/dashboard` | Portfolio overview |
| `src/app/features/accounts` | Account view and update |
| `src/app/features/cards` | Card list, view and update |
| `src/app/features/transactions` | Transaction list, view, add and bill payment |
| `src/app/features/reports` | Report requests and statements |
| `src/app/features/reference` | Reference data browser |
| `src/app/features/admin` | User administration, transaction types, batch console, audit |

## Layout and navigation

The shell pairs a sidebar with a navbar, which carry different things rather than repeating each
other.

**Sidebar — where you can go.** Every destination, grouped the way the two legacy menus grouped
them: Overview, Servicing and Reporting for the eleven `COMEN02Y` functions, and an Administration
group carrying the `COADM02Y` functions plus the batch console. Each entry shows the CICS
transaction it replaces. Below 900px it becomes an off-canvas drawer opened from the navbar.

**Navbar — where you are, and who you are.** A breadcrumb naming the current section, screen and
transaction; a quick lookup that jumps straight to an account view; and the identity menu carrying
change-password and sign-off. It also owns the drawer button on a narrow viewport.

Both the sidebar groups and the breadcrumb table live in `layout/navigation.ts`, so a new screen is
registered once. `resolveBreadcrumb` matches by longest route prefix, so a route carrying a record
key such as `/cards/view/0500024453765740` still resolves to its screen.

The Administration group is hidden for a regular user, but that is presentation only — the backend
enforces the role on every administrator endpoint.

## Conventions

- **Screen header.** `<cd-screen-header>` renders the transaction id, program name, title and the
  COBOL source the screen came from.
- **Icons.** `<cd-icon name="...">` draws from one set in `shared/icon.ts`; icons inherit the
  surrounding text colour.
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
