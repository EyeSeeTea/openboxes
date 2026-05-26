## 1. Place theme assets (from the dropped-in package)

- [ ] 1.1 Move `theme.css` from `openspec/changes/app-theming/` to `grails-app/assets/stylesheets/custom/theme.css` (canonical tokens + `.ob-*` utilities).
- [ ] 1.2 Decide `THEME.md`'s home (repo root or `.claude/docs/`) and reference it from `CLAUDE.md` UI conventions.
- [x] 1.3 Strip layout-shifting global rules from `theme.css` (removed `html,body{margin:0;padding:0}` and base `font-size`/`line-height`; kept `box-sizing`, body color/bg/font, and link retheme). Only `body`/`a` global retheme + `.ob-*` utilities remain.

## 2. Curated self-hosted fonts

- [ ] 2.1 Add Inter + JetBrains Mono woff2 files under `grails-app/assets/fonts/custom/theme/`.
- [ ] 2.2 Create `grails-app/assets/stylesheets/custom/themeFonts.css` with `@font-face` for both families (asset-pipeline rewrites `url()`).

## 3. Bridge existing OB chrome to the new tokens

- [ ] 3.1 Create `grails-app/assets/stylesheets/custom/themeBridge.css` mapping OB vars to `--ob-*` under a `:root:root { … }` block (`--blue-primary`→`--ob-primary`, `--blue-700`→`--ob-primary-dark`, `--color-red`→`--ob-red`, `--color-green`→`--ob-green`, `--color-yellow`→`--ob-amber`, …).
- [ ] 3.2 Add `html body { font-family: var(--ob-font); }` to the bridge (specificity beats upstream `body`).

## 4. Bundle + wire into layouts (upstream touch points)

- [ ] 4.1 Create asset manifest `grails-app/assets/stylesheets/custom/obTheme.css` with `//= require custom/themeFonts`, `//= require custom/theme`, `//= require custom/themeBridge`.
- [ ] 4.2 Add one `<asset:stylesheet src="custom/obTheme.css"/>` line in the `<head>` of `grails-app/views/layouts/main.gsp` (after `application.css`).
- [ ] 4.3 Add one `<asset:stylesheet src="custom/obTheme.css"/>` line in the `<head>` of `grails-app/views/layouts/react.gsp`.
- [ ] 4.4 (Optional) Same line in `bootstrap.gsp`/`mobile.gsp`/`print.gsp`/`email.gsp` only if those surfaces need theming.

## 5. Verify

- [ ] 5.1 Run `./gradlew bootRun` and `npm run watch`.
- [ ] 5.2 GSP page (dashboard): `<head>` includes the theme asset; header/buttons/links/table chrome show navy + Inter; **DOM/layout unchanged** vs before.
- [ ] 5.3 React page (e.g. stock movement list): same recolor + font (bridge `:root:root` beats body-loaded bundle CSS).
- [ ] 5.4 Retheme test: change only `--ob-primary` (and `--ob-accent`) in `theme.css`, restart, reload a GSP and a React page → both reflect the new brand color; semantic green/red/amber and chart series unchanged.
- [ ] 5.5 `git diff --stat`: only new `custom/` asset files + the two layout one-liners; no upstream CSS modified; nothing under `bundle*` staged.

## 6. Docs

- [ ] 6.1 Confirm design.md "Upstream touch points" + "Deploy status" are accurate before archiving.
- [ ] 6.2 (Optional) Note in `src/js/custom/` that new React components should consume `--ob-*` tokens / `.ob-*` utilities per `THEME.md`.
