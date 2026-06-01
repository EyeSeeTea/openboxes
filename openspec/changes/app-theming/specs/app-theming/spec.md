## ADDED Requirements

### Requirement: Single in-code theme source of truth
The system SHALL define theme colors and the base font family in a single in-code theme stylesheet
(`grails-app/assets/stylesheets/custom/obTheme.css`), edited per deployment/customer branch to
rebrand the application. The theme SHALL be expressed as named CSS custom properties on `:root`
(colors) plus base font families, and SHALL support producing palette variants by overriding only the
`--ob-primary*` tokens.

#### Scenario: Changing a theme color recolors the app
- **WHEN** a developer changes a `--ob-primary*` value in the theme file and the application is restarted
- **THEN** pages render with the new color without any other source file being edited

#### Scenario: Theme defines semantic tokens
- **WHEN** the theme file is read
- **THEN** it provides semantic tokens (e.g. `--ob-primary`, `--ob-bg`, `--ob-ink`, `--ob-font`) usable by new custom components

### Requirement: Theme applies to both React and GSP pages
The system SHALL apply the theme to both React-rendered pages and Grails/GSP server-rendered pages
from the single theme file, by injecting it into the `<head>` of the layouts that serve real pages
(`react.gsp`, `custom.gsp`, `main.gsp`).

#### Scenario: GSP page reflects the theme
- **WHEN** a Grails/GSP page (e.g. Browse Inventory) is loaded
- **THEN** its `<head>` links the theme file and themed chrome (header, selectors, buttons) renders with the theme colors and font

#### Scenario: React page reflects the theme
- **WHEN** a React-rendered page (e.g. the dashboard) is loaded
- **THEN** its `<head>` links the theme file and themed components (header, sidebar, dropdowns) render with the theme colors and font

#### Scenario: One source feeds both stacks
- **WHEN** a single theme value is changed and the app is restarted
- **THEN** both a GSP page and a React page reflect the change

### Requirement: Theme overrides win the cascade without editing upstream stylesheets or components
The theme SHALL take precedence over the application's default styles regardless of stylesheet load
order, and SHALL achieve this without modifying upstream stylesheets (`colors.scss`, `main.scss`,
`openboxes.css`) or React/Groovy components.

#### Scenario: Injected theme beats later-loaded bundle defaults
- **WHEN** a React page loads its Webpack bundle CSS (which defines `:root` defaults and component styles) in the body after the head
- **THEN** the head-linked theme still applies, because token overrides use a higher-specificity `:root:root` selector and chrome overrides use higher-specificity class anchors (`.navbar.main-wrapper` / `#main-wrapper`)

#### Scenario: No upstream stylesheet or component is modified
- **WHEN** the change is reviewed
- **THEN** no edits exist in `src/css/colors.scss`, `src/css/main.scss`, `web-app/css/openboxes.css`, `HeaderStyles.scss`, `Dashboard.scss`, `LocationChooserModal.scss`, or any other component file — only the layout-head links

### Requirement: Existing brand chrome recolors via overridden palette variables
The theme SHALL override the existing brand-driving CSS variables already consumed by the application
(e.g. `--blue-primary`, `--color-red`, `--color-green`, `--color-yellow`, `--page-background`) so that
components already using those variables recolor without code changes.

#### Scenario: Components using existing variables recolor
- **WHEN** a component renders using `var(--blue-primary)`
- **THEN** it displays the theme's primary color, not the upstream default

### Requirement: Theme font family without external requests
The theme SHALL set the base font family (Inter for UI, JetBrains Mono for identifiers) via the
`--ob-font` / `--ob-font-mono` custom properties using a system-font fallback chain, making **no**
external web-font requests.

#### Scenario: No external font request
- **WHEN** any themed page loads
- **THEN** no request is made to an external font provider

#### Scenario: Both stacks use the themed font family
- **WHEN** a GSP page and a React page are loaded
- **THEN** body text on both renders in the theme's configured font family

### Requirement: Custom-package isolation
All new theming code SHALL live under a custom-isolated path
(`grails-app/assets/stylesheets/custom/obTheme.css`), and any modification to an upstream file SHALL
be limited to adding the theme stylesheet link to layout heads (plus the `CLAUDE.md` docs pointer) and
SHALL be documented as an upstream touch point.

#### Scenario: New code is isolated
- **WHEN** the change is reviewed
- **THEN** the entire theme resides in `grails-app/assets/stylesheets/custom/obTheme.css`

#### Scenario: Upstream edits are limited and documented
- **WHEN** the change is reviewed
- **THEN** the only upstream edits are the single-line theme-link additions in layout heads (and the `CLAUDE.md` pointer), each listed in the design document's upstream touch points
