## 1. Theme file

- [x] 1.1 Create `grails-app/assets/stylesheets/custom/obTheme.css` — the single theme file: `:root` `--ob-*` tokens + `.ob-*` utilities + the `:root:root` bridge + the live header/sidebar/dropdown/selector overrides.
- [x] 1.2 Put the design intent + "retheme vs leave alone" guidance in the file's top comment (no separate `THEME.md`).
- [x] 1.3 Omit layout-shifting global rules (no `html,body{margin/padding}`, no base `font-size`/`line-height`); keep `box-sizing`, body color/bg/font, and the link retheme so existing OB pages don't reflow.
- [x] 1.4 One brand color: `--ob-primary*` + neutrals + semantic tokens; no `--ob-accent`.

## 2. Bridge (recolor existing OB chrome)

- [x] 2.1 `:root:root` block maps `--blue-primary/500/700/800`, `--color-red/green/yellow`, and `--page-background` onto the `--ob-*` tokens (specificity bump beats the React bundle's later `:root`).
- [x] 2.2 `html body { font-family: var(--ob-font); }` applies the font family without `!important`.

## 3. Live overrides (class-level — no component edits)

- [x] 3.1 Header → solid navy bar, 1px dark-navy bottom line, white nav (82% / full on hover), white active underline, translucent warehouse pill, white tool icons. React anchor `.navbar.main-wrapper`, GSP `#main-wrapper`.
- [x] 3.2 Dashboard sidebar (`.configs-left-nav`) → solid navy; hover/active items flip to a white pill with navy text + navy icon.
- [x] 3.3 Nav + settings dropdowns (`.dropdown-menu-content`, `.dropdown-item`, `.subsection-section-item`, headings) → light-grey panel, dark rows, white+navy hover, muted headings — identical on both stacks.
- [x] 3.4 Choose-Location modal → navy via the bridge (no dedicated override).
- [x] 3.5 Groovy selectors (Chosen/Select2 highlighted option) + pagination (`.currentStep`) → navy.

## 4. Wiring (upstream touch points)

- [x] 4.1 Inject `<asset:stylesheet src="custom/obTheme.css"/>` in the `<head>` of `custom.gsp`, `react.gsp`, and `main.gsp`.
- [x] 4.2 `CLAUDE.md` Frontend Conventions → Theming points to `obTheme.css`.
- [ ] 4.3 (Optional) Same line in `bootstrap.gsp`/`mobile.gsp`/`print.gsp`/`email.gsp` only if those surfaces need theming.

## 5. Verify

- [ ] 5.1 Retheme test: change `--ob-primary*` only, reload a GSP page and a React page → both recolor; semantic green/red/amber and chart series unchanged.
- [x] 5.2 `git diff`: only the new `custom/obTheme.css` + the three layout one-liners + the `CLAUDE.md` pointer; no upstream stylesheet or component modified; nothing under `bundle*` staged.
- [x] 5.3 Confirm no `THEME.md`, no `theme.css`/`themeBridge.css`/`themeFonts.css`, no font files.

## 6. Docs

- [x] 6.1 design.md "Upstream touch points" + "Deploy status" accurate; theme guidance lives in the `obTheme.css` top comment.
- [ ] 6.2 (Optional) Note in `src/js/custom/` that new React components should consume `--ob-*` tokens / `.ob-*` utilities.
