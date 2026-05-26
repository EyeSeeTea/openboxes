## Context

OpenBoxes serves two kinds of pages from one Grails app:
- **React pages** — a `react` GSP layout shell whose Webpack bundle CSS is loaded in `<body>`
  (`grails-app/views/common/react.gsp`). Color system lives in `src/css/colors.scss`, which
  already defines a `:root { --blue-primary: …; --color-red: … }` block; newer components consume
  `var(--blue-primary)`.
- **GSP pages** — the `main` layout (`grails-app/views/layouts/main.gsp`) loading asset-pipeline
  CSS (`grails-app/assets/stylesheets/*.css`), much of it hardcoded hex.

A Claude-designed theme package now exists (`THEME.md` + `theme.css`, the "UNICEF Tajikistan
Navy" theme inspired by moh.tj). `theme.css` is the canonical artifact: a `:root` block of `--ob-*`
custom properties plus an `.ob-*` utility-class layer. `THEME.md` is the written source of truth —
reskin, not redesign; override only `--ob-primary*` and `--ob-accent` to produce palette variants.

There is no single brand source in the live app today. We want `theme.css` to be that source for
both stacks, without modifying upstream stylesheets and without disturbing existing OB layouts.

## Goals / Non-Goals

**Goals:**
- Make `theme.css` the single in-code source of truth for colors + fonts, edited per customer branch.
- Recolor **both** React and GSP pages from it.
- Zero edits to upstream stylesheets (`colors.scss`, `main.scss`, `grails.css`, component `.scss`).
- Custom code isolated under `custom/` paths; only surgical, documented upstream touches.
- Reskin only — no change to DOM order, element positions, or copy on existing OB pages.

**Non-Goals:**
- Migrating the ~30% hardcoded-hex values in upstream CSS to tokens.
- Runtime admin UI, DB-backed theme, or per-location theming.
- External web fonts; new layout/IA; brand imagery beyond where the logo already lives.

## Decisions

**1. `theme.css` is the token + utility source; no Groovy token map, no token-generating taglib.**
The dropped-in `theme.css` owns the `:root` `--ob-*` tokens and the `.ob-*` utilities. Retheming is
editing its `--ob-primary*` / `--ob-accent` values — nothing is generated from code.
- *Supersedes the earlier plan* of a `ThemeTokens.groovy` map + an `obtheme` taglib: redundant now
  that a canonical CSS file exists.

**2. Deliver via the Grails asset pipeline, injected into both layout heads.**
Place the theme CSS under `grails-app/assets/stylesheets/custom/` and inject a single
`<asset:stylesheet>` line into the `<head>` of `main.gsp` (GSP pages) and `react.gsp` (React pages).
Both are GSP layouts, so the asset link reaches both stacks; React needs no webpack import (the
asset is served same-origin and loads on React pages too).
- *Alternative — webpack `@import` of theme.css into the bundle:* rejected; would only reach React
  and duplicate delivery.

**3. A small "bridge" file recolors existing OB chrome; uses a specificity bump so it always wins.**
`theme.css` only defines `--ob-*`; existing OB components read `--blue-primary`, `--color-red`, etc.
A companion `themeBridge.css` maps those to the new tokens and applies the base font:
```css
:root:root {            /* 0,2,0 beats the bundle's :root (0,1,0) regardless of load order */
  --blue-primary: var(--ob-primary);
  --blue-500:     var(--ob-primary);
  --blue-700:     var(--ob-primary-dark);
  --color-red:    var(--ob-red);
  --color-green:  var(--ob-green);
  --color-yellow: var(--ob-amber);
}
html body { font-family: var(--ob-font); }   /* beats upstream `body {…}` without !important */
```
The React bundle's `:root` defaults load in `<body>` after the head, so the bump is what makes the
override win — without editing `colors.scss`/`main.scss`/`grails.css` or using `!important`.

**4. Do NOT blanket-apply `theme.css`'s global reset/body rules to existing pages.**
`theme.css` was authored for standalone mocks and includes `*,*::before,*::after{box-sizing}` and a
full `body{…}` restyle. Bootstrap 4.6 already sets `box-sizing: border-box`, so that is a no-op; the
`body` background/color/size could shift existing pages. To honor "reskin, not redesign," the live
wiring loads the **tokens** (`:root`), the **`.ob-*` utilities** (new namespace, inert on existing
markup), and the **bridge** (recolors via OB's own vars). Any opinionated global `body`/reset rules
from the mock are scoped or omitted at apply time, verified against real OB pages.

**5. Curated self-hosted fonts: Inter + JetBrains Mono.**
`theme.css` references `"Inter"` (UI) and `"JetBrains Mono"` (IDs/lots). `@font-face` for both lives
in `grails-app/assets/stylesheets/custom/themeFonts.css` with woff2 under
`grails-app/assets/fonts/custom/theme/`, served same-origin so both stacks use them. Inter is also
bundled on React via `@fontsource/inter` today; self-hosting unifies it for GSP without an external
request.

**Bundling:** an asset manifest `grails-app/assets/stylesheets/custom/obTheme.css` uses
`//= require` to pull in `themeFonts`, `theme`, and `themeBridge`, so each layout head needs only one
`<asset:stylesheet src="custom/obTheme.css"/>` line.

## Risks / Trade-offs

- **Mock `theme.css` global rules disturb existing layouts** → Load tokens + utilities + bridge;
  scope/omit the mock's `body`/reset; verify real OB pages render unchanged in structure (Decision 4).
- **Specificity-bump fragility** → If upstream ever sets a brand var at higher specificity than
  `:root:root`, the bridge could lose. Low risk (vars live at `:root`); documented in `themeBridge.css`.
- **GSP hardcoded-hex unaffected** → Pages using literal hex won't recolor. Accepted per scope.
- **Upstream layout-head edits are merge points** → One `<asset:stylesheet>` line each in
  `main.gsp`/`react.gsp`; listed below for the merge hitlist.

## Migration Plan

Additive; no DB, no data migration. Deploy = ship the new custom asset files + the two layout
one-liners. Rollback = remove the two `<asset:stylesheet>` lines (pages fall back to upstream
`colors.scss` defaults) and delete the custom assets. No persisted state to unwind.

## Upstream touch points

| File | Edit | Reason |
|---|---|---|
| `grails-app/views/layouts/main.gsp` | add one `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>` (after `application.css`) | load theme on all GSP pages |
| `grails-app/views/layouts/react.gsp` | add one `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>` | load theme on all React pages |

Optional (only if needed): same line in `bootstrap.gsp`, `mobile.gsp`, `print.gsp`, `email.gsp`.
No other upstream files are modified. `THEME.md` and `theme.css` move from this change folder to
their runtime homes (`theme.css` → `grails-app/assets/stylesheets/custom/`; `THEME.md` → repo root
or `.claude/docs/`, referenced from `CLAUDE.md` UI conventions).

## Deploy status

- Implemented on: `release/est/tjk/0.9.7` (pending).
- Replayed onto other customer branches: none yet.
- Submitted upstream: no.

## Open Questions

- Final home for `THEME.md` (repo root vs `.claude/docs/`) and how it's referenced from `CLAUDE.md`.
- Which of `theme.css`'s global `body`/reset rules (if any) are safe to apply app-wide vs must be
  scoped to `.ob-*` containers — resolved by visual diff against real OB pages during apply.
- Whether to promote the mechanism to the EST shared layer with a neutral default theme, leaving
  only token values per customer branch.
