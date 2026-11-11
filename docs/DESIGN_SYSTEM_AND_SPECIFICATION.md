# Drive Partner — UI/UX Design System & Screen Specifications

**Document ID:** DP-DESIGN-SPEC-001  
**Version:** 1.0.0  
**Design Constitution:** Original Professional Driver Experience  
**Target Platform:** Native Android (Jetpack Compose + Material 3)  
**Package:** `com.drivepartner.app.ui`  

---

## 1. Executive Design Philosophy

The **Drive Partner** design system is engineered specifically for mobility and delivery drivers operating in demanding real-world conditions: high ambient outdoor sunlight, vibrating bike/auto phone mounts, one-handed operation while stopped at intersections, and fast-paced decision making during 15-second dispatch offers.

### 1.1 Strict Brand Independence (Anti-Copy Discipline)
- **Zero Competitor Imitation**: Drive Partner maintains its own independent visual brand. It strictly avoids Rapido's bright yellow canvas, Uber's stark black monochrome, or Ola's green circle motifs.
- **Color Identity**:
  - **Main Canvas**: Pure White (`#FFFFFF`) for crisp daylight legibility.
  - **Secondary Surfaces**: Light Grey (`#F3F4F6` / `#F9FAFB`) for visual segmentation.
  - **Primary Action Color**: Deep Blue (`#0D2560`) conveying institutional trust, security, and stability.
  - **Duty & Success Indicators**: Green (`#16A34A` / `#DCFCE7`) used **exclusively** for Online availability and confirmed successful operations.
  - **Earnings & Rewards Accent**: Warm Gold / Drive Yellow (`#CA8A04` / `#FEF08A` container) for monetary totals and milestone targets.
- **Status Accessibility**: Status is **never communicated by color alone**. Every status chip, badge, or banner combines semantic text, explicit icon, and semantic tint.

---

## 2. Visual Tokens & Theme Foundation

### 2.1 Color Palette Tokens

```kotlin
// Primary Brand: Deep Blue (Professional, Trustworthy)
val DriveBluePrimary = Color(0xFF0D2560)
val DriveBlueDark = Color(0xFF08173D)
val DriveBlueLight = Color(0xFF1E3A8A)
val DriveBlueContainer = Color(0xFFE0E7FF)
val DriveOnBlueContainer = Color(0xFF0A1A44)

// Online & Success: Green (Restricted to Online duty & completed status)
val DriveGreenOnline = Color(0xFF16A34A)
val DriveGreenDark = Color(0xFF15803D)
val DriveGreenContainer = Color(0xFFDCFCE7)
val DriveOnGreenContainer = Color(0xFF14532D)

// Earnings & Financial Highlights: Warm Gold / Yellow
val DriveYellowEarning = Color(0xFFCA8A04)
val DriveYellowGold = Color(0xFFEAB308)
val DriveYellowContainer = Color(0xFFFEF08A)
val DriveOnYellowContainer = Color(0xFF713F12)

// Neutrals & Surfaces (White background, light grey secondary surfaces)
val DriveSurfaceWhite = Color(0xFFFFFFFF)
val DriveSurfaceLightGrey = Color(0xFFF3F4F6)
val DriveSurfaceCard = Color(0xFFFFFFFF)
val DriveSurfaceVariant = Color(0xFFF9FAFB)
val DriveSurfaceBorder = Color(0xFFE5E7EB)

// Typography & Contrast
val DriveTextPrimary = Color(0xFF111827)    // WCAG AAA Contrast on White
val DriveTextSecondary = Color(0xFF4B5563)  // WCAG AA Contrast
val DriveTextMuted = Color(0xFF9CA3AF)

// Status & Error
val DriveError = Color(0xFFDC2626)
val DriveErrorContainer = Color(0xFFFEE2E2)
val DriveOnErrorContainer = Color(0xFF7F1D1D)
val DriveOfflineGrey = Color(0xFF6B7280)
val DriveOfflineContainer = Color(0xFFF3F4F6)
```

### 2.2 Typography Scale
Typography uses modern Android system sans-serif with verified Devanagari fallback support for full Hindi localization:

| Role | Size | Weight | Line Height | Usage |
|---|---|---|---|---|
| **Display Large** | 30sp | Black (900) | 36sp | Hero earnings totals (e.g., `₹1,450.00`) |
| **Headline Medium** | 22sp | Bold (700) | 28sp | Screen headers, Dispatch fare banner |
| **Title Medium** | 16sp | Bold (700) | 22sp | Card titles, Partner name, Dialog titles |
| **Body Large** | 15sp | Medium (500) | 20sp | Primary button labels, Form inputs |
| **Body Medium** | 13sp | Regular (400) | 18sp | Address descriptions, Instructions, Ledger |
| **Label Small** | 11sp | SemiBold (600) | 14sp | Badges, Chips, Timers, Bottom tab labels |

### 2.3 Shapes & Radius
- **Cards**: `RoundedCornerShape(16.dp)` — Clean, rounded modern aesthetic without clipping content.
- **Buttons**: `RoundedCornerShape(12.dp)` — Comfortable rounded rectangles.
- **Pills / Status Chips**: `RoundedCornerShape(20.dp)` — High-recognition semantic badges.
- **Bottom Sheets**: `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`.

### 2.4 Spacing & Touch Targets
- **Base Grid**: 4dp multiples (`4dp`, `8dp`, `12dp`, `16dp`, `20dp`, `24dp`, `32dp`).
- **Standard Inset**: `16dp` horizontal content padding.
- **Touch Target Accessibility**: Minimum interactive height of **52dp** for all primary and secondary buttons (comfortably exceeds the Android 48dp accessibility guideline).

---

## 3. Reusable Component Inventory

The following 13 production-grade Compose components have been implemented in `com.drivepartner.app.ui.components`:

1. **`PrimaryButton`** (`PrimaryButton.kt`):
   - 52dp height, Deep Blue container (`DriveBluePrimary`), white text.
   - Built-in loading spinner state preventing duplicate taps.
   - Optional leading icon with semantic alignment.
2. **`SecondaryButton`** (`SecondaryButton.kt`):
   - 52dp height, neutral border (`DriveSurfaceBorder`), dark text.
   - Outlined aesthetic for secondary actions (e.g. "Decline", "Cancel").
3. **`StatusChip`** (`StatusChip.kt`):
   - 20dp pill with icon + text label.
   - Overloads for `PartnerStatusChip` (Online / Offline) and `VerificationStatusChip` (Verified, Under Review, Action Required, Suspended).
4. **`EarningsCard`** (`EarningsCard.kt`):
   - High-emphasis Drive Yellow Container (`#FEF08A`) with bold INR currency display.
   - Subtext for period context (e.g. "Reset daily at 12:00 AM").
5. **`OrderRequestCard`** (`OrderRequestCard.kt`):
   - Service category badge (Bike Taxi, Auto, Cab, Delivery).
   - Animated synchronized countdown progress bar (15s to 0s).
   - Clear pickup & drop route summary with connecting line and distances.
   - Guaranteed driver earning highlight in INR.
   - Dual actions: High-emphasis Green Accept and clean Decline button.
6. **`DriveNavigationBar`** (`NavigationBar.kt`):
   - Material 3 NavigationBar with 5 primary destinations (Home, Orders, Earnings, Rewards, Profile).
   - Active tab indicator in Deep Blue Container.
   - Full bilingual StringRes labels.
7. **`OnlineDutySwitch`** (`OnlineDutySwitch.kt`):
   - Driver-focused availability toggle card.
   - Dynamic container color (Green when Online, Neutral White when Offline).
   - Instant toggle button with power icon.
8. **`ProfileCard`** (`ProfileCard.kt`):
   - Category icon badge, partner name, ID, vehicle registration number plate.
   - Star rating badge and verified green status chip.
9. **`RewardCard`** (`RewardCard.kt`):
   - Milestone trophy icon, progress bar (% completed), guaranteed bonus amount.
   - Claim action when qualified.
10. **`TransactionCard`** (`TransactionCard.kt`):
    - Real ledger transaction row: Credit (+₹ in green) vs Debit (-₹ in gray).
    - Date/time, reference ID, and formatted currency amount.
11. **`LoadingIndicator`** (`LoadingIndicator.kt`):
    - Deep Blue circular progress spinner with localized loading string.
12. **`EmptyState`** (`EmptyState.kt`):
    - Truthful empty state view adhering to zero-fake-data discipline.
    - Soft grey circular icon badge, title, subtitle, and optional CTA button.
13. **`ErrorState`** (`ErrorState.kt`):
    - Error red alert container, descriptive message, and "Try Again" retry button.

---

## 4. Screen-Level Design Specifications

### S-01: Splash Screen
- **Background**: Pure White (`#FFFFFF`).
- **Brand Mark**: Deep Blue shield vector with stylized forward arrow.
- **Typography**: "Drive Partner" (24sp Bold Deep Blue), "Driver & Delivery Partner Platform" (13sp Muted).
- **Progress**: Linear progress indicator at bottom (`4dp` height).

### S-02: Language Selection
- **Header**: "Choose Language / भाषा चुनें".
- **Cards**: Two selectable 16dp rounded cards:
  - English (Subtitle: "Default language for notifications & navigation").
  - हिंदी (Subtitle: "नेविगेशन और सूचनाओं के लिए हिंदी").
- **Bottom Action**: Fixed 52dp `PrimaryButton` ("Continue / आगे बढ़ें").

### S-03 & S-04: Phone Login & OTP Verification
- **Input Field**: OutlinedTextField with `+91` fixed country prefix, phone icon, and 16dp rounded corner shape.
- **OTP Screen**: 6 distinct rounded digit boxes (`48dp` x `56dp`) with active Deep Blue border focus.
- **Countdown**: 60s timer in bold text, transitioning to active "Resend OTP" text link upon expiry.

### S-05 to S-08: Onboarding & Verification
- **Progress Stepper**: Compact 3-step indicator (`Personal` ➔ `Vehicle` ➔ `Documents`).
- **Document Row**: White card with document name, expiry date, status badge, and "Upload / Replace" CTA.
- **Verification Status**: Centered status card with Deep Blue clock icon for Under Review, Amber alert for Action Required, and Red shield for Blocked.

### S-09: Home Dashboard
- **Top Bar**: Deep Blue container with menu drawer, service zone chip ("Jaipur Central"), and notification bell.
- **Duty Card**: `OnlineDutySwitch` showing live state.
- **Today Overview**: `EarningsCard` highlighting ₹0.00 baseline with 3-column performance metrics.
- **Shortcuts Grid**: 2x2 grid of 16dp rounded cards for Orders, Earnings, Rewards, and Profile.
- **Dispatch Queue**: `EmptyState` card with zero-fake-data assurance.

### S-10: Service Area & Preferences
- **Map Surface**: Subdued neutral map style with semi-transparent Deep Blue operational zone boundary.
- **Preference Controls**: Return-home destination selector and Auto Boost toggle card.

### S-12: Order History
- **Tabs**: Active, Completed, Cancelled.
- **Order Card**: White card with date, order ID, service category icon, pickup/drop summary, and final driver earning in bold green.

### S-13 to S-15: Earnings, Wallet & Payout
- **Earnings Period Switcher**: Segmented button (`Today` / `This Week` / `This Month`).
- **Wallet Hero**: Large Available Balance card (`28sp Black`) with "Withdraw Now" action.
- **Withdrawal Modal**: Form with pre-set quick chips (`₹500`, `₹1000`, `₹2000`, `All`), masked bank account preview, and idempotent confirmation button.

### S-16 & S-17: Rewards & Referrals
- **Reward Cards**: List of active target cards with `LinearProgressIndicator` and expiry countdowns.
- **Referral Hero**: Referral code card with 1-tap Copy and WhatsApp share button.

### S-19: Incoming Offer Overlay
- **Modal Style**: Edge-to-edge high-contrast dispatch sheet.
- **Offer Content**: `OrderRequestCard` with 15-second radial timer, route summary, and guaranteed fare.

### S-20: Active Trip
- **Map View**: Full-screen live navigation map with polyline and driver pin.
- **Trip Stage Action**: Single persistent 56dp action button at bottom (`Navigate` ➔ `Arrived` ➔ `Start Trip` ➔ `Complete Trip`).
- **Customer Bar**: Masked call icon and in-app chat button.
- **Emergency**: Persistent red SOS button in top-right corner.

### S-21: Trip Completion & Fare Receipt
- **Header**: Green checkmark with "Trip Completed Successfully".
- **Payment Callout**: High-contrast amber card ("COLLECT CASH: ₹145.00") or green card ("PAID ONLINE — DO NOT COLLECT CASH").
- **Rating**: 5 interactive stars for passenger feedback.

### S-22: Safety Center & SOS
- **SOS Button**: High-visibility circular red button with 3-second hold countdown to prevent false alarms.
- **Quick Dial**: Direct "Call Police (112)" one-tap button.
- **Insurance Card**: Policy number and on-duty accidental coverage summary.

### S-23 & S-24: Profile, Settings & Help Support
- **Profile Header**: `ProfileCard` with service category, vehicle number, and rating.
- **Settings List**: Grouped white card rows with standard Material icons and right chevrons.
- **Support Screen**: Search bar, quick dispute categories, and ticket history with status chips.

---

## 5. Localization & Cultural Adaptation

- **Languages Supported**: English and हिंदी (Hindi).
- **String Externalization**: 100% of visible strings are stored in `res/values/strings.xml` and `res/values-hi/strings.xml`.
- **Currency Standard**: Indian Rupee (`₹`) formatted with standard Indian grouping (e.g. `₹1,50,000.00`).
- **Date / Time Formatting**: 12-hour format with AM/PM (e.g. `04:30 PM`, `11 Nov 2026`).
- **Font Support**: Android system sans-serif with complete Devanagari Unicode support ensuring no clipped characters or irregular line breaks.

This document serves as the formal design system and screen-level UI specification for all current and future development in the Drive Partner Android application.
