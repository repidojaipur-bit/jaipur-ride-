#!/usr/bin/env python3
"""
Drive Partner — Original iconography source of truth.

Every glyph below is hand-authored geometry for this product on a 24 x 24 optical grid with a
1.8 unit round-cap/round-join stroke. Nothing is traced from a competitor icon set and nothing is
copied from a third-party font; the shapes are generated from these path definitions so the
Android vector drawables, the design-system preview and the documentation can never drift apart.

Run:  python3 tools/design_system/drive_icons.py
Emits:
  app/src/main/res/drawable/ic_drv_<name>.xml      (Android VectorDrawable, stroke based)
  design/icons.json                                (mirrored geometry for the preview site)
  docs/design/ICON_INVENTORY.md                    (inventory table for reviewers)
"""

from __future__ import annotations

import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES_DIR = ROOT / "app/src/main/res/drawable"
DESIGN_DIR = ROOT / "public" / "design"
DOCS_DIR = ROOT / "docs/design"

VIEWPORT = 24.0
SIZE_DP = 24
STROKE_WIDTH = 1.8
PREFIX = "ic_drv_"


def circle(cx: float, cy: float, r: float) -> str:
    """Full circle as two arcs so it renders identically in Android and SVG."""
    left = round(cx - r, 3)
    right = round(cx + r, 3)
    return (
        f"M{left} {cy} A{r} {r} 0 1 1 {right} {cy} "
        f"A{r} {r} 0 1 1 {left} {cy} Z"
    )


def rounded_rect(x: float, y: float, w: float, h: float, r: float) -> str:
    return (
        f"M{x + r} {y} H{x + w - r} A{r} {r} 0 0 1 {x + w} {y + r} "
        f"V{y + h - r} A{r} {r} 0 0 1 {x + w - r} {y + h} "
        f"H{x + r} A{r} {r} 0 0 1 {x} {y + h - r} "
        f"V{y + r} A{r} {r} 0 0 1 {x + r} {y} Z"
    )


# name -> (group, label, [stroke paths], [filled paths])
ICONS: dict[str, tuple[str, str, list[str], list[str]]] = {
    # ------------------------------------------------------------------ navigation
    "home": (
        "Navigation", "Home",
        ["M3.6 10.8 L12 4.2 L20.4 10.8",
         "M5.9 9.7 V19 Q5.9 20.1 7 20.1 H17 Q18.1 20.1 18.1 19 V9.7",
         "M9.9 20.1 V14.7 H14.1 V20.1"], []),
    "orders": (
        "Navigation", "Orders",
        ["M6.2 3.8 H17.8 V19 L15.9 20.5 L14 19 L12 20.5 L10 19 L8.1 20.5 L6.2 19 Z",
         "M9.2 8.4 H14.8", "M9.2 12 H13.2"], []),
    "earnings": (
        "Navigation", "Earnings",
        [circle(12, 12, 8.6),
         "M9.5 8 H14.6", "M9.5 11 H14.6",
         "M13 8 C13 11.2 9.5 11 9.5 11 L14.6 16.4"], []),
    "rewards": (
        "Navigation", "Rewards",
        ["M8.2 4.2 H15.8 V8.8 A3.8 3.8 0 0 1 8.2 8.8 Z",
         "M8.2 5.6 H5.6 V7.2 A3.2 3.2 0 0 0 8.6 10.4",
         "M15.8 5.6 H18.4 V7.2 A3.2 3.2 0 0 1 15.4 10.4",
         "M12 12.6 V16.4", "M9.2 19.8 H14.8", "M9.6 16.4 H14.4 L15 19.8 H9 Z"], []),
    "profile": (
        "Navigation", "Profile",
        [circle(12, 8.4, 3.7),
         "M4.9 20 C4.9 16.3 8.1 14.2 12 14.2 C15.9 14.2 19.1 16.3 19.1 20"], []),
    "bell": (
        "Navigation", "Notifications",
        ["M6.5 10.6 A5.5 5.5 0 0 1 17.5 10.6 C17.5 14.6 18.9 16.2 18.9 16.2 H5.1 C5.1 16.2 6.5 14.6 6.5 10.6 Z",
         "M10 19 A2.2 2.2 0 0 0 14 19"], []),
    "menu": (
        "Navigation", "Menu",
        ["M4 7.2 H20", "M4 12 H20", "M4 16.8 H20"], []),
    "settings": (
        "Navigation", "Settings",
        ["M3.8 7.6 H20.2", "M3.8 12 H20.2", "M3.8 16.4 H20.2",
         circle(9.2, 7.6, 2.1), circle(15.4, 12, 2.1), circle(7.6, 16.4, 2.1)], []),
    "support": (
        "Navigation", "Support",
        ["M4.6 15.4 V12 A7.4 7.4 0 0 1 19.4 12 V15.4",
         rounded_rect(3.4, 14.2, 3.6, 5.6, 1.5),
         rounded_rect(17, 14.2, 3.6, 5.6, 1.5),
         "M18.8 19.8 V20.4 A1.6 1.6 0 0 1 17.2 22 H14"], []),
    "sos": (
        "Navigation", "Emergency SOS",
        [circle(12, 12, 8.8), circle(12, 12, 3.6),
         "M5.8 5.8 L9.5 9.5", "M18.2 5.8 L14.5 9.5",
         "M5.8 18.2 L9.5 14.5", "M18.2 18.2 L14.5 14.5"], []),
    "logout": (
        "Navigation", "Sign out",
        ["M9.8 4.4 H6.2 A2 2 0 0 0 4.2 6.4 V17.6 A2 2 0 0 0 6.2 19.6 H9.8",
         "M15.2 8 L19.4 12 L15.2 16", "M19.4 12 H9"], []),

    # ------------------------------------------------------------------ service types
    "bike": (
        "Service", "Bike taxi",
        [circle(5.9, 17.1, 2.9), circle(18.1, 17.1, 2.9),
         "M5.9 17.1 L9.4 10.8 H13.6 L15.6 14.6",
         "M9.4 10.8 L8.2 7.8 H11.6",
         "M18.1 17.1 L19.6 9.6 H16.4"], []),
    "auto": (
        "Service", "Auto rickshaw",
        ["M4.2 17 V11.6 C4.2 8.6 6.6 6.4 9.8 6.4 H13.2 C16.8 6.4 19.4 8.8 19.4 12 V17 Z",
         "M7.4 11.8 V9.2 H12.2 V11.8 Z",
         circle(7.4, 18.6, 1.7), circle(16.6, 18.6, 1.7),
         "M15.4 14.6 H19.4"], []),
    "cab": (
        "Service", "Cab",
        ["M3.8 15.4 L5.4 10.2 C5.7 9.1 6.8 8.3 8 8.3 H16 C17.2 8.3 18.3 9.1 18.6 10.2 L20.2 15.4",
         "M3.8 15.4 V17.6 H20.2 V15.4 Z",
         "M6.8 12.6 H17.2",
         circle(7.4, 18.9, 1.5), circle(16.6, 18.9, 1.5)], []),
    "delivery": (
        "Service", "Delivery",
        ["M12 3.6 L20.2 8 V16 L12 20.4 L3.8 16 V8 Z",
         "M3.8 8 L12 12.4 L20.2 8", "M12 12.4 V20.4",
         "M7.9 5.8 L16.1 10.2"], []),

    # ------------------------------------------------------------------ duty & status
    "power": (
        "Status", "Duty power",
        ["M12 3.6 V11.6", "M7.3 6.7 A6.9 6.9 0 1 0 16.7 6.7"], []),
    "check_circle": (
        "Status", "Confirmed",
        [circle(12, 12, 8.6), "M8.2 12.4 L11 15.2 L16.1 9.1"], []),
    "alert_triangle": (
        "Status", "Attention required",
        ["M12 4.1 L21.1 19.5 H2.9 Z", "M12 9.8 V13.9", "M12 16.6 V16.9"], []),
    "info": (
        "Status", "Information",
        [circle(12, 12, 8.6), "M12 11.2 V16.6", "M12 7.9 V8.2"], []),
    "shield_check": (
        "Status", "Safety verified",
        ["M12 3.4 L19.6 6.2 V12 C19.6 16.6 16.1 19.9 12 21 C7.9 19.9 4.4 16.6 4.4 12 V6.2 Z",
         "M8.8 12.1 L11.2 14.5 L15.5 9.7"], []),
    "clock": (
        "Status", "Elapsed time",
        [circle(12, 12, 8.6), "M12 7.4 V12.2 L15.5 14.2"], []),
    "timer": (
        "Status", "Countdown",
        [circle(12, 13.4, 7.4), "M9.6 3.4 H14.4", "M12 13.4 V9.8",
         "M18.4 6.2 L20.2 4.4"], []),
    "wifi_off": (
        "Status", "Connection lost",
        ["M3 8.6 C6.2 6 17.8 6 21 8.6", "M6.6 12.2 C8.6 10.6 15.4 10.6 17.4 12.2",
         "M9.9 15.7 C10.9 14.9 13.1 14.9 14.1 15.7", "M12 19.1 V19.4",
         "M3.6 3.6 L20.4 20.4"], []),
    "star": (
        "Status", "Rating",
        ["M12 3.9 L14.4 9.2 L20.2 9.9 L15.9 13.9 L17.1 19.6 L12 16.7 L6.9 19.6 L8.1 13.9 L3.8 9.9 L9.6 9.2 Z"], []),
    "user_check": (
        "Status", "Partner verified",
        [circle(10.2, 8.2, 3.6), "M3.6 19.8 C3.6 16.3 6.6 14.3 10.2 14.3 C11.4 14.3 12.5 14.5 13.4 14.9",
         "M15.2 18.2 L17.2 20.2 L21 16.2"], []),
    "target": (
        "Status", "Daily target",
        [circle(12, 12, 8.2), circle(12, 12, 4.4)], [circle(12, 12, 1.3)]),
    "id_badge": (
        "Status", "Partner ID",
        [rounded_rect(3.2, 5, 17.6, 14.6, 2.2), circle(9, 11, 2.2),
         "M5.8 16.6 C5.8 14.9 7.2 14 9 14 C10.8 14 12.2 14.9 12.2 16.6",
         "M15 10 H18.2", "M15 13.2 H18.2"], []),
    "inbox": (
        "Status", "Nothing to show",
        ["M3.6 13.4 L6 5.2 C6.3 4.3 7.1 3.7 8 3.7 H16 C16.9 3.7 17.7 4.3 18 5.2 L20.4 13.4 V18.4 A2 2 0 0 1 18.4 20.4 H5.6 A2 2 0 0 1 3.6 18.4 Z",
         "M3.6 13.4 H8.4 L9.6 15.8 H14.4 L15.6 13.4 H20.4"], []),

    # ------------------------------------------------------------------ map & location
    "map_pin": (
        "Map", "Location",
        ["M12 21 C12 21 19 15.2 19 10.4 A7 7 0 1 0 5 10.4 C5 15.2 12 21 12 21 Z",
         circle(12, 10.4, 2.6)], []),
    "navigation": (
        "Map", "Navigate",
        ["M20.4 3.6 L3.8 10.1 L10.4 12.9 L13.2 19.9 Z"], []),
    "crosshair": (
        "Map", "Current location",
        [circle(12, 12, 7.2), circle(12, 12, 2.2),
         "M12 2.4 V4.8", "M12 19.2 V21.6", "M2.4 12 H4.8", "M19.2 12 H21.6"], []),
    "route": (
        "Map", "Trip route",
        [circle(6.2, 6.2, 2.4), circle(17.8, 17.8, 2.4),
         "M8.6 6.2 H14.4 A3.6 3.6 0 0 1 14.4 13.4 H9.6 A3.6 3.6 0 0 0 9.6 17.8 H15.4"], []),
    "globe": (
        "Map", "Service area",
        [circle(12, 12, 8.6),
         "M3.6 12 H20.4",
         "M12 3.4 C14.6 6.2 14.6 17.8 12 20.6 C9.4 17.8 9.4 6.2 12 3.4 Z"], []),
    "flag": (
        "Map", "Destination",
        ["M5.6 21 V3.8", "M5.6 4.6 H18.4 L15.6 8.6 L18.4 12.6 H5.6"], []),
    "zoom_in": (
        "Map", "Zoom in",
        [circle(10.6, 10.6, 6.6),
         "M15.6 15.6 L20.6 20.6",
         "M10.6 7.9 V13.3", "M7.9 10.6 H13.3"], []),
    "zoom_out": (
        "Map", "Zoom out",
        [circle(10.6, 10.6, 6.6),
         "M15.6 15.6 L20.6 20.6",
         "M7.9 10.6 H13.3"], []),
    "layers": (
        "Map", "Map layers",
        ["M12 3.4 L20.6 7.9 L12 12.4 L3.4 7.9 Z",
         "M3.4 12.3 L12 16.8 L20.6 12.3",
         "M3.4 16.5 L12 21 L20.6 16.5"], []),
    "bookmark": (
        "Map", "Saved location",
        ["M6.6 3.9 H17.4 Q18.3 3.9 18.3 4.8 V20.5 L12 16.3 L5.7 20.5 V4.8 Q5.7 3.9 6.6 3.9 Z"], []),

    # ------------------------------------------------------------------ actions
    "check": ("Action", "Accept", ["M4.6 12.6 L9.6 17.6 L19.4 6.6"], []),
    "close": ("Action", "Decline", ["M6.4 6.4 L17.6 17.6", "M17.6 6.4 L6.4 17.6"], []),
    "chevron_right": ("Action", "Open", ["M9.4 5.2 L16.2 12 L9.4 18.8"], []),
    "chevron_left": ("Action", "Back", ["M14.6 5.2 L7.8 12 L14.6 18.8"], []),
    "arrow_left": ("Action", "Back", ["M19.4 12 H4.8", "M10.8 5.8 L4.6 12 L10.8 18.2"], []),
    "refresh": (
        "Action", "Retry",
        ["M20 12 A8 8 0 1 1 17.2 6", "M20.4 3.6 V8.4 H15.6"], []),
    "plus": ("Action", "Add", ["M12 5 V19", "M5 12 H19"], []),
    "copy": (
        "Action", "Copy code",
        [rounded_rect(8.6, 8.6, 11.4, 11.4, 2.2),
         "M15.4 5.4 A1.8 1.8 0 0 0 13.6 3.6 H5.8 A2.2 2.2 0 0 0 3.6 5.8 V13.6 A1.8 1.8 0 0 0 5.4 15.4"], []),
    "share": (
        "Action", "Share",
        [circle(17.8, 6.2, 2.6), circle(6.2, 12, 2.6), circle(17.8, 17.8, 2.6),
         "M8.6 10.8 L15.4 7.4", "M8.6 13.2 L15.4 16.6"], []),
    "call": (
        "Action", "Call",
        ["M6.4 3.8 H9.2 L10.6 7.6 L8.7 9 C9.8 11.5 11.9 13.5 14.3 14.5 L15.7 12.7 L19.5 14.1 V16.9 C19.5 18.1 18.5 19.1 17.3 19 C10.4 18.4 4.9 12.9 4.3 6 C4.2 4.8 5.2 3.8 6.4 3.8 Z"], []),
    "message": (
        "Action", "Message",
        ["M4 5.4 H20 V15.8 H10.4 L5.8 19.6 V15.8 H4 Z",
         "M7.6 9.2 H16.4", "M7.6 12.2 H13.4"], []),
    "search": (
        "Action", "Search",
        [circle(10.8, 10.8, 6.4), "M15.5 15.5 L20.4 20.4"], []),
    "upload": (
        "Action", "Upload document",
        ["M12 15.6 V4.4", "M7.8 8.6 L12 4.4 L16.2 8.6", "M4.6 19.6 H19.4"], []),
    "download": (
        "Action", "Download statement",
        ["M12 4 V15.2", "M7.8 11 L12 15.2 L16.2 11", "M4.6 19.6 H19.4"], []),
    "camera": (
        "Action", "Capture document",
        ["M3.2 9 H7 L8.6 6.4 H15.4 L17 9 H20.8 V19 H3.2 Z", circle(12, 13.8, 3.2)], []),
    "edit": (
        "Action", "Edit",
        ["M4.6 19.4 L5 15.6 L15.8 4.8 A2 2 0 0 1 18.6 4.8 L19.2 5.4 A2 2 0 0 1 19.2 8.2 L8.4 19 L4.6 19.4 Z",
         "M14.4 6.2 L17.8 9.6"], []),
    "trash": (
        "Action", "Remove",
        ["M4.6 6.6 H19.4", "M9.4 6.6 V4.6 H14.6 V6.6",
         "M6.6 6.6 L7.6 19.4 A1.6 1.6 0 0 0 9.2 20.8 H14.8 A1.6 1.6 0 0 0 16.4 19.4 L17.4 6.6",
         "M10.4 10.4 V17", "M13.6 10.4 V17"], []),
    "external_link": (
        "Action", "Open externally",
        ["M14 4.4 H19.6 V10", "M19.6 4.4 L11.4 12.6",
         "M18 14.4 V18.4 A2 2 0 0 1 16 20.4 H5.6 A2 2 0 0 1 3.6 18.4 V8 A2 2 0 0 1 5.6 6 H9.6"], []),
    "more_vertical": (
        "Action", "More options",
        [circle(12, 5.4, 1.5), circle(12, 12, 1.5), circle(12, 18.6, 1.5)], []),
    "translate": (
        "Action", "Switch language",
        ["M3.4 5.6 H12.2", "M7.8 3.6 V5.6",
         "M9.9 5.6 C9.9 9.7 6.5 12.7 3.6 13.7",
         "M5.7 9.2 C6.8 11.1 8.8 12.7 11.4 13.5",
         "M13.4 12.8 H20.6", "M17 10.8 V12.8",
         "M14.6 12.8 L17.5 20.4", "M20.1 12.8 L17.7 19"], []),

    # ------------------------------------------------------------------ money & documents
    "wallet": (
        "Money", "Wallet",
        ["M3.8 8 V6.8 A1.8 1.8 0 0 1 5.6 5 H15.4 A1.8 1.8 0 0 1 17.2 6.8 V8",
         rounded_rect(3.8, 8, 16.4, 11.4, 2.2),
         "M15.4 13.7 H20.2", circle(16.4, 13.7, 0.4)], []),
    "bank": (
        "Money", "Bank account",
        ["M3.4 9.4 L12 4.2 L20.6 9.4",
         "M5.6 9.4 V17.4", "M9.6 9.4 V17.4", "M14.4 9.4 V17.4", "M18.4 9.4 V17.4",
         "M3.4 19.8 H20.6"], []),
    "rupee_note": (
        "Money", "Earning amount",
        [rounded_rect(2.8, 6, 18.4, 12, 2.2),
         "M8.4 9.6 H13.4", "M8.4 12 H13.4",
         "M12 9.6 C12 12.6 8.4 12.4 8.4 12.4 L13.4 15.6",
         circle(18, 12, 0.4)], []),
    "zap": (
        "Money", "Boost",
        ["M13.6 2.8 L5.4 13.6 H11 L10.2 21.2 L18.6 10 H12.8 Z"], []),
    "fuel": (
        "Money", "Fuel",
        ["M4.6 20.4 V5.4 A1.8 1.8 0 0 1 6.4 3.6 H12 A1.8 1.8 0 0 1 13.8 5.4 V20.4",
         "M3.2 20.4 H15.2", "M6.6 8.6 H11.8 V12.4 H6.6 Z",
         "M13.8 9.4 H16.6 A2 2 0 0 1 18.6 11.4 V15.4 A1.6 1.6 0 0 0 20.2 17 A1.6 1.6 0 0 0 21.8 15.4 V8.6 L19.4 6.2"], []),
    "chart": (
        "Money", "Trend",
        ["M3.6 19.4 H20.4", "M6.4 19.4 V13.2", "M11 19.4 V8.4", "M15.6 19.4 V11.2", "M20 19.4 V5.6"], []),
    "calendar": (
        "Money", "Period",
        [rounded_rect(3.6, 5.2, 16.8, 15.2, 2.2),
         "M3.6 10 H20.4", "M8.2 3.4 V7", "M15.8 3.4 V7",
         "M7.6 13.6 H10", "M7.6 16.8 H13.2"], []),
    "file_doc": (
        "Document", "Document",
        ["M6.2 3.6 H13.8 L18.6 8.4 V20.4 H6.2 Z",
         "M13.8 3.6 V8.4 H18.6",
         "M9 13 H15.4", "M9 16.6 H13.4"], []),
    "lock": (
        "Document", "Secured",
        [rounded_rect(4.6, 10.4, 14.8, 9.8, 2.2),
         "M8.2 10.4 V7.8 A3.8 3.8 0 0 1 15.8 7.8 V10.4",
         "M12 14.2 V16.6"], []),
    "eye": (
        "Document", "Reveal",
        ["M2.6 12 C5.4 7.4 18.6 7.4 21.4 12 C18.6 16.6 5.4 16.6 2.6 12 Z",
         circle(12, 12, 2.9)], []),
    "shield_alert": (
        "Document", "Blocked",
        ["M12 3.4 L19.6 6.2 V12 C19.6 16.6 16.1 19.9 12 21 C7.9 19.9 4.4 16.6 4.4 12 V6.2 Z",
         "M12 8.2 V12.6", "M12 15.6 V15.9"], []),
}


def vector_xml(name: str, strokes: list[str], fills: list[str]) -> str:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        '<!--',
        f'  Original Drive Partner icon: {name}',
        '  Hand-authored geometry on a 24dp optical grid, 1.8dp round stroke.',
        '  Generated by tools/design_system/drive_icons.py — edit the generator, not this file.',
        '-->',
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{SIZE_DP}dp"',
        f'    android:height="{SIZE_DP}dp"',
        f'    android:viewportWidth="{int(VIEWPORT)}"',
        f'    android:viewportHeight="{int(VIEWPORT)}">',
    ]
    for d in strokes:
        lines += [
            '    <path',
            f'        android:pathData="{d}"',
            '        android:strokeColor="#FF17202A"',
            f'        android:strokeWidth="{STROKE_WIDTH}"',
            '        android:strokeLineCap="round"',
            '        android:strokeLineJoin="round" />',
        ]
    for d in fills:
        lines += [
            '    <path',
            f'        android:pathData="{d}"',
            '        android:fillColor="#FF17202A" />',
        ]
    lines.append('</vector>')
    lines.append('')
    return "\n".join(lines)


KOTLIN_PATH = ROOT / "app/src/main/java/com/drivepartner/app/ui/icons/DriveIcons.kt"

KOTLIN_HEADER = """package com.drivepartner.app.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.drivepartner.app.R

/**
 * Drive Partner — Original icon registry.
 *
 * Generated from tools/design_system/drive_icons.py (the single source of truth for the geometry).
 * Every glyph is original artwork on a 24 dp grid with a 1.8 dp round stroke; colour is never
 * baked into the vector so semantic tinting from [com.drivepartner.app.ui.theme.DriveColorTokens]
 * keeps each icon correct in light and dark mode.
 *
 * Render through `DriveIcon` (ui/components/DriveIcon.kt) for consistent size, tint and
 * accessibility semantics. `key` matches the drawable suffix, e.g. key "bike" -> ic_drv_bike.
 */
@Immutable
data class DriveIconSpec(
    @DrawableRes val resource: Int,
    val key: String,
    val group: String,
    val label: String
)

/** The original Drive Partner icon set. No third-party icon font is used anywhere in the app. */
object DriveIcons {
"""


def kotlin_registry() -> None:
    lines = [KOTLIN_HEADER]
    for name, (group, label, _s, _f) in ICONS.items():
        prop = "".join(part.capitalize() for part in name.split("_"))
        res = f"{PREFIX}{name}"
        lines.append(
            f'    val {prop} = DriveIconSpec(R.drawable.{res}, "{name}", "{group}", "{label}")\n'
        )
    lines.append("\n    /** Every icon in group order — used by the in-app Design System gallery. */\n")
    lines.append("    val all: List<DriveIconSpec> = listOf(\n")
    lines.append(",\n".join(
        "        " + "".join(part.capitalize() for part in name.split("_"))
        for name in ICONS
    ))
    lines.append("\n    )\n")
    lines.append("\n    /** Icons grouped for documentation and gallery sections. */\n")
    lines.append("    val byGroup: Map<String, List<DriveIconSpec>> = all.groupBy { it.group }\n")
    lines.append("}\n")
    KOTLIN_PATH.parent.mkdir(parents=True, exist_ok=True)
    KOTLIN_PATH.write_text("".join(lines), encoding="utf-8")
    print(f"kotlin -> {KOTLIN_PATH}")



def main() -> None:
    RES_DIR.mkdir(parents=True, exist_ok=True)
    DESIGN_DIR.mkdir(parents=True, exist_ok=True)
    DOCS_DIR.mkdir(parents=True, exist_ok=True)

    manifest: dict[str, dict] = {}
    for name, (group, label, strokes, fills) in ICONS.items():
        res_name = f"{PREFIX}{name}"
        (RES_DIR / f"{res_name}.xml").write_text(vector_xml(name, strokes, fills), encoding="utf-8")
        manifest[name] = {
            "resource": res_name,
            "group": group,
            "label": label,
            "viewport": int(VIEWPORT),
            "strokeWidth": STROKE_WIDTH,
            "strokes": strokes,
            "fills": fills,
        }

    payload = {
        "meta": {
            "viewport": int(VIEWPORT),
            "strokeWidth": STROKE_WIDTH,
            "strokeLineCap": "round",
            "strokeLineJoin": "round",
            "prefix": PREFIX,
            "count": len(manifest),
            "license": "Original Drive Partner iconography — not a third-party icon font.",
        },
        "icons": manifest,
    }
    (DESIGN_DIR / "icons.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")

    groups: dict[str, list[str]] = {}
    for name, (group, label, _, _) in ICONS.items():
        groups.setdefault(group, []).append(name)

    lines = [
        "# Drive Partner — Original Icon Inventory",
        "",
        f"{len(ICONS)} hand-authored glyphs on a 24 dp optical grid, 1.8 dp round stroke, "
        "round joins. Source of truth: `tools/design_system/drive_icons.py`.",
        "",
        "Generated into `app/src/main/res/drawable/ic_drv_*.xml` (Android VectorDrawable) and "
        "`public/design/icons.json` (preview mirror). Icons are monochrome stroke artwork: tint comes from "
        "the semantic colour tokens, so every icon is correct in light and dark mode.",
        "",
        "| Group | Icon | Resource | Meaning |",
        "|---|---|---|---|",
    ]
    for group in sorted(groups):
        for name in sorted(groups[group]):
            label = ICONS[name][1]
            lines.append(f"| {group} | `{name}` | `@drawable/{PREFIX}{name}` | {label} |")
    lines += [
        "",
        "## Usage rules",
        "",
        "1. Render through `DriveIcon` so size, tint and accessibility semantics stay consistent.",
        "2. Icon-only controls must pass a `contentDescription`; decorative icons must not.",
        "3. Never resize below 20 dp — daylight legibility floor (`DriveAccessibility.minIconSize`).",
        "4. New icons are added to the generator, never hand-edited into `res/drawable`.",
        "",
    ]
    (DOCS_DIR / "ICON_INVENTORY.md").write_text("\n".join(lines), encoding="utf-8")

    print(f"generated {len(ICONS)} icons -> {RES_DIR}")
    print(f"mirror -> {DESIGN_DIR / 'icons.json'}")
    print(f"docs   -> {DOCS_DIR / 'ICON_INVENTORY.md'}")
    kotlin_registry()


if __name__ == "__main__":
    main()
