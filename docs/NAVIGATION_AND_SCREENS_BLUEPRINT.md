# Drive Partner — Navigation & Screen Implementation Blueprint

**Document ID:** DP-SPEC-004  
**Version:** 1.0.0  
**Status:** Approved Architecture Baseline  
**Target Platform:** Native Android (Kotlin + Jetpack Compose + Material 3 + Navigation Compose)  
**Package Root:** `com.drivepartner.app`  

---

## 1. Executive Summary & Architecture Foundation

The **Drive Partner** application provides a resilient, safety-first mobile experience for Driver Partners operating across four distinct mobility and logistics categories:
- **Bike Taxi**
- **Auto Rickshaw**
- **Cab (Economy & Premium)**
- **Delivery Partner**

This blueprint bridges product requirements (`PRD.md`), technical architecture (`02_Architecture.md`, `04_TECHSPEC.md`), database & business rules (`05_SCHEMA (1).md`, `06_RULES (1).md`), application flowcharts (`03_APPFLOW.md`), and screen specifications (`11_SCREENS.md`).

### Foundational Architectural Principles
1. **Single Source of Truth**: The PostgreSQL + PostGIS transactional backend strictly owns all state transitions (duty eligibility, order allocation, trip states, fare calculation, ledger entries, and wallet balance). The client app never calculates fares, modifies ledger balances, or generates fake ride requests.
2. **Crash & Process Death Resilience**: An active order offer or active trip is authoritative and server-backed. If the Android OS kills the app process or the driver restarts the device, the app bootstrap sequence queries the backend `/v1/driver/session/restore` and immediately restores the active offer countdown or active trip screen.
3. **Idempotency & Double-Tap Prevention**: All mutating API actions (accept offer, trip transitions, payout requests, SOS trigger) carry unique client-generated idempotency keys (`X-Idempotency-Key: UUID`) and freeze UI controls during dispatch to prevent accidental duplicate actions.
4. **Contextual Permission Architecture**: Android permissions (Fine/Coarse Location, Background Location, Post Notifications, Camera, Phone Call) are requested strictly in context with educational rationale dialogs, never blasted upfront on first launch.
5. **Strict No-Fake-Data Discipline**: Empty states reflect genuine zero-records (e.g., ₹0.00 today earnings, 0 trips completed, empty dispatch queue). No synthetic heatmaps, artificial demand multipliers, or fake orders are ever rendered.

---

## 2. Navigation Architecture & Graph Topology

### 2.1 Navigation Graph Hierarchy
The application uses **Navigation Compose** with nested navigation graphs organized into three primary sub-graphs plus two high-priority modal overlay destinations:

```
RootNavGraph
 ├── [Graph 1: Splash & Auth]
 │    ├── S-01: SplashScreen
 │    ├── S-02: LanguageSelectionScreen
 │    ├── S-03: PhoneLoginScreen
 │    └── S-04: OtpVerificationScreen
 │
 ├── [Graph 2: Onboarding & Verification]
 │    ├── S-05: DriverRegistrationScreen (Personal Details)
 │    ├── S-06: VehicleRegistrationScreen (Category & Vehicle)
 │    ├── S-07: DocumentUploadScreen (DL, RC, Aadhaar, PAN)
 │    └── S-08: VerificationStatusScreen (Under Review / Blocked)
 │
 └── [Graph 3: Main App Shell (Bottom Bar Navigation)]
      ├── S-09: HomeDashboardScreen (Home Tab)
      │    ├── S-10: ServiceAreaScreen (Service Zones & Preferences)
      │    ├── S-11: DutyStatusSheet / Control
      │    ├── S-18: PriorityOrdersScreen
      │    └── S-21: SafetyCenterScreen
      ├── S-12: OrdersHistoryScreen (Orders Tab - Active / Completed / Cancelled)
      ├── S-13: EarningsDashboardScreen (Earnings Tab)
      │    ├── S-14: WalletScreen
      │    └── S-15: WithdrawalsScreen (Payout Request & History)
      ├── S-16: RewardsScreen (Rewards Tab - Incentives & Targets)
      │    └── S-17: ReferAndEarnScreen (Referrals)
      └── S-22: ProfileScreen (Profile Tab)
           ├── S-23: HelpAndSupportScreen (Tickets & FAQs)
           └── S-24: NotificationsScreen (Inbox & Alerts)

 [Priority Overlays / Interceptors]
  ├── S-19: IncomingOfferOverlay (Atomic countdown modal / Full-screen offer)
  └── S-20: ActiveTripScreen (Lockout trip workflow until completion/cancellation)
```

---

## 3. Comprehensive Analysis of All 24 Screens

---

### Screen S-01: Splash Screen
- **Screen ID / Name:** `S-01_Splash` (`SplashScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.auth`
- **Purpose:** Cold-start entry point. Validates app environment integrity, checks stored JWT tokens, queries backend session restore endpoint, and determines routing destination.
- **UI Components & Hierarchy:** Full-screen Box, Centered brand logo vector, App name text, Linear indeterminate progress indicator at bottom, Minimal version footer.
- **Header:** None (edge-to-edge transparent system bars).
- **Main Content:** Clean white canvas (`#FFFFFF`), centered Deep Blue brand logo (`#0D2560`), bold typography "Drive Partner" with subtitle "Driver & Delivery Partner Platform".
- **Buttons / CTAs:** None during normal flow; "Retry Connection" button appears if backend health check fails after 10s timeout.
- **User Interactions:** None during normal loading. Tap retry on timeout.
- **Loading State:** Native Compose progress bar with smooth alpha fade-in.
- **Empty State:** Not applicable.
- **Error State:** Network failure banner with "Unable to reach Drive Partner servers. Check internet." and a Retry CTA.
- **Navigation Destinations:**
  - If no session token: Navigate to `S-02: LanguageSelectionScreen` (clearing splash from backstack: `popUpTo(Splash) { inclusive = true }`).
  - If token exists & session valid:
    - If `profile_incomplete`: Navigate to `S-05: DriverRegistrationScreen`.
    - If `documents_pending`: Navigate to `S-07: DocumentUploadScreen`.
    - If `under_review`: Navigate to `S-08: VerificationStatusScreen`.
    - If `approved`:
      - If active trip exists on server: Navigate directly to `S-20: ActiveTripScreen`.
      - Else: Navigate to `S-09: HomeDashboardScreen`.
- **Backend Dependencies:** `GET /v1/system/config`, `GET /v1/driver/session/restore`.
- **Permission Requirements:** None at this stage.

---

### Screen S-02: Language Selection
- **Screen ID / Name:** `S-02_LanguageSelection` (`LanguageSelectionScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.auth`
- **Purpose:** Allows driver to choose their primary operating language (English or हिंदी), configuring UI string resources and server-side locale preference.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with brand title, Column with Language Option Cards (Radio selection), Floating/Bottom Confirmation Button.
- **Header:** Clean TopAppBar with "Choose Language / भाषा चुनें".
- **Main Content:**
  - Card 1: **English** (Subtitle: "Default language for notifications & navigation").
  - Card 2: **हिंदी (Hindi)** (Subtitle: "नेविगेशन और सूचनाओं के लिए हिंदी").
  - Semantic checkmark icon on selected card.
- **Buttons / CTAs:** Primary button "Continue / आगे बढ़ें" (Enabled when a selection is active).
- **User Interactions:** Tapping language card updates local state and preview; tapping "Continue" saves preference in DataStore and proceeds.
- **Loading State:** Button shows inline circular progress indicator while saving locale preference.
- **Empty State:** Not applicable.
- **Error State:** Snackbar if DataStore write fails.
- **Navigation Destinations:**
  - Forward: `S-03: PhoneLoginScreen`.
  - Back: System exit (app closes since Splash is popped).
- **Backend Dependencies:** Local DataStore cache; synced to backend on first profile sync (`PATCH /v1/driver/preferences`).
- **Permission Requirements:** None.

---

### Screen S-03: Phone Login
- **Screen ID / Name:** `S-03_PhoneLogin` (`PhoneLoginScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.auth`
- **Purpose:** Initiates driver authentication by collecting a 10-digit Indian mobile phone number for OTP generation.
- **UI Components & Hierarchy:** Scaffold, Clean TopAppBar, Header text block, OutlinedTextField with "+91" fixed country code prefix, Terms & Privacy disclaimer text with clickable spans, Full-width Primary Button.
- **Header:** TopAppBar with back arrow and Title "Partner Sign In".
- **Main Content:**
  - Title: "Enter your phone number / अपना मोबाइल नंबर दर्ज करें".
  - Subtitle: "We will send a 6-digit OTP to verify your account."
  - Input: OutlinedTextField with `KeyboardType.Phone`, 10-digit length filter, visual phone icon leading prefix.
  - Legal: "By continuing, you agree to Drive Partner Terms of Service and Privacy Policy."
- **Buttons / CTAs:** Primary Button "Get OTP / ओटीपी प्राप्त करें".
- **User Interactions:** Typing mobile number; auto-validates 10-digit format (rejects numbers starting with < 6); pressing "Get OTP".
- **Loading State:** Button disables and renders CircularProgressIndicator; input field disables.
- **Empty State:** Button disabled when phone number length < 10.
- **Error State:** Helper text in error color: "Please enter a valid 10-digit mobile number" or server error: "Account suspended. Contact support."
- **Navigation Destinations:**
  - Forward: `S-04: OtpVerificationScreen` (passing `phoneNumber` argument).
  - Back: Returns to `S-02: LanguageSelectionScreen`.
- **Backend Dependencies:** `POST /v1/auth/phone/otp/request`.
- **Permission Requirements:** None.

---

### Screen S-04: OTP Verification
- **Screen ID / Name:** `S-04_OtpVerification` (`OtpVerificationScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.auth`
- **Purpose:** Verifies the 6-digit one-time password sent via SMS to authenticate the driver's phone number.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back navigation, Subtitle with masked phone number and "Edit" link, 6-box OTP input field (or combined auto-focusing PinInput), 60-second countdown resend timer, "Resend OTP" clickable text, Primary "Verify & Continue" button.
- **Header:** TopAppBar with back icon and Title "Verify OTP".
- **Main Content:**
  - Text: "Enter the 6-digit code sent to +91 ******{last4}".
  - Edit Number button to pop back to S-03.
  - OTP Input: 6 distinct rounded digit boxes with active border focus.
  - Resend block: "Resend code in 00:54" -> becomes active "Resend OTP" link once timer expires.
- **Buttons / CTAs:**
  - "Verify & Proceed" primary button.
  - "Resend via SMS" secondary link.
- **User Interactions:** Entering 6 digits; auto-submit on 6th digit entry; tapping "Edit Number" pops back to S-03; tapping "Resend" re-triggers request and restarts 60s timer.
- **Loading State:** Modal loading overlay or button spinner during `POST /v1/auth/phone/otp/verify`.
- **Empty State:** Button disabled until all 6 digits are typed.
- **Error State:** Red border on OTP boxes with error message: "Incorrect OTP. 2 attempts remaining." or "OTP expired. Request a new code."
- **Navigation Destinations:**
  - Forward:
    - If new user or profile incomplete: `S-05: DriverRegistrationScreen`.
    - If vehicle missing: `S-06: VehicleRegistrationScreen`.
    - If documents missing: `S-07: DocumentUploadScreen`.
    - If fully registered and verified: `S-09: HomeDashboardScreen` (clearing entire Auth graph).
  - Back: Pops to `S-03: PhoneLoginScreen`.
- **Backend Dependencies:** `POST /v1/auth/phone/otp/verify`, `POST /v1/auth/phone/otp/request` (resend).
- **Permission Requirements:** Optional `SMS_RECEIVE` / Google Play Services SMS Retriever API (consent-based autofill).

---

### Screen S-05: Driver Registration (Personal Details)
- **Screen ID / Name:** `S-05_DriverRegistration` (`DriverRegistrationScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.onboarding`
- **Purpose:** First step of onboarding. Collects verified personal information (Full Legal Name, Date of Birth, Gender, Address, Emergency Contact, Profile Photo).
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Onboarding Step Indicator (Step 1 of 3), Scrollable Form Column, Avatar Picker with Camera/Gallery bottom sheet, Text inputs with validation icons, DatePicker dialog for DOB (must be >= 18 years), Primary "Save & Next" button.
- **Header:** TopAppBar with Title "Driver Profile" and Stepper indicator (1/3).
- **Main Content:**
  - Profile photo capture slot (circular avatar with camera badge).
  - Full Name (as on Driving Licence).
  - Date of Birth (DatePicker enforcing age >= 18).
  - Gender selector (Male / Female / Other chips).
  - Residential Address (Street, City, State, PIN Code).
  - Emergency Contact Name & Phone Number.
- **Buttons / CTAs:** "Continue to Vehicle Details" full-width primary button.
- **User Interactions:** Selecting avatar; filling text fields; picking DOB; clicking continue.
- **Loading State:** Full-screen skeleton while checking for existing draft profile; button progress indicator on submit.
- **Empty State:** Form inputs empty or pre-filled from backend draft if resuming.
- **Error State:** Field-level error messages ("Name must match Driving Licence", "Driver must be at least 18 years old").
- **Navigation Destinations:**
  - Forward: `S-06: VehicleRegistrationScreen`.
  - Back: Confirmation dialog "Do you want to log out or continue registration later?".
- **Backend Dependencies:** `GET /v1/driver/onboarding/draft`, `POST /v1/driver/profile/personal`.
- **Permission Requirements:** `CAMERA`, `READ_MEDIA_IMAGES` (for profile photo capture).

---

### Screen S-06: Vehicle Registration
- **Screen ID / Name:** `S-06_VehicleRegistration` (`VehicleRegistrationScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.onboarding`
- **Purpose:** Onboarding Step 2. Selects service category and captures vehicle details (Make, Model, Year, Registration Number, Commercial/Private permit type).
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Stepper (Step 2 of 3), Category Selector Cards (Bike / Auto / Cab / Delivery), Vehicle specification form fields, Number Plate input field with uppercase transformation, Fuel type chips, Primary "Save & Continue" button.
- **Header:** TopAppBar with Title "Vehicle Information" and Stepper (2/3).
- **Main Content:**
  - Category Selection Cards:
    - 🛵 **Bike Taxi** (2-wheeler, helmet required)
    - 🛺 **Auto Rickshaw** (3-wheeler commercial)
    - 🚗 **Cab** (4-wheeler hatchback/sedan/SUV)
    - 📦 **Delivery Partner** (2-wheeler parcel/food)
  - Vehicle Registration Number (e.g., `RJ-14-EA-2024`).
  - Vehicle Make & Model (e.g., "Bajaj Compact 4S" or "Maruti WagonR").
  - Model Year (dropdown).
  - Vehicle Color.
- **Buttons / CTAs:** "Proceed to Documents" primary button.
- **User Interactions:** Switching category updates vehicle-specific validation; auto-formatting plate text to uppercase; tapping proceed.
- **Loading State:** Spinner on category selection if category availability is queried from server.
- **Empty State:** Default category unselected or restored from server draft.
- **Error State:** "Invalid registration plate format", "Category currently unavailable in your registered city".
- **Navigation Destinations:**
  - Forward: `S-07: DocumentUploadScreen`.
  - Back: Returns to `S-05: DriverRegistrationScreen`.
- **Backend Dependencies:** `GET /v1/service-areas/categories`, `POST /v1/driver/vehicle`.
- **Permission Requirements:** None.

---

### Screen S-07: Document Upload & Verification
- **Screen ID / Name:** `S-07_DocumentUpload` (`DocumentUploadScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.onboarding`
- **Purpose:** Onboarding Step 3. Captures and securely uploads required regulatory documents: Driving Licence (Front & Back), Vehicle RC, Aadhaar / National ID, PAN Card, Vehicle Insurance, and Commercial Permit (for Cab/Auto).
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Stepper (Step 3 of 3), Document Checklist Column with status badges (`Pending`, `Uploaded`, `Rejected`, `Approved`), Document Capture Dialog / Sheet, Image cropping and preview modal, Expiry Date input field, Primary "Submit All for Verification" button.
- **Header:** TopAppBar with Title "Upload Documents" and Stepper (3/3).
- **Main Content:**
  - Mandatory checklist:
    1. **Driving Licence (DL)** — Front & Back + Expiry Date.
    2. **Registration Certificate (RC)** — Front & Back.
    3. **Aadhaar Card / National ID** — Front & Back (Masked Aadhaar guidelines).
    4. **PAN Card** — Front photo + PAN number.
    5. **Vehicle Insurance** — Policy copy + Expiry Date.
    6. **Vehicle Permit & Fitness** (Cab & Auto only).
  - Each item shows thumbnail preview once uploaded, file size, status chip, and "Replace / Re-upload" button.
  - Clear rejection reason banner if a document was previously rejected by back-office reviewer.
- **Buttons / CTAs:** "Submit for Review" (enabled only when all mandatory documents are successfully uploaded).
- **User Interactions:** Tapping document row opens camera/gallery picker; user crops image to clear rectangle; enters expiry date; uploads with real progress percentage; submits batch for review.
- **Loading State:** Per-document upload progress bar with percentage (`Uploading... 68%`).
- **Empty State:** Gray placeholder document outlines before upload.
- **Error State:** "Document blurry or unreadable. Please recapture in bright light." or "Document expired. Upload a valid document."
- **Navigation Destinations:**
  - Forward: `S-08: VerificationStatusScreen`.
  - Back: Pops to `S-06: VehicleRegistrationScreen`.
- **Backend Dependencies:** `POST /v1/documents/upload-url` (presigned S3/GCS secure upload), `POST /v1/documents/submit-batch`.
- **Permission Requirements:** `CAMERA`, `READ_MEDIA_IMAGES`.

---

### Screen S-08: Verification Status (Pending / Blocked)
- **Screen ID / Name:** `S-08_VerificationStatus` (`VerificationStatusScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.onboarding`
- **Purpose:** Informs driver of their account review status. If documents are under review, displays progress. If rejected or account blocked, displays specific feedback and re-upload actions.
- **UI Components & Hierarchy:** Scaffold, Centered status illustration/icon card, Status Title & Description block, Document checklist status summary with action buttons, "Contact Support" button, "Refresh Status" pull-to-refresh.
- **Header:** TopAppBar with Title "Account Verification".
- **Main Content:**
  - State A (Under Review): Clock icon in Deep Blue, Title "Verification in Progress", Text "Our team is verifying your submitted documents. This typically takes 24–48 hours."
  - State B (Action Required / Rejection): Warning icon in Amber, Title "Action Required on Documents", list of rejected documents with reviewer notes (e.g., "RC photo is blurry") and direct "Re-upload DL" CTA.
  - State C (Suspended / Blocked): Red Shield Alert, Title "Account Suspended", clear explanation and dedicated Support button.
- **Buttons / CTAs:**
  - "Update Documents" (State B).
  - "Contact Support" (All states).
  - "Refresh Status" button.
- **User Interactions:** Pull-to-refresh queries backend; tapping rejected document navigates directly to re-upload flow.
- **Loading State:** CircularProgressIndicator while polling status.
- **Empty State:** Not applicable.
- **Error State:** Banner if status refresh fails due to network.
- **Navigation Destinations:**
  - If approved on refresh: Navigate to `S-09: HomeDashboardScreen` (clearing Onboarding graph).
  - If rejected items tapped: Navigate to `S-07: DocumentUploadScreen`.
  - Support CTA: Navigate to `S-23: HelpAndSupportScreen`.
- **Backend Dependencies:** `GET /v1/driver/verification-status`.
- **Permission Requirements:** None.

---

### Screen S-09: Home Dashboard
- **Screen ID / Name:** `S-09_HomeDashboard` (`HomeScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.home`
- **Purpose:** Primary driver operational cockpit. Displays verified partner profile, duty status switch, today's real earnings, key performance metrics, quick navigation shortcuts, and active dispatch queue.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with drawer hamburger menu, city/service area tag, and notification bell with unread badge; Vertical scrollable column containing:
  1. `PartnerHeaderCard`: Avatar, name, service category badge (`Bike`/`Auto`/`Cab`/`Delivery`), vehicle registration, verified badge.
  2. `DutyStatusCard`: Online/Offline indicator, toggle button (`Go Online` / `Go Offline`), status description.
  3. `TodaySummaryCard`: Prominent yellow earnings highlight (`₹0.00` baseline), online hours (`0.0h`), completed trips (`0`), acceptance rate (`100%`).
  4. `NavigationShortcutsSection`: 4 OutlinedCards for Orders, Earnings, Rewards, and Profile.
  5. `DispatchQueueCard`: Atomic queue state explaining live dispatch availability.
- **Header:** TopAppBar with Deep Blue background, Drawer toggle, Service Zone label ("Jaipur Central"), and Notification Bell with unread counter.
- **Main Content:** Modular Material 3 cards organized with clean white cards and rounded corners (16dp).
- **Buttons / CTAs:**
  - "Go Online / Go Offline" duty toggle button.
  - Quick navigation shortcut cards.
  - Recenter / Service Zone shortcut.
- **User Interactions:** Toggling duty switch triggers location permission check and backend online request; tapping shortcut cards switches tabs or launches child screens.
- **Loading State:** Shimmer placeholders for earnings strip when fetching live metrics.
- **Empty State:** Clean zero-baseline state (`₹0.00`, 0 trips, "No active orders. Go online to start receiving ride requests.").
- **Error State:** Location disabled warning bar; duty toggle error snackbar.
- **Navigation Destinations:**
  - Bottom Bar tabs: Orders, Earnings, Rewards, Profile.
  - Shortcuts: Service Area (`S-10`), Safety Center (`S-21`), Notifications (`S-24`), Active Orders (`S-12`).
- **Backend Dependencies:** `GET /v1/driver/dashboard/today`, `POST /v1/driver/duty/toggle`, WebSocket/FCM topic `driver/{id}/offers`.
- **Permission Requirements:** `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `POST_NOTIFICATIONS`, `ACCESS_BACKGROUND_LOCATION` (duty tracking).

---

### Screen S-10: Service Area & Preferences
- **Screen ID / Name:** `S-10_ServiceArea` (`ServiceAreaScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.home`
- **Purpose:** Allows driver to view assigned operating zones, permitted municipal boundaries, configure return-trip / home-route preferences, and toggle Auto Boost (where authorized).
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back arrow, Interactive Map showing service area polygons, Zone checklist with active switches, Return-home destination address picker, Auto Boost toggle card with policy explanation.
- **Header:** TopAppBar with Title "Service Area & Preferences".
- **Main Content:**
  - Map View: Displays approved operational boundary (e.g., Jaipur Urban Region) and active sub-zones (Malviya Nagar, Mansarovar, Vaishali Nagar, Pink City).
  - Zone Selection List: Checkboxes for allowed operating sub-clusters.
  - "My Destination" / "Home Trip" preference mode: Sets a target drop-off area so dispatch prioritizes trips along the driver's homeward path (limited to 2 per day per business rules).
  - Auto Boost (Back-to-Back Rides): Information card explaining continuous dispatch algorithm with enable/disable switch.
- **Buttons / CTAs:** "Save Preferences" primary bottom button.
- **User Interactions:** Tapping map zones to view details; selecting home destination; toggling return trip mode; saving preferences.
- **Loading State:** Map loading skeleton with circular indicator.
- **Empty State:** "No restricted zones in your operating cluster."
- **Error State:** "Selected destination is outside permitted operating boundary."
- **Navigation Destinations:** Pops back to `S-09: HomeDashboardScreen`.
- **Backend Dependencies:** `GET /v1/service-areas`, `POST /v1/driver/preferences/zones`, `POST /v1/driver/preferences/home-route`.
- **Permission Requirements:** `ACCESS_FINE_LOCATION`.

---

### Screen S-11: Online/Offline Duty Control
- **Screen ID / Name:** `S-11_DutyControl` (`DutyStatusSheet.kt` & Home integration)
- **Package:** `com.drivepartner.app.ui.screens.home`
- **Purpose:** Controls driver availability state with strict multi-layer verification (Authentication, Document approval, Battery status, GPS accuracy, and Backend confirmation).
- **UI Components & Hierarchy:** ModalBottomSheet or dedicated Card state; Duty switch with animated slider, Checklist verification dialog for GPS/Location/Permissions, Offline confirmation dialog if active tasks exist.
- **Header:** BottomSheet drag handle with Title "Duty Status".
- **Main Content:**
  - Readiness Pre-Flight Checklist:
    - [x] Account Approved & Active
    - [x] Vehicle Insurance Valid
    - [x] High-Accuracy GPS Enabled
    - [x] Location Permission Granted
    - [x] Floating Overlay / Foreground Service Authorized
  - Battery optimization warning banner (recommending disabling battery saver so tracking isn't killed by OS).
- **Buttons / CTAs:**
  - "Confirm Go Online" (Green primary button).
  - "Confirm Go Offline" (Neutral gray button with reason selector: "Taking a break", "End of shift", "Vehicle issue").
- **User Interactions:** Sliding or pressing toggle triggers pre-flight check; if any item fails, presents specific resolution action (e.g., "Open Location Settings").
- **Loading State:** "Connecting to dispatch server..." with spinner. Duty button disabled during state transition.
- **Empty State:** Not applicable.
- **Error State:** "Cannot go online: High-accuracy GPS is disabled. Please turn on Device Location." or "Duty request rejected by server: Document expired."
- **Navigation Destinations:** Dismisses sheet on success and updates Home dashboard state.
- **Backend Dependencies:** `POST /v1/driver/duty/online`, `POST /v1/driver/duty/offline`.
- **Permission Requirements:** `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`.

---

### Screen S-12: Order History (Orders Tab)
- **Screen ID / Name:** `S-12_OrdersHistory` (`OrdersScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.orders`
- **Purpose:** Displays driver's past and current order records categorized by status: All, Active, Completed, and Cancelled. Provides itemized trip receipts and support dispute links.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Title "Orders", Primary TabRow (`Active`, `Completed`, `Cancelled`), LazyColumn with `OrderItemCard`s, Date Range filter chip bar, Pull-to-refresh indicator.
- **Header:** TopAppBar with Title "Orders" and Filter icon.
- **Main Content:**
  - Tab 1: **Active** (Shows currently assigned or in-progress trip, or empty queue).
  - Tab 2: **Completed** (Chronological cards showing Date/Time, Order ID, Service Type badge, Pickup/Drop addresses, Trip Distance/Duration, and Final Driver Earning in green).
  - Tab 3: **Cancelled** (Cancelled orders showing who cancelled — Customer / Driver / System — cancellation reason, and any cancellation fee credited).
- **Buttons / CTAs:**
  - "View Order Details" on card click.
  - "Help with this Order" button inside detail.
- **User Interactions:** Switching tabs; filtering by date; clicking card opens detailed bottom sheet/screen; pull-to-refresh updates list.
- **Loading State:** Shimmering placeholder cards during network pagination.
- **Empty State:** `EmptyState` component with Inbox icon and text: "No completed orders yet. Complete your first ride to view records here."
- **Error State:** `ErrorState` with "Failed to load orders" and Retry button.
- **Navigation Destinations:**
  - Tapping card opens Order Detail dialog or navigates to `S-23: HelpAndSupportScreen` with pre-filled Order ID.
- **Backend Dependencies:** `GET /v1/driver/orders?status={tab}&page={page}&limit=20`.
- **Permission Requirements:** None.

---

### Screen S-13: Earnings Dashboard (Earnings Tab)
- **Screen ID / Name:** `S-13_EarningsDashboard` (`EarningsScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.earnings`
- **Purpose:** Comprehensive financial overview. Breaks down gross earnings, platform commission, net driver earnings, customer tips, surge incentives, and deductions over Today, Weekly, and Monthly cycles.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Title "Earnings" and Wallet quick-action button, Time Period Segmented Button (`Today` / `This Week` / `This Month`), Total Earning Hero Card (Yellow background), Weekly Bar Chart / Breakdown Component, Line-item breakdown card, Navigation link to Wallet & Payouts.
- **Header:** TopAppBar with Title "Earnings" and Wallet icon with current balance badge.
- **Main Content:**
  - Hero Card: Big bold Net Earnings (`₹1,420.00`), Subtext "Updated just now".
  - Breakdown Card:
    - Base Fare: `₹1,150.00`
    - Surge / Demand Bonus: `₹180.00`
    - Completed Trip Incentives: `₹90.00`
    - Customer Tips: `₹50.00`
    - Tolls & Parking Reimbursed: `₹40.00`
    - Platform Service Fee / Deductions: `-₹90.00`
  - Performance Metrics: Total Online Hours (`7.2h`), Completed Orders (`11`), Average Per Hour (`₹197/h`).
- **Buttons / CTAs:**
  - "Go to Wallet & Withdraw" primary CTA button.
  - Period selector buttons.
- **User Interactions:** Selecting time filters; expanding line-item fee explanations; tapping Wallet button.
- **Loading State:** Shimmering cards for currency amounts and charts.
- **Empty State:** Zero earnings state (`₹0.00`) with clear explanation: "No earnings recorded for this period."
- **Error State:** Banner with "Unable to fetch ledger data. Please try again."
- **Navigation Destinations:**
  - Forward: `S-14: WalletScreen`, `S-15: WithdrawalsScreen`.
- **Backend Dependencies:** `GET /v1/driver/earnings/summary?period={today|week|month}`.
- **Permission Requirements:** None.

---

### Screen S-14: Wallet Screen
- **Screen ID / Name:** `S-14_Wallet` (`WalletScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.earnings`
- **Purpose:** Real-time ledger view of driver's funds. Shows Available Balance (withdrawable), Pending Clearance, and Security Minimum. Displays linked bank account / UPI ID with masking and transaction ledger history.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back arrow and Title "Partner Wallet", Wallet Balance Hero Card, Linked Payout Account Card with "Manage Account" link, Quick "Withdraw Funds" button, Transaction Ledger LazyColumn with credit/debit badges.
- **Header:** TopAppBar with back arrow and Title "Wallet".
- **Main Content:**
  - Balance Card:
    - Available to Withdraw: `₹2,450.00`
    - In-Transit / Processing: `₹0.00`
    - Reserve / Security Balance: `₹200.00`
  - Linked Payout Method: Masked Bank Account `State Bank of India (•••4012)` or `driver@upi` with Verified green checkmark.
  - Recent Ledger Transactions:
    - Trip Fare Credit (+₹142.00)
    - Weekly Milestone Incentive (+₹300.00)
    - Payout to Bank (-₹2,000.00)
    - Platform Adjustment (-₹15.00)
- **Buttons / CTAs:**
  - "Withdraw Now" primary button (Enabled only if Available Balance >= Minimum Withdrawal Limit, e.g., ₹100).
  - "Manage Bank Account / UPI" secondary button.
- **User Interactions:** Clicking Withdraw launches `S-15`; clicking transaction row opens detailed ledger audit receipt.
- **Loading State:** Circular loader on balance fetch; pull-to-refresh indicator.
- **Empty State:** "No wallet transactions yet."
- **Error State:** Error banner if ledger sync fails.
- **Navigation Destinations:**
  - Forward: `S-15: WithdrawalsScreen`.
  - Back: Returns to `S-13: EarningsDashboardScreen`.
- **Backend Dependencies:** `GET /v1/driver/wallet/balance`, `GET /v1/driver/wallet/ledger?limit=30`.
- **Permission Requirements:** None.

---

### Screen S-15: Withdrawals & Payout
- **Screen ID / Name:** `S-15_Withdrawals` (`WithdrawalsScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.earnings`
- **Purpose:** Secure payout initiation screen. Driver enters amount to withdraw, verifies masked payout destination, checks payout limits and deduction fees (if instant transfer), and submits with idempotent confirmation.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back arrow and Title "Withdraw Funds", Balance display strip, Amount input field with quick chips (`₹500`, `₹1000`, `₹2000`, `All`), Selected Bank Account confirmation card, Transfer Mode selector (`Standard 24-hr NEFT - Free` vs `Instant IMPS/UPI - ₹5 fee`), Idempotent "Confirm Payout" button with biometric/PIN confirmation dialog.
- **Header:** TopAppBar with Title "Withdraw Funds".
- **Main Content:**
  - "Available for Payout: ₹2,450.00".
  - Amount Input (OutlinedTextField with ₹ prefix).
  - Quick amount suggestion chips.
  - Payout Summary:
    - Withdrawal Amount: `₹1,000.00`
    - Transfer Fee: `₹0.00` (Standard)
    - Net Credited to Bank: `₹1,000.00`
    - Estimated Deposit: "Within 24 business hours"
- **Buttons / CTAs:** "Confirm & Transfer" full-width button.
- **User Interactions:** Entering amount; tapping chips; confirming transfer dialog; receiving payout submission receipt.
- **Loading State:** Button disables with spinner and text "Processing Payout with Banking Partner...".
- **Empty State:** Not applicable.
- **Error State:** "Amount exceeds available balance", "Amount is less than minimum withdrawal of ₹100", "Bank verification pending. Payout temporarily locked."
- **Navigation Destinations:**
  - On Success: Replaces with Payout Receipt bottom sheet and refreshes Wallet (`S-14`).
  - Back: Returns to `S-14: WalletScreen`.
- **Backend Dependencies:** `POST /v1/driver/wallet/withdraw` (with `X-Idempotency-Key`).
- **Permission Requirements:** None.

---

### Screen S-16: Rewards & Incentives (Rewards Tab)
- **Screen ID / Name:** `S-16_Rewards` (`RewardsScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.rewards`
- **Purpose:** Gamification and incentive hub. Displays active daily and weekly incentive targets, completed tiers, reward amounts, qualification rules, and expiry countdowns.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Title "Rewards & Incentives" and Referral shortcut, Daily / Weekly tabs, Active Target Cards with LinearProgressIndicator, Milestone Badge tracker, Terms & Conditions collapsible view.
- **Header:** TopAppBar with Title "Rewards & Targets" and "Refer & Earn" icon button.
- **Main Content:**
  - Daily Milestone Card:
    - "Complete 10 Trips Today — Earn Extra ₹250"
    - Progress Bar: `7 / 10 Trips Completed` (70%)
    - Time Remaining: `Ends at 11:59 PM (4h 12m left)`
    - Status: `In Progress`
  - Peak Hours Bonus:
    - "Complete 3 rides between 5 PM – 9 PM — Extra ₹100"
    - Progress: `3 / 3 Completed` -> `Qualified (₹100 added to Wallet)`
  - Quality Metrics Checklist:
    - Acceptance Rate: `94%` (Target: >= 85%)
    - Completion Rate: `98%` (Target: >= 90%)
- **Buttons / CTAs:**
  - "View Full Terms & Criteria" button.
  - "Refer a Friend" promotional banner CTA.
- **User Interactions:** Switching Daily/Weekly tabs; expanding rule criteria; tapping referral banner.
- **Loading State:** Card skeletons while loading campaign targets.
- **Empty State:** "No active incentive campaigns available for your category today."
- **Error State:** "Unable to load rewards. Check internet connection."
- **Navigation Destinations:**
  - Forward: `S-17: ReferAndEarnScreen`.
  - Bottom Bar tabs.
- **Backend Dependencies:** `GET /v1/driver/rewards/campaigns`.
- **Permission Requirements:** None.

---

### Screen S-17: Refer and Earn
- **Screen ID / Name:** `S-17_ReferAndEarn` (`ReferAndEarnScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.rewards`
- **Purpose:** Driver referral program. Generates driver's unique referral code and deep link, explains reward rules (e.g., "Earn ₹500 when your referred friend completes 25 trips in 14 days"), and tracks referral pipeline status.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back arrow and Title "Refer & Earn", Referral code display card with "Copy" and "Share" buttons, Program Benefits steps infographic, Referral History list with statuses (`Registered`, `Active`, `Completed`, `Expired`).
- **Header:** TopAppBar with Title "Refer & Earn".
- **Main Content:**
  - Hero Graphic & Title: "Invite Friends, Earn ₹500 Per Partner".
  - Driver Referral Code Card: Bold text `DRIVE7842` with 1-tap Copy icon.
  - Share Buttons: Native Android Sharesheet trigger (`WhatsApp`, `SMS`, `Telegram`).
  - How it Works:
    1. Share your link with fellow drivers.
    2. Friend registers and uploads verified documents.
    3. Friend completes 25 rides within 14 days.
    4. You both receive ₹500 direct in your wallet!
  - Referral Tracker:
    - Suresh Kumar (`Joined 3 days ago • 14/25 trips completed • In Progress`)
    - Mohan Lal (`Completed • ₹500 Paid on 04 Nov`)
- **Buttons / CTAs:**
  - "Share via WhatsApp" (Green primary button).
  - "Share via Other Apps" (Outlined button).
  - "Copy Code" icon button.
- **User Interactions:** Copying code to clipboard (shows toast "Referral code copied!"); opening Android Sharesheet.
- **Loading State:** Progress bar during referral code generation.
- **Empty State:** "No referrals yet. Share your code to earn bonuses!"
- **Error State:** Banner if referral API fails.
- **Navigation Destinations:** Pops back to `S-16: RewardsScreen`.
- **Backend Dependencies:** `GET /v1/driver/referrals/summary`, `GET /v1/driver/referrals/code`.
- **Permission Requirements:** None.

---

### Screen S-18: Priority Orders
- **Screen ID / Name:** `S-18_PriorityOrders` (`PriorityOrdersScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.home`
- **Purpose:** Displays specialized high-value or scheduled orders (Airport drops, Outstation inter-city rides, Long-distance deliveries, VIP requests) that carry higher guaranteed fares and priority allocation.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Title "Priority Orders", Info banner explaining allocation eligibility criteria (Rating >= 4.85, Acceptance >= 90%), List of available priority dispatch opportunities with countdowns and badges.
- **Header:** TopAppBar with Title "Priority Rides & Deliveries".
- **Main Content:**
  - Tier Eligibility Card: "You are currently eligible for Priority Dispatch (Rating: 4.92 ★)".
  - Priority Order Card:
    - Badge: `Airport Special` / `Long Distance`
    - Estimated Earning: `₹680.00` (includes ₹120 priority bonus)
    - Pickup: "C-Scheme, Jaipur" (4.2 km away)
    - Drop: "Jaipur International Airport (JAI)" (16.8 km)
    - Pickup Time: "Today at 04:30 PM (Scheduled)"
- **Buttons / CTAs:**
  - "Accept Priority Ride" button.
  - "Eligibility Criteria" info link.
- **User Interactions:** Reviewing route details; accepting scheduled ride (locks slot into active schedule).
- **Loading State:** Card shimmers.
- **Empty State:** "No priority orders available right now. Priority orders are dispatched based on real-time customer bookings."
- **Error State:** "This priority order was already assigned to another driver."
- **Navigation Destinations:**
  - On acceptance: Navigates to `S-20: ActiveTripScreen` or Schedule confirmation.
  - Back: Returns to `S-09: HomeDashboardScreen`.
- **Backend Dependencies:** `GET /v1/driver/orders/priority`, `POST /v1/driver/orders/priority/accept`.
- **Permission Requirements:** `ACCESS_FINE_LOCATION`.

---

### Screen S-19: Live Order Requests & Offer Acceptance
- **Screen ID / Name:** `S-19_IncomingOffer` (`IncomingOfferOverlay.kt` / `OfferScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.dispatch`
- **Purpose:** Full-screen atomic incoming ride/delivery offer modal. Presents trip category, pickup distance, drop location, estimated trip duration/distance, guaranteed earning, customer rating, and a strict 15-second server-synchronized countdown timer.
- **UI Components & Hierarchy:** Full-screen dialog / Scaffold overlay, Animated radial countdown progress bar (15s to 0s), Service Type header badge, Route summary card with pickup/drop pins, Fare estimate highlight (Big green/yellow currency), Action buttons: Accept (Full-width swipe or high-emphasis button) and Decline (Secondary button).
- **Header:** Category banner (e.g., "🛺 AUTO RICKSHAW REQUEST" or "🛵 BIKE TAXI") with audio alert tone.
- **Main Content:**
  - Radial Countdown Timer: `15s` counting down with audible ticking chime.
  - Guaranteed Earning: `₹145.00` (with badge "Includes ₹25 Demand Surge").
  - Pickup Location: "Near Gaurav Tower, Malviya Nagar" (`1.2 km away • 4 mins`).
  - Drop Location: "Sindhi Camp Bus Stand" (`11.4 km • 28 mins`).
  - Customer Info: Rating `4.8 ★` • Payment mode `Online / UPI Pre-paid`.
- **Buttons / CTAs:**
  - **"ACCEPT RIDE"** (Green high-emphasis button with ripple or Slide-to-Accept).
  - **"Decline"** (Subtle outlined button with small cross icon).
- **User Interactions:**
  - Tapping Accept: Immediately freezes controls, sends `POST /v1/dispatch/offers/{id}/accept` with idempotency token.
  - Tapping Decline: Sends `POST /v1/dispatch/offers/{id}/reject` and closes overlay.
  - Timer reaches 0s: Automatically closes overlay, marks offer `Expired`, restores Home dashboard.
- **Loading State:** Button shows "Accepting..." spinner while server confirms atomic assignment.
- **Empty State:** Automatically dismissed if offer is withdrawn by backend before driver responds.
- **Error State:** "Offer expired or already assigned to another driver." (Toast notification, overlay dismisses safely).
- **Navigation Destinations:**
  - If accepted & server confirmed: Navigates immediately to `S-20: ActiveTripScreen` (clearing backstack to prevent back-navigation to offer).
  - If declined / expired: Dismisses overlay, returns to `S-09: HomeDashboardScreen`.
- **Backend Dependencies:** `POST /v1/dispatch/offers/{id}/accept`, `POST /v1/dispatch/offers/{id}/reject`.
- **Permission Requirements:** `VIBRATE`, Audio playback for dispatch ringtone, `WAKE_LOCK`.

---

### Screen S-20: Active Trip Execution
- **Screen ID / Name:** `S-20_ActiveTrip` (`ActiveTripScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.trip`
- **Purpose:** Complete state-machine trip execution screen. Guides driver through all sequential stages: (1) `Assigned` -> (2) `En-Route to Pickup` -> (3) `Arrived at Pickup` -> (4) `Trip Started / In-Progress` -> (5) `Completed`.
- **UI Components & Hierarchy:** Scaffold, Persistent Map with live route polyline and driver vehicle pin, Top Floating Header with Trip Stage badge and Turn-by-Turn prompt, Customer Contact Bar (masked call & in-app chat), Bottom Action Sheet with single prominent stage-transition button, OTP input modal (for starting ride), Cancel ride bottom sheet (with strict regulatory reason selection), SOS Emergency floating button.
- **Header:** Stage Header:
  - "Heading to Pickup (1.2 km)"
  - "Arrived at Pickup — Wait for Rider"
  - "On Trip to Drop (11.4 km)"
- **Main Content:**
  - Full-screen Interactive Google Map with live GPS tracking.
  - Customer Card: First Name, Masked Phone Call icon (via backend proxy number), In-app Chat icon.
  - Pickup/Drop address details with "Open in Google Maps / Navigation" intent button.
  - Start Trip OTP field: 4-digit code provided by rider to ensure correct pickup.
- **Buttons / CTAs:**
  - Stage 1: **"Navigate to Pickup"** (Opens external or embedded turn-by-turn navigation).
  - Stage 2: **"I Have Arrived"** (Sends arrival confirmation; notifies rider).
  - Stage 3: **"Start Trip"** (Opens 4-digit start OTP dialog; verifies with backend).
  - Stage 4: **"Complete Trip"** (Triggers drop confirmation and fare calculation).
  - Emergency: **"SOS"** persistent red shield button.
  - Cancellation: "Cancel Ride" (Only available before trip start; requires reason).
- **User Interactions:** Advancing trip stages; entering rider's 4-digit start PIN; opening navigation; triggering SOS.
- **Loading State:** Button progress spinner during state transitions (`Confirming Arrival...`, `Starting Trip...`, `Calculating Fare...`).
- **Empty State:** Not applicable.
- **Error State:** "Invalid start OTP. Please verify with customer." or "Network lost. Action queued — do not restart app."
- **Navigation Destinations:**
  - On Complete Trip: Transitions to `S-21: TripCompletionReceiptScreen`.
  - On Cancelled: Pops to `S-09: HomeDashboardScreen` with cancellation explanation.
  - On SOS tap: Opens `S-21: SafetyCenterScreen` with active emergency context.
- **Backend Dependencies:**
  - `POST /v1/trips/{id}/arrived`
  - `POST /v1/trips/{id}/start` (with `start_otp`)
  - `POST /v1/trips/{id}/complete` (with final coordinates)
  - `POST /v1/trips/{id}/cancel` (with reason)
- **Permission Requirements:** `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `CALL_PHONE` (masked call intent).

---

### Screen S-21: Trip Completion & Fare Receipt
- **Screen ID / Name:** `S-21_TripReceipt` (`TripCompletionScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.trip`
- **Purpose:** Post-trip reconciliation. Displays itemized fare receipt, payment collection method (`Cash Collected` vs `Online Pre-paid`), final net driver earnings, customer rating prompt, and confirmation to return to duty.
- **UI Components & Hierarchy:** Scaffold, Success animation header (Green checkmark), Payment Collection Alert (e.g., "Collect Cash: ₹145.00" or "Pre-paid Online: ₹0.00 to collect"), Itemized Fare Breakdown card, Rider rating star bar (1 to 5 stars), Primary "Confirm & Ready for Next Ride" button.
- **Header:** Clean header with "Trip Completed Successfully".
- **Main Content:**
  - Payment Action Notice:
    - If Cash: High-contrast amber card: **"COLLECT CASH FROM CUSTOMER: ₹145.00"** with checkbox "Cash Received".
    - If Online: Green card: **"PAID ONLINE — DO NOT COLLECT CASH"**.
  - Final Fare Breakdown:
    - Base Distance Fare: `₹120.00`
    - Surge / Bonus: `₹25.00`
    - Platform Fee Deducted: `-₹15.00`
    - **Your Net Earning: ₹130.00**
  - Rider Rating: 5 interactive stars with optional tags ("Polite", "On Time", "Clean").
- **Buttons / CTAs:**
  - "Collect Cash & Finish" / "Complete & Go Online" primary button.
  - "Report Issue with Fare" text button.
- **User Interactions:** Confirming cash collection; selecting star rating; pressing complete.
- **Loading State:** Spinner while registering cash payment confirmation.
- **Empty State:** Not applicable.
- **Error State:** Warning if driver tries to dismiss without confirming cash collection.
- **Navigation Destinations:**
  - Returns to `S-09: HomeDashboardScreen` with duty state online and updated today summary.
- **Backend Dependencies:** `POST /v1/trips/{id}/payment-confirm`, `POST /v1/trips/{id}/rate-customer`.
- **Permission Requirements:** None.

---

### Screen S-22: Safety Center & SOS
- **Screen ID / Name:** `S-22_SafetyCenter` (`SafetyCenterScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.safety`
- **Purpose:** Critical partner safety hub. Provides one-touch SOS emergency alert dispatch, police helpline (112) quick dial, emergency contact sharing, live location sharing link, insurance policy verification, and incident reporting.
- **UI Components & Hierarchy:** Scaffold, TopAppBar in Red/Deep Blue with Title "Safety Center", Emergency SOS Hold-to-Activate button (3-second press to prevent accidental triggers), Emergency contacts list with Add/Edit actions, Police 112 direct call card, Active Insurance Policy overview card, "Report a Safety Incident" form link.
- **Header:** TopAppBar with Title "Safety & Emergency".
- **Main Content:**
  - SOS Card: Red container with prominent circular button: "PRESS & HOLD FOR 3 SECONDS FOR EMERGENCY SOS".
  - Explanation: "Triggers immediate alert to Drive Partner Safety Response Team with your live GPS location and vehicle coordinates."
  - Direct Authorities Dial: "Call Police (112)" one-tap dial button.
  - Emergency Contacts: Displays configured family/friend contacts with "Share My Trip" SMS switch.
  - Partner Insurance: Verified policy coverage summary (Accidental coverage up to ₹5,00,000 for on-duty partners).
- **Buttons / CTAs:**
  - "Press & Hold SOS" button (with countdown animation 3.. 2.. 1..).
  - "Call 112" button.
  - "Report Safety Incident" button.
- **User Interactions:** Holding SOS button for 3 seconds dispatches emergency event to backend and displays active response screen; tapping 112 initiates phone dialer.
- **Loading State:** "Sending Emergency Alert..." overlay.
- **Empty State:** "No emergency contacts added yet. Add at least one contact for safety."
- **Error State:** If network is unavailable when SOS is pressed: Displays offline fallback screen with direct emergency dialer numbers and confirms SMS dispatch fallback.
- **Navigation Destinations:**
  - On SOS triggered: Transitions to `ActiveEmergencyScreen` until resolved.
  - Back: Returns to previous screen (Home or Active Trip).
- **Backend Dependencies:** `POST /v1/safety/sos/trigger` (with live lat/long and active trip ID), `GET /v1/safety/contacts`.
- **Permission Requirements:** `ACCESS_FINE_LOCATION`, `CALL_PHONE`, `SEND_SMS` (emergency fallback).

---

### Screen S-23: Profile & Settings (Profile Tab)
- **Screen ID / Name:** `S-23_Profile` (`ProfileScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.profile`
- **Purpose:** Partner account management. Displays driver identity, rating, document status overview, vehicle details, service category switcher, app language toggle, dark/light theme options, notification preferences, legal policies, and logout.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with Title "Profile & Settings", Driver Profile Header (Avatar, Name, ID, Overall Rating, Total Trips), Service Category Card (showing active category and status), Grouped Settings List with icons and arrows:
  1. *Documents & Verification* (`S-07`)
  2. *Vehicle Details* (`S-06`)
  3. *App Language* (`S-02`)
  4. *Safety Center* (`S-22`)
  5. *Help & Support* (`S-24`)
  6. *Terms of Service & Privacy Policy*
  7. *Logout Action* (with confirmation dialog)
- **Header:** TopAppBar with Title "Profile".
- **Main Content:** Clean grouped Material 3 cards with standard icons and clear typography.
- **Buttons / CTAs:**
  - "Edit Profile" button.
  - Settings row items with chevron right.
  - "Log Out" destructive button at bottom.
- **User Interactions:** Tapping menu rows navigates to respective destinations; toggling language; confirming logout.
- **Loading State:** Skeleton loader while fetching partner profile.
- **Empty State:** Not applicable.
- **Error State:** Banner if profile refresh fails.
- **Navigation Destinations:**
  - Forward: Document Upload (`S-07`), Help & Support (`S-24`), Language (`S-02`).
  - On Logout: Clears local session, stops background duty location service, and navigates to `S-03: PhoneLoginScreen`.
- **Backend Dependencies:** `GET /v1/driver/profile`, `POST /v1/auth/logout`.
- **Permission Requirements:** None.

---

### Screen S-24: Help & Support (Support Tickets & FAQs)
- **Screen ID / Name:** `S-24_HelpAndSupport` (`HelpAndSupportScreen.kt`)
- **Package:** `com.drivepartner.app.ui.screens.support`
- **Purpose:** Customer service and issue resolution. Driver can browse categorized FAQs, view existing support tickets, raise a new ticket (e.g., Fare Dispute, Customer Behavior, Vehicle Breakdown, Payment Delay), attach photo evidence, and chat with support agents.
- **UI Components & Hierarchy:** Scaffold, TopAppBar with back arrow and Title "Help & Support", TabRow (`FAQs & Topics` / `My Tickets`), Search bar for help articles, Common Issue Quick Tiles ("Fare not credited", "Customer cancelled ride", "App GPS issue"), Active Tickets LazyColumn with status chips (`Open`, `In Progress`, `Resolved`), Floating "Raise New Ticket" button.
- **Header:** TopAppBar with Title "Help & Support".
- **Main Content:**
  - Quick Issue Categories:
    - 💰 Fare & Wallet Issues
    - 🚗 Vehicle & Category Issues
    - ⭐ Rating & Penalty Disputes
    - 🛡️ Safety & Incident Reports
  - My Tickets Tab:
    - Ticket `#TK-9021`: "Fare under-calculated on Order #ORD-4412" — Status: `In Review` (Updated 2 hours ago).
    - Ticket `#TK-8810`: "Document re-upload inquiry" — Status: `Resolved`.
- **Buttons / CTAs:**
  - "Raise a Ticket" floating action button.
  - "Call Partner Helpline" direct dialer button.
  - "Submit Ticket" inside creation dialog.
- **User Interactions:** Searching help articles; opening ticket detail to view agent responses; submitting new dispute with photo attachment.
- **Loading State:** Shimmer list during ticket sync.
- **Empty State:** "No active support tickets. Need help? Raise a ticket above."
- **Error State:** "Failed to submit ticket. Please check connection and try again."
- **Navigation Destinations:**
  - Opens Ticket Detail chat screen; pops back to previous screen.
- **Backend Dependencies:** `GET /v1/support/tickets`, `POST /v1/support/tickets/create`, `GET /v1/support/faqs`.
- **Permission Requirements:** `CAMERA`, `READ_MEDIA_IMAGES` (for ticket screenshot attachments).

---

## 4. Navigation Inconsistencies & Conflict Resolutions

During analysis of `03_APPFLOW.md`, `11_SCREENS.md`, and `06_RULES (1).md`, five critical navigation conflicts were identified and resolved:

| ID | Potential Inconsistency | Root Cause in Specs | Architectural Resolution & Rule |
|---|---|---|---|
| **C-01** | Back navigation during an **Active Offer (S-19)** | If the driver presses the Android system back button while a 15s offer is ringing, does the offer reject? | **Resolution:** Intercept back press with `BackHandler`. Back press does **not** decline the offer; the offer countdown remains active until explicit "Decline" tap or 0s expiry, preventing accidental loss of rides. |
| **C-02** | Bottom Navigation visibility during **Active Trip (S-20)** | Bottom tabs allow navigating away from the active trip to Earnings or Profile. | **Resolution:** Bottom Navigation Bar is **hidden** during `ActiveTripScreen`. Driver can access Safety and Support via dedicated trip overlay actions, but cannot abandon the trip flow until completion. |
| **C-03** | Document Upload navigation loop on **Rejection (S-08 -> S-07)** | Tapping back on document upload might pop the driver back into the blocked screen loop. | **Resolution:** `S-07: DocumentUploadScreen` is launched as a single-top edit destination. Back returns to `S-08: VerificationStatusScreen` with refreshed status. |
| **C-04** | Duty Toggle during **Active Trip / Offer** | Driver taps "Go Offline" while on trip. | **Resolution:** `DutyStatusCard` disables offline toggle while `trip_status IN (ASSIGNED, EN_ROUTE, ARRIVED, IN_PROGRESS)`. Driver must resolve active trip before going offline. |
| **C-05** | Category Switching while **Online** | Changing category (e.g. from Auto to Bike) while waiting for dispatch. | **Resolution:** Category selection in `ProfileScreen` is locked while duty is `Online`. Driver must first toggle to `Offline` before modifying active service category. |

---

## 5. Critical Recovery Flows & Edge-Case Lifecycle

### 5.1 App Process Death / System Kill
- **Scenario:** Driver is in the middle of a trip (`S-20`) or reviewing an offer (`S-19`), and Android OS terminates the process due to memory pressure or device reboot.
- **Recovery Architecture:**
  1. On cold launch, `SplashScreen` calls `GET /v1/driver/session/restore`.
  2. Backend returns current authoritative state:
     - If `active_trip_id` is present: Navigates immediately to `S-20: ActiveTripScreen`, re-populates polyline, stage, and customer info.
     - If `active_offer_id` is present with server expiry time in future: Navigates to `S-19: IncomingOfferOverlay` with remaining seconds (`server_expiry - current_server_time`). If expired, discards offer.
     - If no active work: Restores `S-09: HomeDashboardScreen` with duty state as confirmed by server.

### 5.2 Network Loss & Offline Reconnection
- **Scenario:** Driver drives through an underground underpass or cellular dead zone while on an active trip.
- **Recovery Architecture:**
  1. UI displays an unobtrusive top banner: "Network disconnected. Offline tracking active."
  2. GPS location coordinates continue recording into local Room database buffer (`LocationBufferEntity`).
  3. Action buttons (e.g., "I Have Arrived", "Start Trip") are tagged with `pending_sync = true` and stored in local queue.
  4. Once cellular connectivity returns, the sync worker (`SyncTripEventsWorker`) flushes buffered locations and events in chronological order with original timestamps.
  5. Critical irreversible transitions (like fare calculation) block until backend acknowledges receipt.

### 5.3 Location / GPS Disablement while Online
- **Scenario:** Driver accidentally turns off Location services or switches to low-accuracy mode while Online.
- **Recovery Architecture:**
  1. FusedLocationProvider listener detects provider disabled.
  2. App immediately prompts full-screen non-dismissible Dialog: "High-Accuracy GPS Required. Turn on Device Location to stay online."
  3. If not re-enabled within 60 seconds, app automatically requests backend `POST /v1/driver/duty/offline` with reason `GPS_LOST` to protect driver's acceptance rating and prevent unreachable dispatches.

---

## 6. Android Back Button & System Navigation Behavior Matrix

| Active Screen | System Back Press Action | Navigation Stack Action |
|---|---|---|
| `S-01: Splash` | No-op (ignores back press during config check) | None |
| `S-02: Language` | System exit | App closes |
| `S-03: Phone Login` | Navigates back to `S-02: LanguageSelection` | `navController.popBackStack()` |
| `S-04: OTP Verification` | Navigates back to `S-03: PhoneLogin` | `navController.popBackStack()` |
| `S-05: Driver Reg` | Shows "Save Draft & Exit?" confirmation dialog | Dismisses or exits |
| `S-06: Vehicle Reg` | Navigates back to `S-05: DriverRegistration` | `navController.popBackStack()` |
| `S-07: Document Upload` | Navigates back to `S-06` (or `S-08` if from review) | `navController.popBackStack()` |
| `S-08: Verify Status` | Closes app (prevents re-entering registration forms) | System exit |
| `S-09: Home Dashboard` | Pressing Back prompts: "Press back again to exit Drive Partner" | Double-tap to exit |
| `S-10: Service Area` | Saves pending preferences and returns to Home | `navController.popBackStack()` |
| `S-12: Orders` | Switches bottom navigation back to Home tab | Selects Home tab |
| `S-13: Earnings` | Switches bottom navigation back to Home tab | Selects Home tab |
| `S-14: Wallet` | Returns to `S-13: EarningsDashboard` | `navController.popBackStack()` |
| `S-15: Withdrawals` | Returns to `S-14: Wallet` | `navController.popBackStack()` |
| `S-16: Rewards` | Switches bottom navigation back to Home tab | Selects Home tab |
| `S-17: Refer & Earn` | Returns to `S-16: Rewards` | `navController.popBackStack()` |
| `S-18: Priority Orders`| Returns to `S-09: HomeDashboard` | `navController.popBackStack()` |
| `S-19: Incoming Offer` | Consumed / No-op (does NOT cancel offer; waits for explicit action or expiry) | None |
| `S-20: Active Trip` | Minimized to pip / shows background toast ("Trip is in progress") | Locked in trip |
| `S-21: Trip Receipt` | Finalizes receipt and returns to `S-09: HomeDashboard` | `popUpTo(Home) { inclusive = false }` |
| `S-22: Safety Center` | Returns to previous screen (Home or Active Trip) | `navController.popBackStack()` |
| `S-23: Profile` | Switches bottom navigation back to Home tab | Selects Home tab |
| `S-24: Help & Support` | Returns to previous screen (Profile or Order Detail) | `navController.popBackStack()` |

---

## 7. Technical Implementation Plan & Package Organization

All screens will be implemented strictly according to the clean architecture package structure:

```
app/src/main/java/com/drivepartner/app/
 ├── data/
 │    ├── location/         # FusedLocationProviderClient & PartnerLocation
 │    ├── model/            # PartnerProfile, PartnerStatus, ServiceType, Order, Trip, Wallet
 │    ├── remote/           # Retrofit API services, DTOs, WebSocket handlers
 │    └── repository/       # PartnerRepository, TripRepository, WalletRepository
 ├── navigation/
 │    ├── DriveDestination.kt    # Type-safe navigation routes & arguments
 │    └── DriveNavHost.kt        # NavHost configuration & transitions
 └── ui/
      ├── components/       # Reusable Material 3 design system atoms
      ├── screens/
      │    ├── auth/        # S-01 (Splash), S-02 (Lang), S-03 (Phone), S-04 (OTP)
      │    ├── onboarding/  # S-05 (Personal), S-06 (Vehicle), S-07 (Docs), S-08 (Status)
      │    ├── home/        # S-09 (Home), S-10 (Service Area), S-11 (Duty Sheet)
      │    ├── dispatch/    # S-18 (Priority Orders), S-19 (Incoming Offer)
      │    ├── trip/        # S-20 (Active Trip), S-21 (Trip Receipt)
      │    ├── orders/      # S-12 (Orders History & Detail)
      │    ├── earnings/    # S-13 (Earnings), S-14 (Wallet), S-15 (Withdrawals)
      │    ├── rewards/     # S-16 (Rewards), S-17 (Refer & Earn)
      │    ├── safety/      # S-22 (Safety Center & SOS)
      │    ├── profile/     # S-23 (Profile & Settings)
      │    └── support/     # S-24 (Help & Support Tickets)
      ├── theme/            # Theme, Color, Type, Shape
      └── viewmodel/        # MVVM StateFlow ViewModels
```

This blueprint stands as the authoritative reference for all subsequent screen construction and navigation wiring in the Drive Partner Android application.
