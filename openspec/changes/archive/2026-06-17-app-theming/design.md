## Context

OpenBoxes serves two kinds of pages from one Grails app:
- **React pages** — the `react` GSP layout (`grails-app/views/common/react.gsp`) whose Webpack
  bundle CSS loads in `<body>`. The color system lives in `src/css/colors.scss`, which defines a
  `:root { --blue-primary: …; --color-red: … }` block that newer components consume.
- **GSP pages** — the `custom` and `main` layouts loading asset-pipeline CSS plus
  `web-app/css/openboxes.css`, much of it hardcoded hex.

There is no single brand source in the live app. This change makes one file —
`grails-app/assets/stylesheets/custom/obTheme.css` — that source for both stacks, without modifying
upstream stylesheets or components and without disturbing existing OB layouts. The palette is the
"UNICEF Tajikistan Navy" look (moh.tj-inspired): one brand color (`--ob-primary`) plus neutrals.

## Goals / Non-Goals

**Goals:**
- One in-code file is the source of truth for colors + fonts, edited per customer branch.
- Recolor **both** React and GSP pages from it.
- Zero edits to upstream stylesheets or React/Groovy components.
- Custom code isolated under `custom/`; only surgical, documented upstream touches (layout-head links).
- Reskin only — no change to DOM order, element positions, or copy.

**Non-Goals:**
- Migrating the hardcoded-hex values in upstream CSS to tokens.
- Runtime admin UI, DB-backed theme, or per-location theming.
- Self-hosted/external web fonts; new layout/IA; brand imagery beyond the existing logo.

## Decisions

**1. One file, four parts.** `grails-app/assets/stylesheets/custom/obTheme.css` contains, in order:
(1) `:root` `--ob-*` tokens + `.ob-*` utility classes (a toolkit for net-new themed markup — the live
app does not use them); (2) the `:root:root` bridge; (3) navy header + sidebar + dropdown overrides;
(4) Groovy selector + pagination overrides. Retheme = edit `--ob-primary*`. There is no `--ob-accent`
(one brand color + neutrals) and no generated-from-code token map. The design intent and the
"retheme-vs-leave-alone" rules live in the file's top comment — there is no separate `THEME.md`.

**2. Deliver via the Grails asset pipeline, injected into the layout heads.** One
`<asset:stylesheet src="custom/obTheme.css"/>` line in the `<head>` of `custom.gsp` (most GSP pages),
`react.gsp` (React pages), and `main.gsp`. All are GSP layouts, so the asset reaches both stacks;
React needs no webpack import (served same-origin).

**3. A `:root:root` bridge recolors existing OB chrome via its own vars.** The tokens only define
`--ob-*`; existing components read `--blue-primary`, `--color-red`, etc. The bridge maps them and
applies the base font:
```css
:root:root {            /* 0,2,0 beats the bundle's :root (0,1,0) regardless of load order */
  --blue-primary: var(--ob-primary);
  --blue-700:     var(--ob-primary-dark);
  --color-red:    var(--ob-red);
  --color-green:  var(--ob-green);
  --color-yellow: var(--ob-amber);
  --page-background: var(--ob-bg);
}
html body { font-family: var(--ob-font); }
```
The React bundle's `:root` defaults load in `<body>` after the head, so the bump is what makes the
override win — without editing `colors.scss`/`main.scss` or using `!important`.

**4. Chrome that doesn't read those vars is recolored by class override — still no component edits.**
The header, dashboard sidebar (`.configs-left-nav`), nav/settings dropdowns (`.dropdown-menu-content`
/ `.subsection-section-item`), Choose-Location modal, and Groovy Chosen/Select2 selectors are restyled
by targeting their existing classes from `obTheme.css`. React rules are anchored on
`.navbar.main-wrapper` and GSP rules on `#main-wrapper`: the extra class out-specifies
`HeaderStyles.scss` (which nests its rules under `.main-wrapper {}` and loads after this head sheet),
so `!important` + the anchor are required there. No edits to `Header.jsx`, `HeaderStyles.scss`,
`Dashboard.scss`, `LocationChooserModal.scss`, or the GSP/megamenu markup.

**5. Do NOT apply the design mock's global reset/body rules.** The upstream design mock set
`*,*::before,*::after{box-sizing}`, a body `margin/padding` reset, and base `font-size`/`line-height`.
Only `box-sizing` (a Bootstrap no-op), body color/bg/font, and the link retheme are kept; the
margin/padding reset and base size/line-height are omitted so existing OB pages don't reflow.

**6. Fonts: family only, no self-hosting.** The theme sets `--ob-font` (Inter UI) / `--ob-font-mono`
(JetBrains Mono for IDs) via the bridge, relying on the system-font fallback chain plus React's
existing `@fontsource/inter`. No `@font-face` / woff2 is added.

## Risks / Trade-offs

- **Specificity-bump / anchor fragility** → if upstream sets a brand var at higher specificity than
  `:root:root`, or restructures `HeaderStyles.scss`, the relevant override could lose. Low risk; the
  rationale is documented in the `obTheme.css` comments.
- **GSP hardcoded-hex unaffected** → pages using literal hex won't recolor. Accepted per scope.
- **Layout-head + class-override edits are merge points** → the three `<asset:stylesheet>` lines are
  the only upstream touches; the class-override selectors depend on upstream class names staying
  stable, so re-verify after an upstream UI bump.

## Migration Plan

Additive; no DB, no data migration. Deploy = ship `obTheme.css` + the three layout one-liners.
Rollback = remove the `<asset:stylesheet>` lines (pages fall back to upstream defaults) and delete the
file. No persisted state.

## Upstream touch points

| File | Edit | Reason |
|---|---|---|
| `grails-app/views/layouts/custom.gsp` | add one `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>` | load theme on most GSP pages (the `layout="custom"` pages) |
| `grails-app/views/layouts/react.gsp` | add one `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>` | load theme on all React pages |
| `grails-app/views/layouts/main.gsp` | add one `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>` (after `application.css`) | load theme on GSP pages using the `main` layout |
| `CLAUDE.md` | UI-conventions pointer to `obTheme.css` | docs |

Optional (only if needed): same line in `bootstrap.gsp`, `mobile.gsp`, `print.gsp`, `email.gsp`.
No upstream stylesheets or React/Groovy components are modified.

## Deploy status

- Implemented on: `feat/custom-app-theming` (current working branch).
- Replayed onto other customer branches: none yet.
- Submitted upstream: no.

## Open Questions

- Whether to promote the mechanism to the EST shared layer with a neutral default theme, leaving only
  `--ob-primary*` values per customer branch.
