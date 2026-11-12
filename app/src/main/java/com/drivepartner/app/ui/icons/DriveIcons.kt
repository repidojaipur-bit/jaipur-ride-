package com.drivepartner.app.ui.icons

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
    val Home = DriveIconSpec(R.drawable.ic_drv_home, "home", "Navigation", "Home")
    val Orders = DriveIconSpec(R.drawable.ic_drv_orders, "orders", "Navigation", "Orders")
    val Earnings = DriveIconSpec(R.drawable.ic_drv_earnings, "earnings", "Navigation", "Earnings")
    val Rewards = DriveIconSpec(R.drawable.ic_drv_rewards, "rewards", "Navigation", "Rewards")
    val Profile = DriveIconSpec(R.drawable.ic_drv_profile, "profile", "Navigation", "Profile")
    val Bell = DriveIconSpec(R.drawable.ic_drv_bell, "bell", "Navigation", "Notifications")
    val Menu = DriveIconSpec(R.drawable.ic_drv_menu, "menu", "Navigation", "Menu")
    val Settings = DriveIconSpec(R.drawable.ic_drv_settings, "settings", "Navigation", "Settings")
    val Support = DriveIconSpec(R.drawable.ic_drv_support, "support", "Navigation", "Support")
    val Sos = DriveIconSpec(R.drawable.ic_drv_sos, "sos", "Navigation", "Emergency SOS")
    val Logout = DriveIconSpec(R.drawable.ic_drv_logout, "logout", "Navigation", "Sign out")
    val Bike = DriveIconSpec(R.drawable.ic_drv_bike, "bike", "Service", "Bike taxi")
    val Auto = DriveIconSpec(R.drawable.ic_drv_auto, "auto", "Service", "Auto rickshaw")
    val Cab = DriveIconSpec(R.drawable.ic_drv_cab, "cab", "Service", "Cab")
    val Delivery = DriveIconSpec(R.drawable.ic_drv_delivery, "delivery", "Service", "Delivery")
    val Power = DriveIconSpec(R.drawable.ic_drv_power, "power", "Status", "Duty power")
    val CheckCircle = DriveIconSpec(R.drawable.ic_drv_check_circle, "check_circle", "Status", "Confirmed")
    val AlertTriangle = DriveIconSpec(R.drawable.ic_drv_alert_triangle, "alert_triangle", "Status", "Attention required")
    val Info = DriveIconSpec(R.drawable.ic_drv_info, "info", "Status", "Information")
    val ShieldCheck = DriveIconSpec(R.drawable.ic_drv_shield_check, "shield_check", "Status", "Safety verified")
    val Clock = DriveIconSpec(R.drawable.ic_drv_clock, "clock", "Status", "Elapsed time")
    val Timer = DriveIconSpec(R.drawable.ic_drv_timer, "timer", "Status", "Countdown")
    val WifiOff = DriveIconSpec(R.drawable.ic_drv_wifi_off, "wifi_off", "Status", "Connection lost")
    val Star = DriveIconSpec(R.drawable.ic_drv_star, "star", "Status", "Rating")
    val UserCheck = DriveIconSpec(R.drawable.ic_drv_user_check, "user_check", "Status", "Partner verified")
    val IdBadge = DriveIconSpec(R.drawable.ic_drv_id_badge, "id_badge", "Status", "Partner ID")
    val Inbox = DriveIconSpec(R.drawable.ic_drv_inbox, "inbox", "Status", "Nothing to show")
    val MapPin = DriveIconSpec(R.drawable.ic_drv_map_pin, "map_pin", "Map", "Location")
    val Navigation = DriveIconSpec(R.drawable.ic_drv_navigation, "navigation", "Map", "Navigate")
    val Crosshair = DriveIconSpec(R.drawable.ic_drv_crosshair, "crosshair", "Map", "Current location")
    val Route = DriveIconSpec(R.drawable.ic_drv_route, "route", "Map", "Trip route")
    val Globe = DriveIconSpec(R.drawable.ic_drv_globe, "globe", "Map", "Service area")
    val Flag = DriveIconSpec(R.drawable.ic_drv_flag, "flag", "Map", "Destination")
    val Check = DriveIconSpec(R.drawable.ic_drv_check, "check", "Action", "Accept")
    val Close = DriveIconSpec(R.drawable.ic_drv_close, "close", "Action", "Decline")
    val ChevronRight = DriveIconSpec(R.drawable.ic_drv_chevron_right, "chevron_right", "Action", "Open")
    val ChevronLeft = DriveIconSpec(R.drawable.ic_drv_chevron_left, "chevron_left", "Action", "Back")
    val ArrowLeft = DriveIconSpec(R.drawable.ic_drv_arrow_left, "arrow_left", "Action", "Back")
    val Refresh = DriveIconSpec(R.drawable.ic_drv_refresh, "refresh", "Action", "Retry")
    val Plus = DriveIconSpec(R.drawable.ic_drv_plus, "plus", "Action", "Add")
    val Copy = DriveIconSpec(R.drawable.ic_drv_copy, "copy", "Action", "Copy code")
    val Share = DriveIconSpec(R.drawable.ic_drv_share, "share", "Action", "Share")
    val Call = DriveIconSpec(R.drawable.ic_drv_call, "call", "Action", "Call")
    val Message = DriveIconSpec(R.drawable.ic_drv_message, "message", "Action", "Message")
    val Search = DriveIconSpec(R.drawable.ic_drv_search, "search", "Action", "Search")
    val Upload = DriveIconSpec(R.drawable.ic_drv_upload, "upload", "Action", "Upload document")
    val Download = DriveIconSpec(R.drawable.ic_drv_download, "download", "Action", "Download statement")
    val Camera = DriveIconSpec(R.drawable.ic_drv_camera, "camera", "Action", "Capture document")
    val Edit = DriveIconSpec(R.drawable.ic_drv_edit, "edit", "Action", "Edit")
    val Trash = DriveIconSpec(R.drawable.ic_drv_trash, "trash", "Action", "Remove")
    val ExternalLink = DriveIconSpec(R.drawable.ic_drv_external_link, "external_link", "Action", "Open externally")
    val MoreVertical = DriveIconSpec(R.drawable.ic_drv_more_vertical, "more_vertical", "Action", "More options")
    val Translate = DriveIconSpec(R.drawable.ic_drv_translate, "translate", "Action", "Switch language")
    val Wallet = DriveIconSpec(R.drawable.ic_drv_wallet, "wallet", "Money", "Wallet")
    val Bank = DriveIconSpec(R.drawable.ic_drv_bank, "bank", "Money", "Bank account")
    val RupeeNote = DriveIconSpec(R.drawable.ic_drv_rupee_note, "rupee_note", "Money", "Earning amount")
    val Zap = DriveIconSpec(R.drawable.ic_drv_zap, "zap", "Money", "Boost")
    val Fuel = DriveIconSpec(R.drawable.ic_drv_fuel, "fuel", "Money", "Fuel")
    val Chart = DriveIconSpec(R.drawable.ic_drv_chart, "chart", "Money", "Trend")
    val Calendar = DriveIconSpec(R.drawable.ic_drv_calendar, "calendar", "Money", "Period")
    val FileDoc = DriveIconSpec(R.drawable.ic_drv_file_doc, "file_doc", "Document", "Document")
    val Lock = DriveIconSpec(R.drawable.ic_drv_lock, "lock", "Document", "Secured")
    val Eye = DriveIconSpec(R.drawable.ic_drv_eye, "eye", "Document", "Reveal")
    val ShieldAlert = DriveIconSpec(R.drawable.ic_drv_shield_alert, "shield_alert", "Document", "Blocked")

    /** Every icon in group order — used by the in-app Design System gallery. */
    val all: List<DriveIconSpec> = listOf(
        Home,
        Orders,
        Earnings,
        Rewards,
        Profile,
        Bell,
        Menu,
        Settings,
        Support,
        Sos,
        Logout,
        Bike,
        Auto,
        Cab,
        Delivery,
        Power,
        CheckCircle,
        AlertTriangle,
        Info,
        ShieldCheck,
        Clock,
        Timer,
        WifiOff,
        Star,
        UserCheck,
        IdBadge,
        Inbox,
        MapPin,
        Navigation,
        Crosshair,
        Route,
        Globe,
        Flag,
        Check,
        Close,
        ChevronRight,
        ChevronLeft,
        ArrowLeft,
        Refresh,
        Plus,
        Copy,
        Share,
        Call,
        Message,
        Search,
        Upload,
        Download,
        Camera,
        Edit,
        Trash,
        ExternalLink,
        MoreVertical,
        Translate,
        Wallet,
        Bank,
        RupeeNote,
        Zap,
        Fuel,
        Chart,
        Calendar,
        FileDoc,
        Lock,
        Eye,
        ShieldAlert
    )

    /** Icons grouped for documentation and gallery sections. */
    val byGroup: Map<String, List<DriveIconSpec>> = all.groupBy { it.group }
}
