# Drive Partner — Original Icon Inventory

69 hand-authored glyphs on a 24 dp optical grid, 1.8 dp round stroke, round joins. Source of truth: `tools/design_system/drive_icons.py`.

Generated into `app/src/main/res/drawable/ic_drv_*.xml` (Android VectorDrawable) and `public/design/icons.json` (preview mirror). Icons are monochrome stroke artwork: tint comes from the semantic colour tokens, so every icon is correct in light and dark mode.

| Group | Icon | Resource | Meaning |
|---|---|---|---|
| Action | `arrow_left` | `@drawable/ic_drv_arrow_left` | Back |
| Action | `call` | `@drawable/ic_drv_call` | Call |
| Action | `camera` | `@drawable/ic_drv_camera` | Capture document |
| Action | `check` | `@drawable/ic_drv_check` | Accept |
| Action | `chevron_left` | `@drawable/ic_drv_chevron_left` | Back |
| Action | `chevron_right` | `@drawable/ic_drv_chevron_right` | Open |
| Action | `close` | `@drawable/ic_drv_close` | Decline |
| Action | `copy` | `@drawable/ic_drv_copy` | Copy code |
| Action | `download` | `@drawable/ic_drv_download` | Download statement |
| Action | `edit` | `@drawable/ic_drv_edit` | Edit |
| Action | `external_link` | `@drawable/ic_drv_external_link` | Open externally |
| Action | `message` | `@drawable/ic_drv_message` | Message |
| Action | `more_vertical` | `@drawable/ic_drv_more_vertical` | More options |
| Action | `plus` | `@drawable/ic_drv_plus` | Add |
| Action | `refresh` | `@drawable/ic_drv_refresh` | Retry |
| Action | `search` | `@drawable/ic_drv_search` | Search |
| Action | `share` | `@drawable/ic_drv_share` | Share |
| Action | `translate` | `@drawable/ic_drv_translate` | Switch language |
| Action | `trash` | `@drawable/ic_drv_trash` | Remove |
| Action | `upload` | `@drawable/ic_drv_upload` | Upload document |
| Document | `eye` | `@drawable/ic_drv_eye` | Reveal |
| Document | `file_doc` | `@drawable/ic_drv_file_doc` | Document |
| Document | `lock` | `@drawable/ic_drv_lock` | Secured |
| Document | `shield_alert` | `@drawable/ic_drv_shield_alert` | Blocked |
| Map | `bookmark` | `@drawable/ic_drv_bookmark` | Saved location |
| Map | `crosshair` | `@drawable/ic_drv_crosshair` | Current location |
| Map | `flag` | `@drawable/ic_drv_flag` | Destination |
| Map | `globe` | `@drawable/ic_drv_globe` | Service area |
| Map | `layers` | `@drawable/ic_drv_layers` | Map layers |
| Map | `map_pin` | `@drawable/ic_drv_map_pin` | Location |
| Map | `navigation` | `@drawable/ic_drv_navigation` | Navigate |
| Map | `route` | `@drawable/ic_drv_route` | Trip route |
| Map | `zoom_in` | `@drawable/ic_drv_zoom_in` | Zoom in |
| Map | `zoom_out` | `@drawable/ic_drv_zoom_out` | Zoom out |
| Money | `bank` | `@drawable/ic_drv_bank` | Bank account |
| Money | `calendar` | `@drawable/ic_drv_calendar` | Period |
| Money | `chart` | `@drawable/ic_drv_chart` | Trend |
| Money | `fuel` | `@drawable/ic_drv_fuel` | Fuel |
| Money | `rupee_note` | `@drawable/ic_drv_rupee_note` | Earning amount |
| Money | `wallet` | `@drawable/ic_drv_wallet` | Wallet |
| Money | `zap` | `@drawable/ic_drv_zap` | Boost |
| Navigation | `bell` | `@drawable/ic_drv_bell` | Notifications |
| Navigation | `earnings` | `@drawable/ic_drv_earnings` | Earnings |
| Navigation | `home` | `@drawable/ic_drv_home` | Home |
| Navigation | `logout` | `@drawable/ic_drv_logout` | Sign out |
| Navigation | `menu` | `@drawable/ic_drv_menu` | Menu |
| Navigation | `orders` | `@drawable/ic_drv_orders` | Orders |
| Navigation | `profile` | `@drawable/ic_drv_profile` | Profile |
| Navigation | `rewards` | `@drawable/ic_drv_rewards` | Rewards |
| Navigation | `settings` | `@drawable/ic_drv_settings` | Settings |
| Navigation | `sos` | `@drawable/ic_drv_sos` | Emergency SOS |
| Navigation | `support` | `@drawable/ic_drv_support` | Support |
| Service | `auto` | `@drawable/ic_drv_auto` | Auto rickshaw |
| Service | `bike` | `@drawable/ic_drv_bike` | Bike taxi |
| Service | `cab` | `@drawable/ic_drv_cab` | Cab |
| Service | `delivery` | `@drawable/ic_drv_delivery` | Delivery |
| Status | `alert_triangle` | `@drawable/ic_drv_alert_triangle` | Attention required |
| Status | `check_circle` | `@drawable/ic_drv_check_circle` | Confirmed |
| Status | `clock` | `@drawable/ic_drv_clock` | Elapsed time |
| Status | `id_badge` | `@drawable/ic_drv_id_badge` | Partner ID |
| Status | `inbox` | `@drawable/ic_drv_inbox` | Nothing to show |
| Status | `info` | `@drawable/ic_drv_info` | Information |
| Status | `power` | `@drawable/ic_drv_power` | Duty power |
| Status | `shield_check` | `@drawable/ic_drv_shield_check` | Safety verified |
| Status | `star` | `@drawable/ic_drv_star` | Rating |
| Status | `target` | `@drawable/ic_drv_target` | Daily target |
| Status | `timer` | `@drawable/ic_drv_timer` | Countdown |
| Status | `user_check` | `@drawable/ic_drv_user_check` | Partner verified |
| Status | `wifi_off` | `@drawable/ic_drv_wifi_off` | Connection lost |

## Usage rules

1. Render through `DriveIcon` so size, tint and accessibility semantics stay consistent.
2. Icon-only controls must pass a `contentDescription`; decorative icons must not.
3. Never resize below 20 dp — daylight legibility floor (`DriveAccessibility.minIconSize`).
4. New icons are added to the generator, never hand-edited into `res/drawable`.
