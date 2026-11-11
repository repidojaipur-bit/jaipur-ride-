# Design System and UI Guidelines — Drive Partner

**Document ID:** DP-DESIGN-001 | **Version:** 1.0

## 1. Design intent
Create a distinct, trustworthy and driver-friendly Android experience. It should be legible outdoors, practical, and easy to operate with one hand. Common mobility-app conventions are acceptable, but do not imitate a competitor's exact brand, screen composition, icon set, illustrations, wording or screenshots. Product name and palette are provisional; centralize them as tokens.

## 2. Color tokens
Initial neutral/blue-green direction, subject to brand approval:

| Token | Hex | Purpose |
|---|---|---|
| `brandPrimary` | `#1769AA` | Main actions and selected navigation |
| `brandPrimaryDark` | `#124B7A` | Strong emphasis/pressed state |
| `brandSecondary` | `#16A36A` | Online/success state only |
| `background` | `#FFFFFF` | Main canvas |
| `surface` | `#FFFFFF` | Cards and sheets |
| `surfaceSubtle` | `#F5F7FA` | Secondary sections |
| `textPrimary` | `#17202A` | Main text |
| `textSecondary` | `#697586` | Supporting text |
| `border` | `#E7EAF0` | Dividers/outlines |
| `warning` | `#F5A623` | Attention/expiry |
| `danger` | `#DC3545` | Errors/destructive actions |
| `info` | `#2878C7` | Neutral information |

Avoid using green as generic decoration if it makes Online/Success unclear. Status must include text/icon, not color alone. Verify contrast for real sizes before release.

## 3. Typography
- Use a readable Android sans-serif family with Devanagari-capable fallback for Hindi.
- Define Display, Headline, Title, Body, Label and Caption roles.
- Use semibold/bold for monetary totals, with currency and period/status labels.
- Use sentence case; avoid long all-caps labels.
- Do not shrink text to force long addresses into one line; allow wrapping and accessible detail views.
- Respect system font scaling; use Compose `sp` typography tokens, not raw pixel sizes.

## 4. Spacing, shape and elevation
- Base spacing unit: 4 dp.
- Common spacing: 4, 8, 12, 16, 20, 24 and 32 dp.
- Page inset typically 16 dp; adapt for large displays.
- Card corner radius: 16 dp baseline; sheets 20–24 dp.
- Critical controls must have comfortable Android-compliant touch targets.
- Use subtle elevation sparingly; favor clear spacing and surface contrast.

## 5. Component patterns

### Duty control
Distinct states: Offline, Turning Online, Online, Turning Offline, Blocked and Connection Unknown. Display status text with the switch. Disable rapid repeat taps while request is pending. If backend fails, return to last confirmed state and explain that the change was not confirmed.

### Earnings card
Show amount, period and last-updated time. Separate estimated, finalized and settled. Never imply withdrawable balance unless wallet API confirms it.

### Offer card
Show category, pickup/drop hierarchy, pickup distance, trip distance/duration, estimated base and incentive breakdown, and countdown. Accept and Reject must both be clear and accessible. Do not use visual pressure to hide rejection.

### Status chip
Use text + icon + semantic color. Keep status labels consistent with API mapping.

### Map and overlays
Show only useful layers and keep zoom/recenter controls reachable. Demand/zone overlays require real backend data and an explanation/legend. Do not cover pickup/drop markers with cards; provide a text route summary for accessibility.

### Forms
Persistent labels, inline validation, correct keyboard/input types, safe draft persistence and explicit submission status. Do not silently discard user input.

## 6. Navigation and responsive layout
- Five primary bottom destinations as specified in `11_SCREENS.md`.
- Side drawer for secondary profile/help/safety/settings destinations.
- Critical active-trip actions remain prominent.
- Use adaptive scrollable layouts; avoid fixed heights that clip compact devices.
- Handle display cutouts, system bars, edge-to-edge and gesture navigation.
- Use lazy lists for long content.

## 7. Motion
Motion communicates state changes, not decoration. Use restrained transitions for sheets/cards. Respect reduced-motion settings where available. Do not animate earnings as if money is being earned live without a confirmed event. Avoid flashing demand markers and excessive map pulses.

## 8. Icons and imagery
Use an original, consistent licensed icon set or Material Symbols. Create original illustrations. Never use competitor screenshots or promotional assets as production UI. Driver/vehicle photographs are user-provided content requiring privacy/storage controls. Avoid decorative stock photos on functional screens.

## 9. Localization
- Externalize all visible strings.
- English and Hindi are baseline languages; validate Devanagari fonts and line wrapping.
- Format INR by locale; store paise integer and currency code.
- Store UTC timestamps; display in user locale/timezone.
- Use complete translatable sentences and plural resources, not concatenated fragments.
- Handle mixed-script addresses without clipping.

## 10. Accessibility
- Provide Compose semantics for icon buttons, switches, countdowns and map actions.
- Add content descriptions for meaningful imagery.
- Ensure logical focus order, readable large text and sufficient contrast.
- Never communicate a status by color alone.
- Provide non-map textual alternatives for pickup/drop and route details.
- Avoid tiny secondary actions and low-contrast supporting text.

## 11. Required screen states
Every network-backed screen handles: initial loading, loaded content, empty, validation, server/network error with safe retry, offline/stale data with timestamp where useful, and permission-denied states where relevant.

## 12. Brand configuration
Store logo, product name, colors and typography in centralized assets/theme configuration. Final brand can change without editing feature screens. Do not treat the provisional palette as an approved final identity; avoid inline hex colors in feature modules.
