/**
 * Drive Partner — design tokens mirrored 1:1 from the Android source of truth
 * (app/src/main/java/com/drivepartner/app/ui/theme/*).
 *
 * Keep this file in lock-step with DriveColorTokens.kt: the Android unit tests assert the contrast
 * ratios quoted here, so any change must happen in both places.
 */

export type Palette = {
  isDark: boolean;
  brand: string;
  brandPressed: string;
  brandSubtle: string;
  onBrand: string;
  brandContainer: string;
  onBrandContainer: string;
  background: string;
  backgroundSubtle: string;
  surface: string;
  surfaceRaised: string;
  surfaceSunken: string;
  surfaceTonal: string;
  border: string;
  borderStrong: string;
  divider: string;
  textPrimary: string;
  textSecondary: string;
  textTertiary: string;
  textOnSubtle: string;
  textDisabled: string;
  textLink: string;
  iconPrimary: string;
  iconSecondary: string;
  success: string;
  successStrong: string;
  onSuccess: string;
  successContainer: string;
  onSuccessContainer: string;
  warning: string;
  warningStrong: string;
  onWarning: string;
  warningContainer: string;
  onWarningContainer: string;
  danger: string;
  dangerStrong: string;
  onDanger: string;
  dangerContainer: string;
  onDangerContainer: string;
  neutral: string;
  neutralContainer: string;
  onNeutralContainer: string;
  shadow: string;
  scrim: string;
  /** 10-18% brand tint used for icon wells and selected rows. */
  tint: string;
  map: {
    land: string;
    block: string;
    park: string;
    water: string;
    road: string;
    roadMajor: string;
    label: string;
    route: string;
    pickup: string;
    drop: string;
  };
};

export const light: Palette = {
  isDark: false,
  brand: '#1769AA',
  brandPressed: '#124B7A',
  brandSubtle: '#F3F9FD',
  onBrand: '#FFFFFF',
  brandContainer: '#E3F0FB',
  onBrandContainer: '#0D3A61',
  background: '#FFFFFF',
  backgroundSubtle: '#F5F7FA',
  surface: '#FFFFFF',
  surfaceRaised: '#F0F3F8',
  surfaceSunken: '#F5F7FA',
  surfaceTonal: '#F0F3F8',
  border: '#E7EAF0',
  borderStrong: '#7C8798',
  divider: '#E7EAF0',
  textPrimary: '#17202A',
  textSecondary: '#697586',
  textTertiary: '#656F80',
  textOnSubtle: '#616B7C',
  textDisabled: '#B4BDCB',
  textLink: '#124B7A',
  iconPrimary: '#17202A',
  iconSecondary: '#697586',
  success: '#16A36A',
  successStrong: '#12875A',
  onSuccess: '#FFFFFF',
  successContainer: '#E6F6EF',
  onSuccessContainer: '#0B5C3B',
  warning: '#F5A623',
  warningStrong: '#8A5A06',
  onWarning: '#17202A',
  warningContainer: '#FEF3DC',
  onWarningContainer: '#7A4F04',
  danger: '#DC3545',
  dangerStrong: '#DC3545',
  onDanger: '#FFFFFF',
  dangerContainer: '#FDECEC',
  onDangerContainer: '#7D1924',
  neutral: '#697586',
  neutralContainer: '#E7EAF0',
  onNeutralContainer: '#3F4A5A',
  shadow: 'rgba(23, 32, 42, 0.08)',
  scrim: 'rgba(23, 32, 42, 0.45)',
  tint: 'rgba(23, 105, 170, 0.10)',
  map: {
    land: '#EFF3F8',
    block: '#E5EBF3',
    park: '#DCEFDF',
    water: '#CDE3F6',
    road: '#FFFFFF',
    roadMajor: '#FDF1D8',
    label: '#697586',
    route: '#1769AA',
    pickup: '#16A36A',
    drop: '#DC3545',
  },
};

export const dark: Palette = {
  isDark: true,
  brand: '#5CA8E6',
  brandPressed: '#7CBDF0',
  brandSubtle: '#13283C',
  onBrand: '#04121E',
  brandContainer: '#15385A',
  onBrandContainer: '#D6EAFC',
  background: '#0E141B',
  backgroundSubtle: '#0A0F14',
  surface: '#16202B',
  surfaceRaised: '#1B2735',
  surfaceSunken: '#101820',
  surfaceTonal: '#1F2B38',
  border: '#2D3C4E',
  borderStrong: '#5E7186',
  divider: '#263443',
  textPrimary: '#F1F5F9',
  textSecondary: '#A8B6C6',
  textTertiary: '#8895A8',
  textOnSubtle: '#B6C2D1',
  textDisabled: '#4A5A6C',
  textLink: '#5CA8E6',
  iconPrimary: '#F1F5F9',
  iconSecondary: '#A8B6C6',
  success: '#3ECF8E',
  successStrong: '#3ECF8E',
  onSuccess: '#04121E',
  successContainer: '#12402F',
  onSuccessContainer: '#C9F5E1',
  warning: '#FBC65F',
  warningStrong: '#FBC65F',
  onWarning: '#2A1B04',
  warningContainer: '#4A3208',
  onWarningContainer: '#FBE7C2',
  danger: '#FF7C89',
  dangerStrong: '#FF7C89',
  onDanger: '#2A0A0E',
  dangerContainer: '#4E1A22',
  onDangerContainer: '#FFDADF',
  neutral: '#93A0B2',
  neutralContainer: '#1F2B38',
  onNeutralContainer: '#D3DCE8',
  shadow: 'rgba(0, 0, 0, 0.35)',
  scrim: 'rgba(5, 10, 16, 0.6)',
  tint: 'rgba(92, 168, 230, 0.18)',
  map: {
    land: '#111A24',
    block: '#17222E',
    park: '#14291F',
    water: '#14324A',
    road: '#2C3B4C',
    roadMajor: '#3D4A57',
    label: '#A8B6C6',
    route: '#5CA8E6',
    pickup: '#3ECF8E',
    drop: '#FF7C89',
  },
};

export const spacing = { xxs: 2, xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 };
export const radius = { control: 12, cardSmall: 14, card: 16, cardLarge: 20, sheet: 20, pill: 999 };
export const elevation = { l0: 0, l1: 1, l2: 2, l3: 4, l4: 8, l5: 16 };
export const motion = { instant: 90, fast: 140, standard: 220, moderate: 300, slow: 420 };

export const typography = [
  { role: 'displayMedium', size: 28, lineHeight: 34, weight: 700, use: 'Hero earnings totals' },
  { role: 'headlineMedium', size: 20, lineHeight: 26, weight: 700, use: 'Screen titles' },
  { role: 'titleLarge', size: 18, lineHeight: 24, weight: 600, use: 'Card titles, app bar' },
  { role: 'titleMedium', size: 16, lineHeight: 22, weight: 600, use: 'Section titles' },
  { role: 'bodyLarge', size: 15, lineHeight: 22, weight: 400, use: 'Primary reading text' },
  { role: 'bodyMedium', size: 14, lineHeight: 20, weight: 400, use: 'Addresses, ledger rows' },
  { role: 'labelSmall', size: 11, lineHeight: 14, weight: 600, use: 'Chips, tabs, badges' },
] as const;

/** WCAG 2.1 relative luminance + contrast — same maths as the Android unit tests. */
export function contrast(fg: string, bg: string): number {
  const lum = (hex: string) => {
    const h = hex.replace('#', '');
    const [r, g, b] = [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16) / 255).map((c) =>
      c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4),
    );
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  };
  const a = lum(fg);
  const b = lum(bg);
  const [hi, lo] = a > b ? [a, b] : [b, a];
  return (hi + 0.05) / (lo + 0.05);
}

export type Strings = Record<string, { en: string; hi: string }>;

export const strings: Strings = {
  appName: { en: 'Drive Partner', hi: 'ड्राइव पार्टनर' },
  home: { en: 'Home', hi: 'होम' },
  orders: { en: 'Orders', hi: 'ऑर्डर' },
  earnings: { en: 'Earnings', hi: 'कमाई' },
  rewards: { en: 'Rewards', hi: 'रिवार्ड्स' },
  profile: { en: 'Profile', hi: 'प्रोफाइल' },
  online: { en: 'Online', hi: 'ऑनलाइन' },
  offline: { en: 'Offline', hi: 'ऑफलाइन' },
  welcome: { en: 'Welcome back, Rajesh', hi: 'वापसी पर स्वागत है, राजेश' },
  today: { en: "Today's overview", hi: 'आज का विवरण' },
  trips: { en: 'Trips', hi: 'ट्रिप' },
  hours: { en: 'Hours online', hi: 'ऑनलाइन घंटे' },
  acceptance: { en: 'Acceptance', hi: 'स्वीकृति' },
  shortcuts: { en: 'Shortcuts', hi: 'शॉर्टकट' },
  dispatchQueue: { en: 'Dispatch queue', hi: 'डिस्पैच कतार' },
  noOrders: { en: 'No orders yet', hi: 'अभी कोई ऑर्डर नहीं' },
  noOrdersSub: { en: 'New booking requests appear here once you go online.', hi: 'ऑनलाइन आने पर नए बुकिंग अनुरोध यहां दिखेंगे।' },
  goOnline: { en: 'Go online', hi: 'ऑनलाइन जाएं' },
  incomingOffer: { en: 'Incoming request', hi: 'आने वाला अनुरोध' },
  pickup: { en: 'Pickup', hi: 'पिकअप' },
  drop: { en: 'Drop', hi: 'ड्रॉप' },
  accept: { en: 'Accept', hi: 'स्वीकारें' },
  decline: { en: 'Decline', hi: 'अस्वीकारें' },
  estimated: { en: 'Estimated earning', hi: 'अनुमानित कमाई' },
  activeTrip: { en: 'Active trip', hi: 'सक्रिय ट्रिप' },
  navigate: { en: 'Navigate', hi: 'नेविगेट' },
  arrived: { en: 'I have arrived', hi: 'मैं पहुंच गया' },
  wallet: { en: 'Available balance', hi: 'उपलब्ध बैलेंस' },
  withdraw: { en: 'Withdraw', hi: 'निकालें' },
  thisWeek: { en: 'This week', hi: 'इस सप्ताह' },
  todayLabel: { en: 'Today', hi: 'आज' },
  month: { en: 'This month', hi: 'इस माह' },
  ledger: { en: 'Ledger', hi: 'लेजर' },
  bikeTaxi: { en: 'Bike taxi', hi: 'बाइक टैक्सी' },
  serviceArea: { en: 'Jaipur Central', hi: 'जयपुर सेंट्रल' },
};

export function useT(lang: 'en' | 'hi') {
  return (key: keyof typeof strings) => strings[key][lang];
}
