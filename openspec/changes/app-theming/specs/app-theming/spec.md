## ADDED Requirements

### Requirement: Single in-code theme source of truth
The system SHALL define theme colors and the base font family in a single in-code theme stylesheet
(`theme.css`) that is edited per deployment/customer branch to rebrand the application. The theme
SHALL be expressed as named CSS custom properties on `:root` (colors) plus base font families, and
SHALL support producing palette variants by overriding only the `--ob-primary*` and `--ob-accent`
tokens.

#### Scenario: Changing a theme color recolors the app
- **WHEN** a developer changes a color value in the theme definition file and the application is restarted
- **THEN** pages render with the new color without any other source file being edited

#### Scenario: Theme defines semantic tokens
- **WHEN** the theme definition is read
- **THEN** it provides semantic tokens (e.g. `--ob-primary`, `--ob-accent`, `--ob-font-base`) usable by new custom components

### Requirement: Theme applies to both React and GSP pages
The system SHALL apply the theme to both React-rendered pages and Grails/GSP server-rendered pages
from the single theme source, by injecting the theme as a `:root` CSS-custom-properties `<style>`
block into the `<head>` of the relevant page layouts.

#### Scenario: GSP page reflects the theme
- **WHEN** a Grails/GSP page (e.g. the dashboard) is loaded
- **THEN** its `<head>` contains the injected theme style block and themed chrome (e.g. header, buttons) renders with the theme colors and font

#### Scenario: React page reflects the theme
- **WHEN** a React-rendered page is loaded
- **THEN** its `<head>` contains the injected theme style block and themed components render with the theme colors and font

#### Scenario: One source feeds both stacks
- **WHEN** a single theme value is changed and the app is restarted
- **THEN** both a GSP page and a React page reflect the change

### Requirement: Theme overrides win the cascade without editing upstream stylesheets
The injected theme SHALL take precedence over the application's default stylesheet values
regardless of stylesheet load order, and SHALL achieve this without modifying upstream stylesheets
(`colors.scss`, `main.scss`, `grails.css`, component stylesheets) and without using `!important`.

#### Scenario: Injected theme beats later-loaded bundle defaults
- **WHEN** a React page loads its Webpack bundle CSS (which defines `:root` defaults) in the body after the head
- **THEN** the head-injected theme values still apply, because the injected block uses a higher-specificity selector

#### Scenario: No upstream stylesheet is modified
- **WHEN** the change is reviewed
- **THEN** no edits exist in `src/css/colors.scss`, `src/css/main.scss`, `grails-app/assets/stylesheets/grails.css`, or any component `.scss` file

### Requirement: Existing brand chrome recolors via overridden palette variables
The injected theme SHALL override the existing brand-driving CSS variables already consumed by the
application (e.g. `--blue-primary`, `--color-red`, `--color-green`, `--color-yellow`) so that
components already using those variables recolor without code changes.

#### Scenario: Components using existing variables recolor
- **WHEN** a component renders using `var(--blue-primary)`
- **THEN** it displays the theme's primary color, not the upstream default

### Requirement: Curated self-hosted fonts
The system SHALL provide the theme font from a curated, self-hosted font set (no external web-font
requests), served same-origin so both React and GSP pages can use it, and applied via the
`--ob-font-base` custom property.

#### Scenario: Font loads without external requests
- **WHEN** any themed page loads
- **THEN** the base font is served from the application's own assets and no request is made to an external font provider

#### Scenario: Both stacks use the themed font
- **WHEN** a GSP page and a React page are loaded
- **THEN** body text on both renders in the theme's configured base font

### Requirement: Custom-package isolation
All new theming assets SHALL live under custom-isolated paths
(`grails-app/assets/stylesheets/custom/` and `grails-app/assets/fonts/custom/`), and any
modification to an upstream file SHALL be limited to adding the theme stylesheet link to layout
heads and SHALL be documented as an upstream touch point.

#### Scenario: New files are isolated
- **WHEN** the change is reviewed
- **THEN** the theme stylesheet, bridge, font CSS, and font files reside under `custom/` asset paths

#### Scenario: Upstream edits are limited and documented
- **WHEN** the change is reviewed
- **THEN** the only upstream edits are single-line theme stylesheet-link additions in layout head(s), each listed in the design document's upstream touch points
