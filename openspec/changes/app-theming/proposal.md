## Why

OpenBoxes renders pages from two stacks — React (Webpack bundle) and Grails/GSP
(server-rendered) — each with its own styling delivery. There is no single place to set brand
colors and fonts, so rebranding a deployment (e.g. the Tajikistan MoH "navy" look) means hunting
through SCSS, asset-pipeline CSS, and hardcoded hex across both stacks. One in-code theme recolors
both stacks from a single source of truth.

## What Changes

- A single in-code file, **`grails-app/assets/stylesheets/custom/obTheme.css`**, is the source of
  truth: a `:root` block of `--ob-*` tokens, an `.ob-*` utility layer for net-new themed markup, a
  `:root:root` bridge mapping OpenBoxes' own brand vars onto the tokens, and live overrides that
  recolor the existing header, dashboard sidebar, nav/settings dropdowns, Choose-Location modal, and
  Groovy selectors. Each customer branch rebrands by overriding `--ob-primary*` only (one brand
  color + neutrals; no secondary accent).
- **Delivered via the Grails asset pipeline**, injected with one `<asset:stylesheet>` line into the
  `<head>` of the three layouts that serve real pages — `custom.gsp` (most GSP pages), `react.gsp`
  (React pages), and `main.gsp`. CSS custom properties are the one styling primitive both stacks share.
- The **bridge** maps `--blue-primary` / `--color-red` / … onto `--ob-*`, so chrome that already
  reads those vars recolors with zero edits to upstream CSS. A `:root:root` specificity bump wins the
  cascade regardless of load order; targeted `!important` + `.navbar.main-wrapper` anchoring is used
  only where component CSS (e.g. `HeaderStyles.scss`) must be out-specified.
- The theme font family (Inter UI / JetBrains Mono for IDs) is set via the bridge using the
  system-font fallback chain (plus React's existing `@fontsource/inter`); no fonts are self-hosted.
- Reskin only — existing DOM order, layout, and copy on OB pages are preserved.

Out of scope: migrating the hardcoded-hex values scattered in upstream CSS, runtime admin UI,
DB-backed config, per-location theming, self-hosted/external fonts, and layout/IA changes.

## Capabilities

### New Capabilities
- `app-theming`: a central, in-code theme (colors + fonts) expressed as CSS custom properties and
  injected into every page head, so both the React and Grails/GSP stacks render from one brand source
  of truth without modifying upstream stylesheets or components.

### Modified Capabilities
<!-- None — no existing spec's requirements change. -->

## Impact

- **New custom code** (isolated, merge-safe):
  - `grails-app/assets/stylesheets/custom/obTheme.css` — the single theme file (tokens + `.ob-*`
    utilities + `:root:root` bridge + header/sidebar/dropdown/selector overrides). Its top comment
    carries the design intent and the "what to retheme vs leave alone" guidance.
- **Upstream touch points** (surgical — listed in design.md):
  - `grails-app/views/layouts/custom.gsp`, `react.gsp`, `main.gsp` — one
    `<asset:stylesheet src="custom/obTheme.css"/>` line in `<head>` each.
  - `CLAUDE.md` — UI-conventions pointer to `obTheme.css`.
- **No** edits to any upstream stylesheet or React/Groovy component (header/sidebar/dropdown/modal/
  selectors recolored by class override only), **no** Groovy/taglib, **no** DB/migration, **no** new
  npm dependency.
