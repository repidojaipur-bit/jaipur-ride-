# Drive Partner — Original UI Design System (v2)

**Document ID:** DP-DESIGN-SPEC-002
**Version:** 2.0.0
**Status:** Implemented (Android source of truth + live preview)
**Package:** `com.drivepartner.app.ui`
**Preview:** `npm run dev` (this repository root) — the living design-system site
**Companion docs:** `docs/design/ICON_INVENTORY.md`, `docs/Design.md`, `tools/design_system/drive_icons.py`

---

## 1. Design intent

A premium, clean, modern driver application for partners who read a phone in direct sunlight, on a
vibrating handlebar mount, one-handed, in 15-second decision windows. The system is **original**:
palette, icon geometry, illustrations and map cartography are authored for this product. No
competitor branding, screenshots, stock photography or third-party icon font is used anywhere.

Platform targets:

- Native Android, Jetpack Compose + Material 3, single-activity architecture.
- `minSdk 24` (Android 7.0) — vectors, per-app locales and edge-to-edge degrade gracefully below API 28.
- `targetSdk 35` — edge-to-edge is explicit, insets consumed in Compose.
- English + हिंदी (Devanagari-capable system font stack; no bundled font asset required).
- Light theme, dark theme and SYSTEM follow, all driven by semantic tokens.

## 2. Colour tokens

Source of truth: `ui/theme/Color.kt` (raw values) → `ui/theme/DriveColorTokens.kt` (semantic layer).
Components must consume `DriveColors.current.*`, never raw values or hardcoded hex.

| Semantic role | Light | Dark | Purpose |
|---|---|---|---|
| `background` | `#FFFFFF` | `#0E141B` | Primary canvas |
| `backgroundSubtle` | `#F5F7FA` | `#0A0F14` | Secondary canvas behind card stacks |
| `surface` | `#FFFFFF` | `#16202B` | Cards, sheets, dialogs |
| `surfaceSunken` | `#F5F7FA` | `#101820` | Input wells, empty plates |
| `surfaceTonal` | `#F0F3F8` | `#1F2B38` | Pressed rows, segmented controls |
| `brand` | `#1769AA` | `#5CA8E6` | Primary actions, selected navigation |
| `onBrand` | `#FFFFFF` | `#04121E` | Content on brand (5.77:1 light) |
| `border` | `#E7EAF0` | `#2D3C4E` | Hairline card borders, dividers |
| `borderStrong` | `#7C8798` | `#5E7186` | Outlines that carry meaning alone (≥3:1) |
| `textPrimary` | `#17202A` | `#F1F5F9` | 16.45:1 / 15.03:1 — AAA |
| `textSecondary` | `#697586` | `#A8B6C6` | AA on its canvas |
| `textTertiary` | `#656F80` | `#8895A8` | Meta text, unselected controls (AA) |
| `success` / `successStrong` | `#16A36A` / `#12875A` | `#3ECF8E` | Online & confirmed only; `*Strong` for text/filled buttons |
| `warning` / `warningStrong` | `#F5A623` / `#8A5A06` | `#FBC65F` | Countdowns, payout highlights; amber is never body text on light |
| `danger` | `#DC3545` | `#FF7C89` | SOS, errors, destructive (4.53:1 with white) |
| `neutral` | `#697586` | `#93A0B2` | Offline / inactive |

Container pairs (`brandContainer/onBrandContainer`, `successContainer/onSuccessContainer`, …) are
published for every semantic family and verified ≥ 4.5:1 by `DesignTokensTest`.

**Rule:** status is never colour alone — text + icon + tint, always.

## 3. Typography

`ui/theme/Type.kt`. Roles: display → label (Material scale) plus product roles in `DriveTextRoles`
(`moneyHero`, `moneyLarge`, `moneyMedium`, `countdown`, `overline`, `address`, `caption`).

- Sizes in `sp`; the system font scale is respected end-to-end (no raw px, no clamping).
- **No colour is baked into a style** — text inherits `LocalContentColor`, which is what makes one
  `Text(style = …)` correct in both themes.
- Family: platform sans stack; Android ships Noto Sans Devanagari, so Hindi shaping works on API 24.
- Devanagari line heights use `LineHeightStyle.Alignment.Proportional` to share an optical baseline
  with Latin text.
- Monetary totals are always semibold/black with an explicit currency and period label.

## 4. Shape, spacing, elevation

`ui/theme/Shape.kt`, `Spacing.kt`, `Elevation.kt`.

- Card radii live in a **14–20 dp band** (control 12, cardSmall 14, card 16, cardLarge/sheet 20);
  pills are fully round; the band is asserted by unit test.
- 4 dp base grid: 2/4/8/12/16/20/24/32/48. Page inset 16 dp on phones, 24/32 dp on larger windows.
- Elevation is subtle by design: L0 flat → L1 card → L2 hero → L3 bars → L4 sheets → L5 dispatch
  overlay. Dark mode collapses elevation (shadows are invisible on dark canvases) and the hairline
  border carries separation instead.
- Shadow colour is ink-tinted (`ambientColor`/`spotColor` tokens); below API 28 the platform shadow
  is used unchanged.

## 5. Motion

`ui/theme/Motion.kt`: 90/140/220/300/420 ms tokens with `FastOutSlowIn` defaults and `EaseOut` for
entering surfaces. `rememberReducedMotionEnabled()` reads the system animator scale and collapses
every duration to 0 ms. Countdowns are **never** eased: they re-read authoritative server remaining
time on a 250 ms tick. Earnings are never animated as if accruing live.

## 6. Responsive layout

`ui/theme/Responsive.kt`: window size classes at 600/840 dp width and 480/900 dp height (Material
breakpoints, implemented locally for API 24). The class publishes gutter, card gap, grid columns
(2/3/4), metric columns, map height and maximum reading measure. `DriveAdaptivePane` centres and
caps content on medium/expanded windows. Font-scale buckets (1.2×, 1.45×) let rows collapse into
stacks instead of truncating addresses.

## 7. Accessibility

`ui/theme/Accessibility.kt`:

- Touch targets ≥ 48 dp; primary actions 52 dp (`driveMinTouchTarget`).
- WCAG maths ships in the product: `contrastRatio`, `meetsWcagAa`, `meetsUiComponentContrast`, and
  the Design System gallery displays live ratios for every swatch.
- Semantics helpers: `driveHeading`, `driveLiveRegion` (countdowns, duty transitions),
  `driveStateDescription`, `driveMergedGroup` (a card reads as one TalkBack node), `driveDecorative`
  (illustrations stay out of the accessibility tree).
- Contrast floors: AA (4.5:1) for body text, 3:1 for meaningful non-text UI; `textDisabled` is
  explicitly non-informative.
- Screen readers get text alternatives for pickup/drop and route details; the map never is the only
  representation of a route.

## 8. Iconography

**Original set: 64 glyphs.** Source of truth `tools/design_system/drive_icons.py` (24 dp optical
grid, 1.8 dp round stroke/round join). The generator emits:

- `app/src/main/res/drawable/ic_drv_*.xml` — Android VectorDrawables (stroke based, tintable),
- `app/src/main/java/com/drivepartner/app/ui/icons/DriveIcons.kt` — typed registry,
- `public/design/icons.json` — preview mirror,
- `docs/design/ICON_INVENTORY.md` — reviewer inventory.

Render only through `DriveIcon` / `DriveIconWell` so size, tint and content descriptions stay
consistent. Icon-only controls require a `contentDescription`; decorative icons pass `null`.
Primary navigation, drawer, duty control, chips, empty/error states and the gallery all use this
set. Legacy Material glyph usages in older feature screens are being migrated incrementally —
inventory counts are tracked in `08_TRACKER.md`.

## 9. Illustrations

`ui/illustrations/DriveIllustrations.kt`: empty orders, offline duty, verification pending, wallet,
recoverable error, safety — plus the `DriveBrandMark` shield/chevron logo. All are drawn as Compose
`Canvas` code on a centred 100-unit grid, so they scale losslessly and recolor per theme. No
bitmaps, no screenshots as backgrounds, no stock art (Design.md Section 8).

## 10. Components

Reusable layer in `ui/components/`: `DriveCard` (+ `DriveHeroCard`, `DriveSunkenCard`),
`DrivePrimaryButton` / `DriveSuccessButton` / `DriveOutlinedButton` / `DriveDangerButton`,
`StatusChip` (+ partner/verification variants), `OnlineDutySwitch`, `EarningsCard`,
`OrderRequestCard`, `DriveTopBar` / `DriveSecondaryTopBar`, `DriveBottomNavigation`,
`DriveDrawerSheet`, `EmptyState`, `ErrorState`, `LoadingIndicator`, state views
(`DriveEmptyView`, `DriveErrorView`, `DriveOfflineView`, `DrivePermissionView`, `DriveUiStateHost`),
`DriveIcon`, `DriveIconWell`, `EnvironmentBanner`.

Button contract: full-width by default, ≥52 dp primary / ≥48 dp secondary, pressed and disabled
colours from tokens, accessible container/content pairs (white on `#1769AA` 5.77:1, white on
`#12875A` 4.53:1, white on `#DC3545` 4.53:1).

App bar contract: brand blue with light ink in daylight; a raised dark surface with light ink in
dark mode (a bright bar glares in a night cabin). Status bar icon contrast is owned by Compose
(`DriveSystemBarAppearance`, `DriveStatusBarIcons`); light canvases such as onboarding opt into dark
icons explicitly.

## 11. Screen states

Every network-backed screen ships: initial loading, loaded, empty (truthful, zero fake data),
validation, server/network error with safe retry, offline/stale with timestamp, and
permission-denied. The gallery's *Screen states* section renders each from live tokens.

## 12. Design System gallery (in-app)

`ui/screens/designsystem/DesignSystemGalleryScreen.kt`, route `design_system`, offered from the
drawer in **demo builds only**. It renders colours (with live WCAG ratios), type, shape, spacing,
elevation, motion (with a playable transition), the full icon set, illustrations, components,
states, responsive metrics and the accessibility contract — and can preview the dark theme
independently of the app theme.

## 13. Brand configuration

All brand decisions live in the theme layer and generated assets; feature code contains no hex
literals (asserted by code review tooling and by the absence of `Color(0x…)` outside `ui/theme`).
A future brand refresh changes `Color.kt` + `DriveColorTokens.kt` + the icon generator, and nothing
else.

## 14. Verification

- `gradle testDemoDebugUnitTest` → `DesignTokensTest` (contrast, radii band, touch targets, motion,
  breakpoints, icon registry uniqueness).
- `python3 tools/design_system/drive_icons.py` regenerates icons deterministically.
- Live preview (`npm run dev`) renders the same tokens for human review, light/dark and EN/HI.
