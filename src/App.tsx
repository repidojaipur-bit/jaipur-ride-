/**
 * Drive Partner — Original UI Design System (living preview)
 *
 * This preview renders the exact tokens the Android app ships
 * (app/src/main/java/com/drivepartner/app/ui/theme + public/design/icons.json).
 * Everything you see here is original artwork: the icon geometry is generated from
 * tools/design_system/drive_icons.py, the illustrations and the map cartography are drawn as code.
 * No competitor asset, screenshot or stock photo is used anywhere.
 */

import React, { useEffect, useState } from 'react';
import {
  contrast,
  dark,
  elevation,
  light,
  motion,
  radius,
  spacing,
  typography,
  type Palette,
} from './design/tokens';
import { useT } from './design/tokens';

type IconSpec = { strokes: string[]; fills: string[]; label: string; group: string };
type IconManifest = { meta: Record<string, unknown>; icons: Record<string, IconSpec> };

const FONT =
  "'Inter', 'Segoe UI', 'Noto Sans', 'Noto Sans Devanagari', system-ui, -apple-system, sans-serif";

// ---------------------------------------------------------------------------
// Primitives
// ---------------------------------------------------------------------------

function Icon({
  name,
  icons,
  size = 24,
  color,
  strokeWidth = 1.8,
}: {
  name: string;
  icons: Record<string, IconSpec>;
  size?: number;
  color: string;
  strokeWidth?: number;
}) {
  const spec = icons[name];
  if (!spec) return null;
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" style={{ flexShrink: 0 }}>
      {spec.strokes.map((d, i) => (
        <path
          key={`s${i}`}
          d={d}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ))}
      {spec.fills.map((d, i) => (
        <path key={`f${i}`} d={d} fill={color} />
      ))}
    </svg>
  );
}

function Card({
  p,
  children,
  pad = spacing.lg,
  r = radius.card,
  style,
}: {
  p: Palette;
  children: React.ReactNode;
  pad?: number;
  r?: number;
  style?: React.CSSProperties;
}) {
  return (
    <div
      style={{
        background: p.surface,
        border: `1px solid ${p.border}`,
        borderRadius: r,
        padding: pad,
        boxShadow: p.isDark ? 'none' : `0 1px 2px ${p.shadow}, 0 4px 12px ${p.shadow}`,
        ...style,
      }}
    >
      {children}
    </div>
  );
}

function SectionTitle({ p, title, sub }: { p: Palette; title: string; sub?: string }) {
  return (
    <div style={{ marginBottom: spacing.lg }}>
      <h2 style={{ margin: 0, fontSize: 22, lineHeight: '28px', fontWeight: 700, color: p.textPrimary }}>{title}</h2>
      {sub ? (
        <p style={{ margin: `${spacing.xs}px 0 0`, fontSize: 14, lineHeight: '20px', color: p.textSecondary }}>{sub}</p>
      ) : null}
    </div>
  );
}

function Chip({
  p,
  label,
  bg,
  fg,
  dot,
}: {
  p: Palette;
  label: string;
  bg: string;
  fg: string;
  dot?: string;
}) {
  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 6,
        background: bg,
        color: fg,
        borderRadius: radius.pill,
        padding: '6px 12px',
        fontSize: 11,
        lineHeight: '14px',
        fontWeight: 600,
        letterSpacing: 0.4,
      }}
    >
      {dot ? <span style={{ width: 7, height: 7, borderRadius: 999, background: dot }} /> : null}
      {label}
    </span>
  );
}

function Button({
  p,
  label,
  variant = 'primary',
  disabled,
  full = true,
}: {
  p: Palette;
  label: string;
  variant?: 'primary' | 'success' | 'outline' | 'danger';
  disabled?: boolean;
  full?: boolean;
}) {
  const styles: Record<string, React.CSSProperties> = {
    primary: { background: p.brand, color: p.onBrand },
    success: { background: p.successStrong, color: p.onSuccess },
    outline: { background: 'transparent', color: p.textPrimary, border: `1px solid ${p.borderStrong}` },
    danger: { background: p.danger, color: p.onDanger },
  };
  return (
    <button
      style={{
        width: full ? '100%' : undefined,
        minHeight: variant === 'outline' ? 48 : 52,
        borderRadius: radius.control,
        border: variant === 'outline' ? `1px solid ${p.borderStrong}` : 'none',
        fontSize: 15,
        lineHeight: '20px',
        fontWeight: 600,
        fontFamily: FONT,
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.5 : 1,
        transition: `background ${motion.fast}ms ease`,
        ...styles[variant],
      }}
    >
      {label}
    </button>
  );
}

// ---------------------------------------------------------------------------
// Original illustrations (mirrored 1:1 from DriveIllustrations.kt)
// ---------------------------------------------------------------------------

function Illustration({ kind, p }: { kind: string; p: Palette }) {
  const stroke = (d: string, c: string, w = 2.2, dash?: boolean) => (
    <path
      d={d}
      fill="none"
      stroke={c}
      strokeWidth={w}
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeDasharray={dash ? '4.5 4.5' : undefined}
    />
  );
  const ring = (cx: number, cy: number, r: number, c: string, w = 2.2) => (
    <circle cx={cx} cy={cy} r={r} fill="none" stroke={c} strokeWidth={w} />
  );
  const dot = (cx: number, cy: number, r: number, c: string) => <circle cx={cx} cy={cy} r={r} fill={c} />;
  const scooter = (c: string) => (
    <g>
      {ring(30, 72, 10, c, 2.4)}
      {ring(74, 72, 10, c, 2.4)}
      {stroke('M36 63 H58', c, 2.4)}
      {stroke('M36 63 L30 72', c, 2.4)}
      {stroke('M58 63 L74 72', c, 2.4)}
      {stroke('M40 63 L36 48', c, 2.4)}
      {stroke('M29 48 H43', c, 2.4)}
      {stroke('M62 63 L70 42', c, 2.4)}
      {stroke('M64 40 H77', c, 2.4)}
    </g>
  );
  const shield = (c: string) => stroke('M50 7 L83 19 L83 47 Q83 78 50 93 Q17 78 17 47 L17 19 Z', c, 2.6);
  let body: React.ReactNode = null;
  if (kind === 'empty') {
    body = (
      <g>
        {dot(50, 52, 40, p.isDark ? 'rgba(92,168,230,0.10)' : 'rgba(23,105,170,0.06)')}
        {dot(50, 52, 29, p.isDark ? 'rgba(92,168,230,0.14)' : 'rgba(23,105,170,0.08)')}
        {stroke('M8 86 H92', p.border, 2)}
        {stroke('M58 78 Q78 74 84 56', p.brand, 2, true)}
        {ring(84, 46, 7, p.success)}
        {dot(84, 46, 2.4, p.success)}
        {scooter(p.textSecondary)}
        {stroke('M16 28 Q24 22 32 28', p.textDisabled, 2)}
      </g>
    );
  } else if (kind === 'offline') {
    body = (
      <g>
        {dot(50, 50, 38, p.isDark ? 'rgba(147,160,178,0.16)' : 'rgba(105,117,134,0.08)')}
        <path d="M 68 17 A 36 36 0 1 1 32 17" fill="none" stroke={p.neutral} strokeWidth={2.6} strokeLinecap="round" />
        {stroke('M50 20 V34', p.neutral, 2.6)}
        {stroke('M10 88 H90', p.border, 2)}
        {scooter(p.textSecondary)}
      </g>
    );
  } else if (kind === 'verification') {
    body = (
      <g>
        {dot(50, 50, 40, p.isDark ? 'rgba(92,168,230,0.10)' : 'rgba(23,105,170,0.06)')}
        {shield(p.brand)}
        {stroke('M36 36 H64', p.textSecondary)}
        {stroke('M36 46 H64', p.textSecondary)}
        {stroke('M36 56 H52', p.textSecondary)}
        {dot(74, 70, 11, p.warningContainer)}
        {ring(74, 70, 11, p.warning)}
        {stroke('M74 65 V70', p.warningStrong)}
        {stroke('M74 70 L79 73', p.warningStrong)}
      </g>
    );
  } else if (kind === 'wallet') {
    body = (
      <g>
        {dot(50, 50, 38, p.isDark ? 'rgba(62,207,142,0.12)' : 'rgba(22,163,106,0.07)')}
        {stroke('M14 34 H66 Q70 34 70 38 V62 Q70 66 66 66 H14 Q10 66 10 62 V38 Q10 34 14 34 Z', p.success, 2.4)}
        {stroke('M24 43 H42', p.textPrimary)}
        {stroke('M24 50 H42', p.textPrimary)}
        {stroke('M38 43 Q38 52 26 52 L42 58', p.textPrimary)}
        {ring(56, 50, 7, p.warning)}
        {stroke('M46 78 H92', p.border, 2)}
        {ring(60, 74, 4, p.warning, 2)}
        {ring(70, 74, 4, p.warning, 2)}
        {ring(80, 74, 4, p.warning, 2)}
        {stroke('M74 44 L82 36 L88 41 L94 30', p.successStrong)}
      </g>
    );
  } else if (kind === 'error') {
    body = (
      <g>
        {dot(50, 46, 36, p.isDark ? 'rgba(255,124,137,0.12)' : 'rgba(220,53,69,0.06)')}
        {dot(38, 40, 13, p.surfaceTonal)}
        {dot(58, 36, 16, p.surfaceTonal)}
        {stroke('M20 56 Q20 46 30 45 Q34 30 50 30 Q66 30 70 43 Q82 44 82 56 Z', p.textSecondary, 2.4)}
        {stroke('M32 66 L29 74', p.textDisabled)}
        {stroke('M72 66 L69 74', p.textDisabled)}
        {dot(58, 78, 13, p.dangerContainer)}
        {stroke('M58 62 L74 88 L42 88 Z', p.danger, 2.4)}
        {stroke('M58 72 V79', p.dangerStrong, 2.4)}
        {dot(58, 83.5, 1.4, p.dangerStrong)}
      </g>
    );
  } else {
    body = (
      <g>
        {dot(50, 50, 40, p.isDark ? 'rgba(255,124,137,0.10)' : 'rgba(220,53,69,0.05)')}
        {shield(p.brand)}
        {stroke('M50 66 Q34 54 36 44 Q38 36 46 38 Q50 39 50 44 Q50 39 54 38 Q62 36 64 44 Q66 54 50 66 Z', p.danger, 2.4)}
        {stroke('M8 26 L14 31', p.textDisabled, 2)}
        {stroke('M92 26 L86 31', p.textDisabled, 2)}
      </g>
    );
  }
  return (
    <svg viewBox="0 0 100 100" style={{ width: '100%', maxWidth: 220, display: 'block', margin: '0 auto' }} aria-hidden="true">
      {body}
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Original map cartography
// ---------------------------------------------------------------------------

function MapSurface({ p, withRoute = true }: { p: Palette; withRoute?: boolean }) {
  const m = p.map;
  return (
    <svg viewBox="0 0 340 240" style={{ width: '100%', display: 'block', borderRadius: radius.cardLarge }} aria-hidden="true">
      <rect width="340" height="240" fill={m.land} />
      {[
        [12, 14, 86, 58],
        [112, 10, 92, 62],
        [218, 16, 108, 54],
        [14, 92, 84, 62],
        [112, 90, 94, 64],
        [220, 88, 104, 60],
        [16, 172, 88, 54],
        [118, 170, 90, 56],
        [222, 168, 102, 56],
      ].map(([x, y, w, h], i) => (
        <rect key={i} x={x} y={y} width={w} height={h} rx="6" fill={m.block} />
      ))}
      <path d="M0 208 Q60 196 118 214 T340 206 L340 240 L0 240 Z" fill={m.water} />
      <rect x="252" y="108" width="60" height="42" rx="8" fill={m.park} />
      {[20, 104, 164].map((y) => (
        <line key={y} x1="0" y1={y} x2="340" y2={y} stroke={m.road} strokeWidth={y === 104 ? 10 : 6} />
      ))}
      {[104, 212].map((x) => (
        <line key={x} x1={x} y1="0" x2={x} y2="240" stroke={m.road} strokeWidth={x === 212 ? 10 : 6} />
      ))}
      <line x1="0" y1="82" x2="340" y2="82" stroke={m.roadMajor} strokeWidth={7} />
      <line x1="156" y1="0" x2="156" y2="240" stroke={m.roadMajor} strokeWidth={7} />
      {withRoute ? (
        <path
          d="M64 186 L64 104 L156 104 L156 60 L262 60"
          fill="none"
          stroke={m.route}
          strokeWidth="5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      ) : null}
      {withRoute ? (
        <g>
          <circle cx="64" cy="186" r="9" fill={m.pickup} opacity="0.22" />
          <circle cx="64" cy="186" r="5.5" fill={m.pickup} stroke={p.surface} strokeWidth="2" />
          <circle cx="262" cy="60" r="9" fill={m.drop} opacity="0.22" />
          <circle cx="262" cy="60" r="5.5" fill={m.drop} stroke={p.surface} strokeWidth="2" />
          <g transform="translate(156 104) rotate(45)">
            <path d="M0 -9 L6 7 L0 3 L-6 7 Z" fill={m.route} stroke={p.surface} strokeWidth="1.5" />
          </g>
        </g>
      ) : null}
      <text x="14" y="230" fontSize="10" fill={m.label} fontFamily={FONT}>
        Malviya Nagar • Jaipur
      </text>
    </svg>
  );
}

// ---------------------------------------------------------------------------
// Phone frame screens
// ---------------------------------------------------------------------------

function Phone({ p, children, label }: { p: Palette; children: React.ReactNode; label: string }) {
  return (
    <div style={{ width: 300, flexShrink: 0 }}>
      <div
        style={{
          borderRadius: 34,
          border: `1px solid ${p.border}`,
          background: p.background,
          overflow: 'hidden',
          boxShadow: p.isDark ? 'none' : `0 12px 32px ${p.shadow}`,
        }}
      >
        <div style={{ height: 26, background: p.isDark ? p.surfaceRaised : p.brand }} />
        {children}
      </div>
      <div style={{ marginTop: spacing.sm, fontSize: 12, color: p.textSecondary, textAlign: 'center' }}>{label}</div>
    </div>
  );
}

function HomeScreenPhone({ p, lang, icons }: { p: Palette; lang: 'en' | 'hi'; icons: Record<string, IconSpec> }) {
  const t = useT(lang);
  return (
    <div style={{ background: p.backgroundSubtle, minHeight: 560, display: 'flex', flexDirection: 'column' }}>
      <div style={{ background: p.isDark ? p.surfaceRaised : p.brand, padding: '10px 12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Icon name="menu" icons={icons} size={20} color={p.isDark ? p.textPrimary : p.onBrand} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 16, fontWeight: 700, color: p.isDark ? p.textPrimary : p.onBrand }}>{t('appName')}</div>
            <div style={{ fontSize: 10, color: p.isDark ? p.textSecondary : 'rgba(255,255,255,0.85)' }}>
              {t('bikeTaxi')} • {t('serviceArea')}
            </div>
          </div>
          <Chip p={p} label={t('online')} bg={p.successContainer} fg={p.onSuccessContainer} dot={p.success} />
          <Icon name="bell" icons={icons} size={20} color={p.isDark ? p.textPrimary : p.onBrand} />
        </div>
      </div>
      <div style={{ padding: spacing.lg, display: 'flex', flexDirection: 'column', gap: spacing.md }}>
        <Card p={p} r={radius.cardLarge} pad={spacing.lg}>
          <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
            <div style={{ width: 44, height: 44, borderRadius: radius.control, background: p.tint, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="rupee_note" icons={icons} size={22} color={p.success} />
            </div>
            <div>
              <div style={{ fontSize: 11, fontWeight: 600, letterSpacing: 0.8, color: p.textTertiary }}>{t('today').toUpperCase()}</div>
              <div style={{ fontSize: 26, lineHeight: '32px', fontWeight: 800, color: p.textPrimary }}>₹1,450.00</div>
            </div>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: spacing.sm, marginTop: spacing.md }}>
            {[
              ['12', t('trips')],
              ['6.5', t('hours')],
              ['98%', t('acceptance')],
            ].map(([v, l]) => (
              <div key={l} style={{ background: p.surfaceSunken, borderRadius: radius.control, padding: spacing.sm, textAlign: 'center' }}>
                <div style={{ fontSize: 15, fontWeight: 700, color: p.textPrimary }}>{v}</div>
                <div style={{ fontSize: 10, color: p.textSecondary }}>{l}</div>
              </div>
            ))}
          </div>
        </Card>
        <div style={{ fontSize: 12, fontWeight: 600, color: p.textSecondary }}>{t('shortcuts')}</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: spacing.md }}>
          {[
            ['orders', t('orders')],
            ['earnings', t('earnings')],
            ['rewards', t('rewards')],
            ['profile', t('profile')],
          ].map(([ic, label]) => (
            <Card key={ic} p={p} pad={spacing.md} r={radius.card}>
              <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm }}>
                <div style={{ width: 36, height: 36, borderRadius: radius.control, background: p.brandContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <Icon name={ic} icons={icons} size={20} color={p.brand} />
                </div>
                <span style={{ fontSize: 13, fontWeight: 600, color: p.textPrimary }}>{label}</span>
              </div>
            </Card>
          ))}
        </div>
        <Card p={p} pad={spacing.lg}>
          <div style={{ fontSize: 13, fontWeight: 700, color: p.textPrimary, marginBottom: spacing.sm }}>{t('dispatchQueue')}</div>
          <Illustration kind="empty" p={p} />
          <div style={{ textAlign: 'center', marginTop: spacing.sm }}>
            <div style={{ fontSize: 14, fontWeight: 600, color: p.textPrimary }}>{t('noOrders')}</div>
            <div style={{ fontSize: 12, color: p.textSecondary, marginTop: 2 }}>{t('noOrdersSub')}</div>
          </div>
        </Card>
      </div>
      <BottomBar p={p} lang={lang} icons={icons} active="home" />
    </div>
  );
}

function OfferPhone({ p, lang, icons }: { p: Palette; lang: 'en' | 'hi'; icons: Record<string, IconSpec> }) {
  const t = useT(lang);
  return (
    <div style={{ background: p.scrim, minHeight: 560, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
      <div style={{ background: p.surface, borderRadius: `${radius.sheet}px ${radius.sheet}px 0 0`, padding: spacing.lg }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
          <div style={{ position: 'relative', width: 52, height: 52 }}>
            <svg viewBox="0 0 52 52" width="52" height="52">
              <circle cx="26" cy="26" r="22" fill="none" stroke={p.border} strokeWidth="4" />
              <circle
                cx="26"
                cy="26"
                r="22"
                fill="none"
                stroke={p.warning}
                strokeWidth="4"
                strokeLinecap="round"
                strokeDasharray="138"
                strokeDashoffset="46"
                transform="rotate(-90 26 26)"
              />
            </svg>
            <div style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 16, fontWeight: 800, color: p.textPrimary }}>
              10
            </div>
          </div>
          <div>
            <div style={{ fontSize: 15, fontWeight: 700, color: p.textPrimary }}>{t('incomingOffer')}</div>
            <Chip p={p} label={t('bikeTaxi')} bg={p.brandContainer} fg={p.onBrandContainer} />
          </div>
        </div>
        <div style={{ marginTop: spacing.lg, display: 'flex', flexDirection: 'column', gap: spacing.md }}>
          <RouteRow p={p} icons={icons} kind="pickup" title="Malviya Nagar, Gate 2" meta="2.4 km away" />
          <RouteRow p={p} icons={icons} kind="drop" title="World Trade Park, C-Scheme" meta="6.1 km • 18 min" />
        </div>
        <div style={{ marginTop: spacing.lg, background: p.successContainer, borderRadius: radius.control, padding: spacing.md, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 12, fontWeight: 600, color: p.onSuccessContainer }}>{t('estimated')}</span>
          <span style={{ fontSize: 20, fontWeight: 800, color: p.onSuccessContainer }}>₹96.50</span>
        </div>
        <div style={{ marginTop: spacing.lg, display: 'flex', flexDirection: 'column', gap: spacing.sm }}>
          <Button p={p} label={t('accept')} variant="success" />
          <Button p={p} label={t('decline')} variant="outline" />
        </div>
      </div>
    </div>
  );
}

function RouteRow({ p, icons, kind, title, meta }: { p: Palette; icons: Record<string, IconSpec>; kind: 'pickup' | 'drop'; title: string; meta: string }) {
  const color = kind === 'pickup' ? p.success : p.danger;
  return (
    <div style={{ display: 'flex', gap: spacing.md, alignItems: 'flex-start' }}>
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, paddingTop: 2 }}>
        <span style={{ width: 10, height: 10, borderRadius: 999, background: color }} />
        <span style={{ width: 2, height: 22, background: p.border }} />
      </div>
      <div>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.4, color: p.textTertiary }}>
          {kind === 'pickup' ? 'PICKUP' : 'DROP'}
        </div>
        <div style={{ fontSize: 14, fontWeight: 600, color: p.textPrimary }}>{title}</div>
        <div style={{ fontSize: 12, color: p.textSecondary }}>{meta}</div>
      </div>
    </div>
  );
}

function TripPhone({ p, lang, icons }: { p: Palette; lang: 'en' | 'hi'; icons: Record<string, IconSpec> }) {
  const t = useT(lang);
  return (
    <div style={{ minHeight: 560, display: 'flex', flexDirection: 'column', background: p.background }}>
      <div style={{ position: 'relative' }}>
        <MapSurface p={p} />
        <div style={{ position: 'absolute', top: 10, right: 10, display: 'flex', flexDirection: 'column', gap: 8 }}>
          {['crosshair', 'navigation'].map((ic) => (
            <div key={ic} style={{ width: 40, height: 40, borderRadius: radius.control, background: p.surface, border: `1px solid ${p.border}`, display: 'flex', alignItems: 'center', justifyContent: 'center', boxShadow: `0 2px 8px ${p.shadow}` }}>
              <Icon name={ic} icons={icons} size={20} color={p.brand} />
            </div>
          ))}
        </div>
      </div>
      <Card p={p} pad={spacing.lg} style={{ margin: `-${radius.sheet}px ${spacing.md} 0`, position: 'relative' }} r={radius.cardLarge}>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.md }}>
          <div style={{ width: 44, height: 44, borderRadius: 999, background: p.brandContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Icon name="profile" icons={icons} size={22} color={p.brand} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, fontWeight: 700, color: p.textPrimary }}>Anita Sharma</div>
            <div style={{ fontSize: 12, color: p.textSecondary }}>4.9 ★ • {t('bikeTaxi')}</div>
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ width: 40, height: 40, borderRadius: 999, background: p.successContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="call" icons={icons} size={18} color={p.successStrong} />
            </div>
            <div style={{ width: 40, height: 40, borderRadius: 999, background: p.surfaceSunken, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="message" icons={icons} size={18} color={p.textSecondary} />
            </div>
          </div>
        </div>
        <div style={{ marginTop: spacing.md, display: 'flex', flexDirection: 'column', gap: spacing.sm }}>
          <Button p={p} label={t('navigate')} variant="primary" />
          <Button p={p} label={t('arrived')} variant="outline" />
        </div>
      </Card>
    </div>
  );
}

function EarningsPhone({ p, lang, icons }: { p: Palette; lang: 'en' | 'hi'; icons: Record<string, IconSpec> }) {
  const t = useT(lang);
  return (
    <div style={{ background: p.backgroundSubtle, minHeight: 560, display: 'flex', flexDirection: 'column' }}>
      <div style={{ background: p.isDark ? p.surfaceRaised : p.brand, padding: '12px 16px', fontSize: 16, fontWeight: 700, color: p.isDark ? p.textPrimary : p.onBrand }}>
        {t('earnings')}
      </div>
      <div style={{ padding: spacing.lg, display: 'flex', flexDirection: 'column', gap: spacing.md }}>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: spacing.xs, background: p.surface, border: `1px solid ${p.border}`, borderRadius: radius.control, padding: 4 }}>
          {[t('todayLabel'), t('thisWeek'), t('month')].map((label, i) => (
            <div key={label} style={{ textAlign: 'center', padding: '8px 4px', borderRadius: 8, fontSize: 12, fontWeight: 600, background: i === 1 ? p.brandContainer : 'transparent', color: i === 1 ? p.onBrandContainer : p.textSecondary }}>
              {label}
            </div>
          ))}
        </div>
        <Card p={p} pad={spacing.lg} r={radius.cardLarge}>
          <div style={{ fontSize: 12, color: p.textSecondary }}>{t('wallet')}</div>
          <div style={{ fontSize: 32, lineHeight: '40px', fontWeight: 900, color: p.textPrimary, letterSpacing: -0.5 }}>₹8,240.00</div>
          <div style={{ marginTop: spacing.md }}>
            <Button p={p} label={t('withdraw')} variant="primary" />
          </div>
        </Card>
        <Card p={p} pad={spacing.md}>
          <div style={{ fontSize: 13, fontWeight: 700, color: p.textPrimary, marginBottom: spacing.sm }}>{t('ledger')}</div>
          {[
            ['Trip fare • RJ-14 • 6.1 km', '+₹96.50', p.successStrong],
            ['Incentive • peak hour', '+₹40.00', p.successStrong],
            ['Platform fee', '−₹12.00', p.textSecondary],
          ].map(([label, amount, color]) => (
            <div key={label} style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', borderTop: `1px solid ${p.divider}` }}>
              <span style={{ fontSize: 12, color: p.textSecondary }}>{label}</span>
              <span style={{ fontSize: 13, fontWeight: 700, color: color }}>{amount}</span>
            </div>
          ))}
        </Card>
      </div>
      <BottomBar p={p} lang={lang} icons={icons} active="earnings" />
    </div>
  );
}

function BottomBar({ p, lang, icons, active }: { p: Palette; lang: 'en' | 'hi'; icons: Record<string, IconSpec>; active: string }) {
  const t = useT(lang);
  const items: [string, string][] = [
    ['home', t('home')],
    ['orders', t('orders')],
    ['earnings', t('earnings')],
    ['rewards', t('rewards')],
    ['profile', t('profile')],
  ];
  return (
    <div style={{ marginTop: 'auto', display: 'flex', background: p.surface, borderTop: `1px solid ${p.border}`, padding: '8px 4px 12px' }}>
      {items.map(([ic, label]) => {
        const selected = ic === active;
        return (
          <div key={ic} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2 }}>
            <div style={{ padding: '4px 12px', borderRadius: radius.pill, background: selected ? p.brandContainer : 'transparent' }}>
              <Icon name={ic} icons={icons} size={20} color={selected ? p.brand : p.textTertiary} />
            </div>
            <span style={{ fontSize: 10, fontWeight: selected ? 700 : 500, color: selected ? p.brand : p.textTertiary }}>{label}</span>
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Gallery sections
// ---------------------------------------------------------------------------

function ColorGrid({ p }: { p: Palette }) {
  const rows: [string, string, string][] = [
    ['brand', p.brand, 'Main actions, selected navigation'],
    ['background', p.background, 'Primary canvas'],
    ['backgroundSubtle', p.backgroundSubtle, 'Secondary canvas behind card stacks'],
    ['surface', p.surface, 'Cards, sheets, dialogs'],
    ['border', p.border, 'Hairline card borders, dividers'],
    ['textPrimary', p.textPrimary, 'Headlines, monetary totals'],
    ['textSecondary', p.textSecondary, 'Supporting copy, addresses'],
    ['success', p.success, 'Online duty & confirmed states only'],
    ['warning', p.warning, 'Countdowns, payout highlights'],
    ['danger', p.danger, 'SOS, errors, destructive actions'],
    ['neutral', p.neutral, 'Offline & inactive states'],
  ];
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: spacing.md }}>
      {rows.map(([name, hex, use]) => {
        const isText = name.startsWith('text');
        const ratio = contrast(hex, p.surface);
        return (
          <Card key={name} p={p} pad={spacing.md}>
            <div style={{ display: 'flex', gap: spacing.md, alignItems: 'center' }}>
              <div style={{ width: 46, height: 46, borderRadius: radius.cardSmall, background: hex, border: `1px solid ${p.border}`, flexShrink: 0 }} />
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 13, fontWeight: 700, color: p.textPrimary }}>{name}</div>
                <div style={{ fontSize: 11, fontFamily: 'ui-monospace, monospace', color: p.textSecondary }}>{hex}</div>
              </div>
            </div>
            <div style={{ marginTop: spacing.sm, fontSize: 11, color: p.textSecondary }}>{use}</div>
            <div style={{ marginTop: 4, fontSize: 11, fontWeight: 600, color: ratio >= 4.5 ? p.successStrong : p.textTertiary }}>
              {ratio.toFixed(2)}:1 vs surface {isText ? (ratio >= 7 ? 'AAA' : ratio >= 4.5 ? 'AA' : 'CHECK') : ''}
            </div>
          </Card>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// App shell
// ---------------------------------------------------------------------------

type SectionId = 'overview' | 'tokens' | 'type' | 'icons' | 'illustrations' | 'components' | 'states' | 'screens' | 'access';

const SECTIONS: [SectionId, string][] = [
  ['overview', 'Overview'],
  ['tokens', 'Colour tokens'],
  ['type', 'Typography'],
  ['icons', 'Original icons'],
  ['illustrations', 'Illustrations'],
  ['components', 'Components'],
  ['states', 'Screen states'],
  ['screens', 'App screens'],
  ['access', 'Accessibility & responsive'],
];

export default function App() {
  const [darkMode, setDarkMode] = useState(false);
  const [lang, setLang] = useState<'en' | 'hi'>('en');
  const [section, setSection] = useState<SectionId>('overview');
  const [icons, setIcons] = useState<Record<string, IconSpec>>({});
  const p = darkMode ? dark : light;
  const t = useT(lang);

  useEffect(() => {
    fetch('/design/icons.json')
      .then((r) => r.json())
      .then((m: IconManifest) => setIcons(m.icons))
      .catch(() => setIcons({}));
  }, []);


  return (
    <div style={{ minHeight: '100vh', background: p.backgroundSubtle, fontFamily: FONT, color: p.textPrimary, display: 'flex' }}>
      <aside style={{ width: 232, flexShrink: 0, background: p.surface, borderRight: `1px solid ${p.border}`, padding: spacing.lg, position: 'sticky', top: 0, height: '100vh', overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: spacing.xl }}>
          <svg viewBox="0 0 100 100" width="34" height="34" aria-hidden="true">
            <path d="M50 4 L86 17 L86 48 Q86 82 50 96 Q14 82 14 48 L14 17 Z" fill={p.brand} />
            <path d="M38 34 L56 50 L38 66" fill="none" stroke={p.onBrand} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />
            <path d="M54 38 L66 50 L54 62" fill="none" stroke={p.onBrand} strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" opacity="0.65" />
          </svg>
          <div>
            <div style={{ fontSize: 15, fontWeight: 800 }}>Drive Partner</div>
            <div style={{ fontSize: 11, color: p.textSecondary }}>Design System v2</div>
          </div>
        </div>
        {SECTIONS.map(([id, label]) => (
          <button
            key={id}
            onClick={() => setSection(id)}
            style={{
              display: 'block',
              width: '100%',
              textAlign: 'left',
              padding: '9px 12px',
              marginBottom: 2,
              borderRadius: radius.control,
              border: 'none',
              cursor: 'pointer',
              fontFamily: FONT,
              fontSize: 13,
              fontWeight: section === id ? 700 : 500,
              background: section === id ? p.brandContainer : 'transparent',
              color: section === id ? p.onBrandContainer : p.textSecondary,
            }}
          >
            {label}
          </button>
        ))}
        <div style={{ marginTop: spacing.xl, display: 'flex', flexDirection: 'column', gap: spacing.sm }}>
          <button
            onClick={() => setDarkMode(!darkMode)}
            style={{ padding: '10px 12px', borderRadius: radius.control, border: `1px solid ${p.borderStrong}`, background: 'transparent', color: p.textPrimary, fontFamily: FONT, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
          >
            {darkMode ? 'Light theme' : 'Dark theme'}
          </button>
          <button
            onClick={() => setLang(lang === 'en' ? 'hi' : 'en')}
            style={{ padding: '10px 12px', borderRadius: radius.control, border: `1px solid ${p.borderStrong}`, background: 'transparent', color: p.textPrimary, fontFamily: FONT, fontSize: 13, fontWeight: 600, cursor: 'pointer' }}
          >
            {lang === 'en' ? 'हिंदी में देखें' : 'View in English'}
          </button>
        </div>
      </aside>

      <main style={{ flex: 1, padding: spacing.xxl, maxWidth: 1180 }}>
        {section === 'overview' && (
          <>
            <SectionTitle
              p={p}
              title="Original UI design system"
              sub="Premium, clean and modern driver experience. White canvas #FFFFFF, secondary canvas #F5F7FA, brand #1769AA, 14–20 dp card radii, subtle shadows, Hindi + English, dark mode, edge-to-edge and Android 7.0+ support."
            />
            <div style={{ display: 'flex', gap: spacing.lg, flexWrap: 'wrap' }}>
              <Card p={p} pad={spacing.lg} style={{ flex: '1 1 320px' }}>
                <div style={{ display: 'flex', gap: spacing.md, alignItems: 'center' }}>
                  <Icon name="shield_check" icons={icons} size={28} color={p.brand} />
                  <div>
                    <div style={{ fontSize: 15, fontWeight: 700 }}>Brand independence</div>
                    <div style={{ fontSize: 13, color: p.textSecondary }}>
                      Original palette, icon geometry and illustrations. No competitor branding, screenshots or stock art anywhere in the product.
                    </div>
                  </div>
                </div>
              </Card>
              <Card p={p} pad={spacing.lg} style={{ flex: '1 1 320px' }}>
                <div style={{ display: 'flex', gap: spacing.md, alignItems: 'center' }}>
                  <Icon name="translate" icons={icons} size={28} color={p.brand} />
                  <div>
                    <div style={{ fontSize: 15, fontWeight: 700 }}>Bilingual by default</div>
                    <div style={{ fontSize: 13, color: p.textSecondary }}>
                      English and हिंदी with Devanagari-capable type, complete sentences and locale-correct INR formatting.
                    </div>
                  </div>
                </div>
              </Card>
              <Card p={p} pad={spacing.lg} style={{ flex: '1 1 320px' }}>
                <div style={{ display: 'flex', gap: spacing.md, alignItems: 'center' }}>
                  <Icon name="crosshair" icons={icons} size={28} color={p.brand} />
                  <div>
                    <div style={{ fontSize: 15, fontWeight: 700 }}>Built for the handlebar</div>
                    <div style={{ fontSize: 13, color: p.textSecondary }}>
                      52 dp primary actions, AA contrast in daylight, status always as text + icon + tint — never colour alone.
                    </div>
                  </div>
                </div>
              </Card>
            </div>
            <div style={{ marginTop: spacing.xl, display: 'flex', gap: spacing.xl, flexWrap: 'wrap' }}>
              <Phone p={p} label="Home dashboard">
                <HomeScreenPhone p={p} lang={lang} icons={icons} />
              </Phone>
              <Phone p={p} label="Dispatch offer (15 s server countdown)">
                <OfferPhone p={p} lang={lang} icons={icons} />
              </Phone>
            </div>
          </>
        )}

        {section === 'tokens' && (
          <>
            <SectionTitle p={p} title="Colour tokens" sub="Semantic roles resolve per theme. Contrast ratios are computed live with WCAG 2.1 maths — the same assertions run in the Android unit tests." />
            <ColorGrid p={p} />
            <div style={{ marginTop: spacing.xl }}>
              <SectionTitle p={p} title="Shape, spacing & elevation" />
              <div style={{ display: 'flex', gap: spacing.xl, flexWrap: 'wrap' }}>
                <Card p={p} pad={spacing.lg}>
                  <div style={{ fontSize: 13, fontWeight: 700, marginBottom: spacing.md }}>Card radii 14–20 dp</div>
                  <div style={{ display: 'flex', gap: spacing.md, alignItems: 'flex-end' }}>
                    {[radius.control, radius.cardSmall, radius.card, radius.cardLarge, radius.sheet].map((r) => (
                      <div key={r} style={{ textAlign: 'center' }}>
                        <div style={{ width: 56, height: 56, background: p.brandContainer, border: `1px solid ${p.brand}`, borderRadius: r }} />
                        <div style={{ fontSize: 11, color: p.textSecondary, marginTop: 4 }}>{r}dp</div>
                      </div>
                    ))}
                  </div>
                </Card>
                <Card p={p} pad={spacing.lg}>
                  <div style={{ fontSize: 13, fontWeight: 700, marginBottom: spacing.md }}>4 dp spacing scale</div>
                  {[4, 8, 12, 16, 24, 32].map((v) => (
                    <div key={v} style={{ display: 'flex', alignItems: 'center', gap: spacing.sm, marginBottom: 4 }}>
                      <span style={{ width: 44, fontSize: 11, color: p.textSecondary }}>{v} dp</span>
                      <div style={{ height: 8, width: v * 4, background: p.brand, borderRadius: 2 }} />
                    </div>
                  ))}
                </Card>
                <Card p={p} pad={spacing.lg}>
                  <div style={{ fontSize: 13, fontWeight: 700, marginBottom: spacing.md }}>Subtle elevation</div>
                  <div style={{ display: 'flex', gap: spacing.md }}>
                    {[elevation.l0, elevation.l1, elevation.l2, elevation.l3, elevation.l5].map((l) => (
                      <div key={l} style={{ width: 52, height: 52, borderRadius: radius.cardSmall, background: p.surface, border: `1px solid ${p.border}`, boxShadow: `0 ${l}px ${l * 2}px ${p.shadow}`, display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 11, color: p.textSecondary }}>
                        L{l}
                      </div>
                    ))}
                  </div>
                  <div style={{ marginTop: spacing.md, fontSize: 11, color: p.textSecondary }}>
                    Motion: {motion.instant}/{motion.fast}/{motion.standard}/{motion.moderate}/{motion.slow} ms — collapses to 0 when the system disables animation.
                  </div>
                </Card>
              </div>
            </div>
          </>
        )}

        {section === 'type' && (
          <>
            <SectionTitle p={p} title="Typography" sub="One sans stack with Devanagari fallback. Sizes in sp, weights tuned for daylight; no colour is baked into a style so dark mode inherits correctly." />
            <Card p={p} pad={spacing.xl}>
              {typography.map((row) => (
                <div key={row.role} style={{ display: 'flex', alignItems: 'baseline', gap: spacing.xl, padding: `${spacing.md}px 0`, borderBottom: `1px solid ${p.divider}` }}>
                  <div style={{ width: 150, flexShrink: 0 }}>
                    <div style={{ fontSize: 12, fontWeight: 700, color: p.textPrimary }}>{row.role}</div>
                    <div style={{ fontSize: 11, color: p.textSecondary }}>{row.size}/{row.lineHeight} sp • w{row.weight}</div>
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ fontSize: row.size, lineHeight: `${row.lineHeight}px`, fontWeight: row.weight, color: p.textPrimary }}>
                      {lang === 'hi' ? 'पिकअप मालवीय नगर गेट २' : 'Pickup at Malviya Nagar Gate 2'}
                    </div>
                  </div>
                  <div style={{ width: 190, fontSize: 11, color: p.textSecondary, flexShrink: 0 }}>{row.use}</div>
                </div>
              ))}
              <div style={{ marginTop: spacing.lg, display: 'flex', gap: spacing.xl, alignItems: 'baseline' }}>
                <div>
                  <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.8, color: p.textTertiary }}>MONEY HERO</div>
                  <div style={{ fontSize: 34, lineHeight: '40px', fontWeight: 900, letterSpacing: -0.75, color: p.textPrimary }}>₹1,450.00</div>
                </div>
                <div>
                  <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.8, color: p.textTertiary }}>DEVANAGARI</div>
                  <div style={{ fontSize: 26, lineHeight: '32px', fontWeight: 800, color: p.textPrimary }}>₹१,४५०.००</div>
                </div>
              </div>
            </Card>
          </>
        )}

        {section === 'icons' && (
          <>
            <SectionTitle p={p} title="Original icon set" sub="64 hand-authored glyphs on a 24 dp grid, 1.8 dp round stroke. Generated from tools/design_system/drive_icons.py into Android VectorDrawables and this preview — one source of truth." />
            <Card p={p} pad={spacing.xl}>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(96px, 1fr))', gap: spacing.lg }}>
                {Object.entries(icons).map(([name, spec]) => (
                  <div key={name} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
                    <div style={{ width: 52, height: 52, borderRadius: radius.cardSmall, background: p.surfaceSunken, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Icon name={name} icons={icons} size={26} color={p.iconPrimary} />
                    </div>
                    <span style={{ fontSize: 10, color: p.textSecondary, textAlign: 'center' }}>{name}</span>
                  </div>
                ))}
              </div>
            </Card>
          </>
        )}

        {section === 'illustrations' && (
          <>
            <SectionTitle p={p} title="Vector illustrations" sub="Drawn as code on a 100-unit grid with the icon set's optical language — scales losslessly and recolors per theme. Never bitmaps, screenshots or stock art." />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: spacing.lg }}>
              {[
                ['empty', 'No orders yet'],
                ['offline', 'Offline duty'],
                ['verification', 'KYC under review'],
                ['wallet', 'Wallet & payouts'],
                ['error', 'Recoverable error'],
                ['safety', 'Safety centre'],
              ].map(([kind, label]) => (
                <Card key={kind} p={p} pad={spacing.lg}>
                  <Illustration kind={kind} p={p} />
                  <div style={{ textAlign: 'center', fontSize: 12, fontWeight: 600, color: p.textSecondary, marginTop: spacing.sm }}>{label}</div>
                </Card>
              ))}
            </div>
          </>
        )}

        {section === 'components' && (
          <>
            <SectionTitle p={p} title="Component library" sub="Buttons, chips, cards, wells and the map surface — every colour resolved from semantic tokens." />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: spacing.lg }}>
              <Card p={p} pad={spacing.lg}>
                <div style={{ fontSize: 13, fontWeight: 700, marginBottom: spacing.md }}>Buttons ≥ 48 dp</div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: spacing.sm }}>
                  <Button p={p} label={t('goOnline')} />
                  <Button p={p} label={t('accept')} variant="success" />
                  <Button p={p} label={t('decline')} variant="outline" />
                  <Button p={p} label="SOS" variant="danger" />
                  <Button p={p} label="Disabled" disabled />
                </div>
              </Card>
              <Card p={p} pad={spacing.lg}>
                <div style={{ fontSize: 13, fontWeight: 700, marginBottom: spacing.md }}>Status chips — text + icon + tint</div>
                <div style={{ display: 'flex', gap: spacing.sm, flexWrap: 'wrap' }}>
                  <Chip p={p} label={t('online')} bg={p.successContainer} fg={p.onSuccessContainer} dot={p.success} />
                  <Chip p={p} label={t('offline')} bg={p.neutralContainer} fg={p.onNeutralContainer} dot={p.neutral} />
                  <Chip p={p} label="Blocked" bg={p.dangerContainer} fg={p.onDangerContainer} dot={p.danger} />
                  <Chip p={p} label="Expiring" bg={p.warningContainer} fg={p.onWarningContainer} dot={p.warning} />
                </div>
                <div style={{ fontSize: 13, fontWeight: 700, margin: `${spacing.lg}px 0 ${spacing.md}` }}>Icon wells</div>
                <div style={{ display: 'flex', gap: spacing.md }}>
                  {[
                    ['wallet', p.success],
                    ['shield_check', p.brand],
                    ['timer', p.warning],
                    ['sos', p.danger],
                  ].map(([ic, c]) => (
                    <div key={ic as string} style={{ width: 44, height: 44, borderRadius: radius.control, background: p.isDark ? 'rgba(92,168,230,0.18)' : 'rgba(23,105,170,0.10)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                      <Icon name={ic as string} icons={icons} size={22} color={c as string} />
                    </div>
                  ))}
                </div>
              </Card>
              <Card p={p} pad={0} style={{ overflow: 'hidden' }}>
                <div style={{ fontSize: 13, fontWeight: 700, padding: spacing.lg, paddingBottom: spacing.sm }}>Professional map interface</div>
                <MapSurface p={p} />
              </Card>
            </div>
          </>
        )}

        {section === 'states' && (
          <>
            <SectionTitle p={p} title="Required screen states" sub="Every network-backed screen ships loading, empty, error, offline and permission states — with original illustrations, never fake data." />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: spacing.lg }}>
              <Card p={p} pad={spacing.lg}>
                <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: spacing.md, padding: spacing.lg }}>
                  <div style={{ width: 34, height: 34, borderRadius: 999, border: `3px solid ${p.border}`, borderTopColor: p.brand, animation: 'spin 900ms linear infinite' }} />
                  <div style={{ fontSize: 13, color: p.textSecondary }}>Syncing partner profile…</div>
                </div>
              </Card>
              <Card p={p} pad={spacing.lg}>
                <Illustration kind="empty" p={p} />
                <div style={{ textAlign: 'center', marginTop: spacing.md }}>
                  <div style={{ fontSize: 14, fontWeight: 700 }}>{t('noOrders')}</div>
                  <div style={{ fontSize: 12, color: p.textSecondary, marginTop: 2 }}>{t('noOrdersSub')}</div>
                </div>
              </Card>
              <Card p={p} pad={spacing.lg}>
                <Illustration kind="error" p={p} />
                <div style={{ textAlign: 'center', marginTop: spacing.md }}>
                  <div style={{ fontSize: 14, fontWeight: 700 }}>Something went wrong</div>
                  <div style={{ fontSize: 12, color: p.textSecondary, marginTop: 2 }}>Check your connection and retry.</div>
                  <div style={{ marginTop: spacing.md }}>
                    <Button p={p} label="Try again" variant="outline" />
                  </div>
                </div>
              </Card>
              <Card p={p} pad={spacing.lg}>
                <Illustration kind="offline" p={p} />
                <div style={{ textAlign: 'center', marginTop: spacing.md }}>
                  <div style={{ fontSize: 14, fontWeight: 700 }}>{t('offline')}</div>
                  <div style={{ fontSize: 12, color: p.textSecondary, marginTop: 2 }}>Duty location tracking is stopped.</div>
                </div>
              </Card>
            </div>
            <style>{'@keyframes spin { to { transform: rotate(360deg); } }'}</style>
          </>
        )}

        {section === 'screens' && (
          <>
            <SectionTitle p={p} title="App screens" sub="The same tokens composed into the driver workflow: dashboard, dispatch offer, active trip navigation and earnings." />
            <div style={{ display: 'flex', gap: spacing.xl, flexWrap: 'wrap' }}>
              <Phone p={p} label="Active trip — navigation handoff">
                <TripPhone p={p} lang={lang} icons={icons} />
              </Phone>
              <Phone p={p} label="Earnings & wallet">
                <EarningsPhone p={p} lang={lang} icons={icons} />
              </Phone>
              <Phone p={p} label="Home (secondary canvas)">
                <HomeScreenPhone p={p} lang={lang} icons={icons} />
              </Phone>
            </div>
          </>
        )}

        {section === 'access' && (
          <>
            <SectionTitle p={p} title="Accessibility & responsive layout" sub="Enforced structurally by the Android design system, not per screen." />
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: spacing.lg }}>
              {[
                ['power', 'Touch targets', '48 dp minimum, 52 dp for duty/accept/withdraw. Icon-only controls keep a 48 dp hit box with a content description.'],
                ['eye', 'Contrast', 'textPrimary 16.5:1, textSecondary 4.7:1 on white; every dark-theme text pair ≥ 4.5:1. Borders that carry meaning use borderStrong ≥ 3:1.'],
                ['translate', 'Never colour alone', 'Status chips, duty control and ledger deltas always pair semantic tint with text and an icon; TalkBack merges each card into one node.'],
                ['settings', 'Responsive', 'Window size classes at 600/840 dp drive gutter (16/24/32 dp), grid columns (2/3/4) and reading measure; layouts reflow at 1.2× and 1.45× font scale.'],
                ['clock', 'Motion', '90–420 ms tokens; countdowns are driven by server remaining time, never by easing; durations collapse to 0 ms when animations are disabled.'],
                ['shield_check', 'Platform range', 'minSdk 24 (Android 7.0): vectors, per-app locales and edge-to-edge all degrade gracefully; targetSdk 35 edge-to-edge is explicit.'],
              ].map(([ic, title, body]) => (
                <Card key={title} p={p} pad={spacing.lg}>
                  <div style={{ display: 'flex', gap: spacing.md }}>
                    <div style={{ width: 40, height: 40, borderRadius: radius.control, background: p.brandContainer, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                      <Icon name={ic} icons={icons} size={20} color={p.brand} />
                    </div>
                    <div>
                      <div style={{ fontSize: 14, fontWeight: 700 }}>{title}</div>
                      <div style={{ fontSize: 13, color: p.textSecondary, marginTop: 2 }}>{body}</div>
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          </>
        )}
      </main>
    </div>
  );
}
