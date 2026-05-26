# OpenBoxes — UNICEF Tajikistan Theme

Custom navy theming for OpenBoxes (React + Groovy pages), inspired by the
visual language of the Tajik Ministry of Health & Social Protection
(moh.tj). This document is the source of truth — drop `theme.css` into the
project and follow the rules below when restyling new screens.

> **For Claude (or any agent picking this up):** read this file first, then
> read `theme.css`. The theme is implemented as CSS custom properties on
> `:root` — re‑theming is a matter of overriding those variables, not
> rewriting components. Do **not** invent new colors. Do **not** change
> element positions or add brand imagery to existing OB pages — match the
> existing OpenBoxes layout 1:1 and only swap the visual tokens.

---

## 1. Principles

1. **Theme tokens only, not layout.** OpenBoxes already has a working
   information architecture. We are reskinning, not redesigning. Match the
   existing DOM and element order — change only colors, borders, type and
   small chrome details (badges, focus rings, header strips).
2. **One palette, one font stack.** All brand color flows from
   `--ob-primary`. All UI type is Inter. IDs / lot numbers / shipment
   identifiers use JetBrains Mono.
3. **Semantic chart colors stay.** Green = good / in‑stock,
   Red = critical / out‑of‑stock, Blue = neutral / shipped,
   Amber = warning. Do not retheme these to the brand color.
4. **Sparingly use the accent.** Tajik gold (`--ob-accent`, `#C9A24B`) is
   reserved for active‑tab underlines, login lock icon, and rare emphasis.
   Never for backgrounds, buttons, or chart series.
5. **No emoji, no decorative SVG illustrations, no gradients beyond the
   thin header strips.** This is a clinical supply‑chain tool.

---

## 2. Color tokens

All defined in `theme.css` as CSS custom properties on `:root`. Refer to
them by variable name, never hex.

| Token | Hex | Use |
|---|---|---|
| `--ob-primary` | `#1F4FA8` | Brand navy. Buttons, links, active nav, header strip, focus rings. |
| `--ob-primary-dark` | `#163F8A` | Button hover, gradient bottom. |
| `--ob-primary-darker` | `#0F2E68` | Deepest navy, used rarely (login gradient terminus). |
| `--ob-primary-soft` | `#E8EEF8` | Active-row tint, chip bg, badge bg. |
| `--ob-primary-softer` | `#F4F7FC` | Hover-row tint, card-header bg, table-header bg, footer bg. |
| `--ob-accent` | `#C9A24B` | Tajik gold. Active-tab underline; lock icon on login header. |
| `--ob-bg` | `#F6F8FB` | Page background. |
| `--ob-surface` | `#FFFFFF` | Card / panel surface. |
| `--ob-border` | `#E5E9F0` | Hairline divider. |
| `--ob-border-strong` | `#CDD4E0` | Form inputs, segmented buttons. |
| `--ob-ink` | `#0F1F3D` | Primary text. |
| `--ob-ink-2` | `#2C3A55` | Secondary text / cell body. |
| `--ob-muted` | `#5A6678` | Captions, footer, table headers. |
| `--ob-muted-2` | `#8893A4` | Placeholder text. |
| `--ob-green / red / amber / info` | semantic | Use only for status, charts, alerts. |
| `--ob-chart-1..5` | series | Bar/line/donut series colors. |

### Retheming
To produce another palette variant, override only `--ob-primary*` and
`--ob-accent`. All cards, tables, buttons, badges, focus rings and active
states inherit from these. See `OpenBoxes Theme.html` → "Palette
variants" section for three working examples (MoH Navy, Cobalt, Deep
Ink).

---

## 3. Typography

```
font-family: "Inter", -apple-system, BlinkMacSystemFont, "Segoe UI",
             system-ui, sans-serif;
mono-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo,
             monospace;
```

| Use | Size | Weight |
|---|---|---|
| Page title | 18 | 700 |
| Card title (uppercase, tracked) | 13 | 600 |
| Body / table cells | 13–14 | 400–500 |
| Stat numerics | 36–44 | 700 |
| Form labels (uppercase, tracked) | 11–12 | 600 |
| Footnotes, build strings | 11 | 400 |
| Identifiers (shipment, lot, etc.) | 11.5–12 | 600 / mono |

Apply `.mono { font-family: var(--ob-font-mono); }` for ID columns. Never
use mono for descriptive text.

---

## 4. Component conventions

### Header (two‑tier)
Navy ribbon (`--ob-primary`, 12px white text, 6px vertical pad) over a
white nav bar (64px tall, 14px medium‑weight items). Active nav item
takes `--ob-primary` text and a 3px `--ob-accent` underline.

### Page title bar (Groovy pages)
White strip, 18px bold ink text, 3px gold accent bar before the title.
Pattern: `.ob-page-title`.

### Cards
- 10px radius, 1px `--ob-border`, light shadow.
- Card header has a faint gradient from `--ob-primary-softer` → white,
  border-bottom 1px `--ob-border`.
- Card title: 13px, uppercase, 0.04em tracking, `--ob-ink`.
- Card tools (info / kebab) use `--ob-muted-2`.

### Buttons
- Primary `.ob-btn` — navy fill, white text, 6px radius, 600 weight.
- Secondary `.ob-btn.secondary` — white fill, `--ob-border-strong` border;
  hover swaps border + text to navy with `--ob-primary-softer` tint.
- Ghost `.ob-btn.ghost` — transparent, navy text.

### Forms
- Inputs: 1px `--ob-border-strong`, 6px radius, 9–12px vertical pad.
- Focus: `--ob-primary` border + 3px navy 15%‑alpha ring.
- Labels: 11–12px, uppercase, 600, `--ob-muted`, 0.05em tracking.
- Checkboxes: `accent-color: var(--ob-primary)`.

### Tables (groovy)
- Header cells: 11px uppercase 600, `--ob-muted`, bg
  `--ob-primary-softer`, 0.06em tracking.
- Body cells: 13px, `--ob-ink-2`, 12px vertical pad.
- Row hover: `--ob-primary-softer`.
- Numeric columns: `.right` + tabular‑nums.
- Pagination footer lives inside the table card with
  `--ob-primary-softer` bg.

### Status pills
Use the `.pill-status` pattern + a color modifier:
- `ps-pending` (blue), `ps-shipped` (purple), `ps-received` (green),
  `ps-delayed` (red), `ps-draft` (neutral grey).

### Chips / badges
`.ob-chip` (`--ob-primary-soft` bg, navy text) with semantic
modifiers `.green`, `.red`, `.amber`, `.neutral`.

### Login
Centered 460px card. Header strip is navy gradient with a gold
padlock; body holds the original OB elements (two inputs, button,
"Not registered?" link). **Do not** add logos, ribbons, or marketing
copy to the real login page — keep parity with the existing OB form.

---

## 5. What to retheme vs. leave alone

| Element | Retheme? | Notes |
|---|---|---|
| Navigation, header, footer | ✅ | Apply two‑tier + gold underline pattern. |
| Cards, panels, tabs | ✅ | Soft navy tint headers; navy primary tabs. |
| Tables, pagination | ✅ | Light navy header & hover; navy active page button. |
| Buttons, links, focus rings | ✅ | All flow from `--ob-primary`. |
| Chart axes, grid, tick labels | ✅ | Use `--ob-border` / `--ob-muted-2`. |
| **Chart data series** | ❌ | Keep green/red/blue/amber semantic. |
| **Status colors (success/error/warn)** | ❌ | Keep semantic. |
| **Element positions, DOM order, copy** | ❌ | Match real OB pages 1:1. |
| **Imagery / illustrations** | ❌ | Don't add. Keep the UNICEF logo where it already lives; nowhere else. |

---

## 6. How to apply this to the OpenBoxes codebase

### Quick path (single‑theme override)
1. Copy `theme.css` into the project's static assets folder
   (`grails-app/assets/stylesheets/` for Groovy pages,
   `src/styles/` for React).
2. Import it **after** OpenBoxes' own stylesheets so the
   `:root` variables and any overlapping selectors win.
3. Map OpenBoxes' existing class names to our utilities where
   sensible, or add `:root` variable overrides that target the
   bootstrap‑era class names in `theme.css`. (Example: map
   `.btn-primary` to use `--ob-primary`.)

### Codebase path (recommended)
Extract the tokens into a SCSS partial that overrides OpenBoxes' own
brand variables:

```scss
// _theme-unicef-tjk.scss
$brand-primary:        #1F4FA8;
$brand-primary-dark:   #163F8A;
$brand-accent:         #C9A24B;
$body-bg:              #F6F8FB;
$panel-bg:             #FFFFFF;
$border-color:         #E5E9F0;
// …continue mapping the rest of this doc.
```

Then import this partial first in the OB build pipeline so the
existing component SCSS picks up the new values without further
changes.

### Working with Claude
When asking Claude to apply this theme to a new OpenBoxes screen, paste
into the chat (or attach via `CLAUDE.md` at the repo root):

> Follow the OpenBoxes UNICEF‑TJK theme as defined in `THEME.md`. Apply
> the navy palette via `theme.css` tokens only. Do not change element
> positions, copy, or layout — only retheme the existing markup.

Reference `OpenBoxes Theme.html` as the visual ground truth — open it
side‑by‑side while restyling.

---

## 7. Files in this package

- `theme.css` — the canonical token + utility stylesheet. Drop into the
  app.
- `THEME.md` — this document.
- `OpenBoxes Theme.html` — interactive design canvas with:
  - Hero dashboard with live `Tweaks` panel for primary/accent
  - Themed Login, Browse Inventory, Inbound Stock Movements
  - Three palette variants of the dashboard
- `pages/dashboard.html`, `pages/inventory.html`, `pages/movements.html`,
  `pages/login.html` — standalone themed mocks consuming `theme.css`.

When the canonical palette changes, update **only** `theme.css` and
the hex tables in this document. All mocks will update automatically.
