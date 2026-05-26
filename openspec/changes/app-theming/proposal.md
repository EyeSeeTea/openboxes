## Why

OpenBoxes renders pages from two stacks — React (Webpack bundle) and Grails/GSP
(server-rendered) — each with its own styling delivery. There is no single place to set
brand colors and fonts, so rebranding a deployment (e.g. the Tajikistan MoH look on
`release/est/tjk/0.9.7`) means hunting through SCSS, asset-pipeline CSS, and hardcoded hex
values across both stacks. We want one in-code theme that recolors both stacks from a single
source of truth.

## What Changes

- Adopt the dropped-in **`theme.css`** (Claude-designed "UNICEF Tajikistan Navy" theme) as the
  single in-code source of truth: a `:root` block of `--ob-*` tokens plus an `.ob-*` utility layer.
  Each customer branch rebrands by overriding `--ob-primary*` and `--ob-accent` only. `THEME.md` is
  the written reskin-not-redesign convention.
- **Deliver via the Grails asset pipeline**, injected into the React and GSP layout heads with one
  `<asset:stylesheet>` line each — CSS variables are the one styling primitive both stacks share.
- Add a small **bridge** stylesheet that maps the existing brand-driving palette variables
  (`--blue-primary`, `--color-red`, …) to the new `--ob-*` tokens and applies the base font, so
  current chrome recolors immediately with **zero edits to upstream CSS** (a `:root:root`
  specificity bump makes the override win the cascade regardless of load order; no `!important`).
- Bundle a **curated, self-hosted font set** (Inter + JetBrains Mono; no external web-font requests).
- Reskin only — preserve existing DOM order, layout, and copy on OB pages.

Out of scope (deliberately): migrating the ~30% hardcoded-hex values scattered in upstream CSS,
runtime admin UI, DB-backed config, per-location theming, external fonts, layout/IA changes.

## Capabilities

### New Capabilities
- `app-theming`: A central, in-code theme (colors + fonts) expressed as CSS custom properties and
  injected into every page head, so both the React and Grails/GSP stacks render with one brand
  source of truth, without modifying upstream stylesheets.

### Modified Capabilities
<!-- None — no existing spec's requirements change. -->

## Impact

- **New custom code** (isolated, merge-safe), all under `grails-app/assets/.../custom/`:
  - `stylesheets/custom/theme.css` — canonical tokens + `.ob-*` utilities (the dropped-in file).
  - `stylesheets/custom/themeBridge.css` — maps OB vars → `--ob-*` (specificity bump) + base font.
  - `stylesheets/custom/themeFonts.css` + `fonts/custom/theme/` — self-hosted Inter + JetBrains Mono.
  - `stylesheets/custom/obTheme.css` — asset manifest requiring the three above.
  - `THEME.md` — reskin convention doc (repo root or `.claude/docs/`), referenced from `CLAUDE.md`.
- **Upstream touch points** (surgical, one line each — listed in design.md):
  - `grails-app/views/layouts/main.gsp` — add `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>`.
  - `grails-app/views/layouts/react.gsp` — add `<asset:stylesheet src="custom/obTheme.css"/>` in `<head>`.
- **No** Groovy/taglib, **no** DB/migration, **no** new runtime npm dependency, **no** changes to
  `colors.scss`/`main.scss`/`grails.css`.
