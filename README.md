# Kosh — private, local-first personal finance for Android

Kosh (कोश — "treasury") tracks your income, expenses, savings goals, and net worth **entirely on your device**. No account, no server, no analytics. Bank SMS are parsed on-device and every detected transaction waits for your approval before it is recorded.

## Features

### Money in / money out
- **SMS capture with approval** — a broadcast receiver parses incoming bank/UPI SMS in real time (Indian formats: `debited`, `credited`, `Rs./INR/₹`, A/c tails, VPAs). You can also scan your existing inbox (1 month / 3 months / 1 year) from More. Nothing is added without your explicit approval; each item can be edited or rejected in the Review queue (reachable from both Home and Activity). OTPs, offers, payment requests, balance alerts, IPO/ASBA blocks, and mandates are filtered out.
- **Smarter parsing of messy real-world SMS** — limit/balance figures ("Avl Limit: INR …") are never mistaken for the transaction amount; foreign-currency spends ("USD 23.60 spent…") are captured with the foreign value and flagged so you can correct it to INR; credit-card bill payments are suggested as *transfers* (the card-side "payment received" confirmation is dropped); and when a bank reports the same transaction in two SMS, the second report (same amount, same direction, within 10 minutes) is skipped.
- **Manual entry** — add/edit/delete transactions with amount, type, category, merchant, note, and date.
- **Categories** — 20 sensible defaults with emoji + color; create, edit, and delete your own.
- **Auto-categorization** — merchant keywords (Swiggy → Food, Netflix → Entertainment, BigBasket → Groceries, salary → Income, …) suggest a category for each detected SMS.
- **Search & filter** — free-text search plus type/category filter chips, grouped by day with daily net.

### Insights
- Overview at Day / Week / Month / Year with previous/next navigation: income vs expenses, category donut, spending trend, top merchants, average daily spend, savings rate, biggest expense.
- Compare tab: **MoM / QoQ / YoY** with deltas, grouped bars, and biggest category movers.

### Wealth (net worth)
- Manual asset ledger: add each holding — mutual funds, stocks, EPF/PPF/NPS, FDs, gold, crypto, property, cash — with platform and current value; loans as liabilities.
- Update values whenever you like; every update is kept as history, giving a **net worth trend** chart (assets − liabilities).
- Per-holding **returns** when you record the invested amount (absolute + %).
- Deliberately manual: Kosh never connects to brokers or banks. (Planned phase-2: on-device import of CAMS/KFintech CAS statements to bulk-update MF/stock values.)

### Budgets & Goals
- **Monthly budgets** per expense category with progress bars and overspend highlighting (home-screen snapshot of the top 3).
- **Event budgets** — set aside an amount for a trip, wedding, or festival, then tag expenses to it from the add/edit transaction screen; the budget shows planned vs actually spent vs remaining. Deleting a budget keeps the expenses, just untagged.
- **Goals** — save for a target: target amount, optional target date, manual contribution log, progress and days-left. Income transactions can also be tagged to a goal and count toward it automatically.
- **Cash flow vs net worth are deliberately separate**: income/expenses track monthly flow; the Wealth tab tracks holdings you value manually (update your Bank/Cash asset periodically). No auto-linking, no double counting.

### Automation & protection
- **App lock** — biometric / device PIN gate on launch and whenever the app returns from background (BiometricPrompt; devices without a screen lock are never locked out).
- **Block screenshots** (on by default) — `FLAG_SECURE` keeps balances out of screenshots, screen recordings, and the Recents preview; can be turned off in More → Security.
- **Notification capture** — an optional `NotificationListenerService` detects transactions from bank-app and messaging notifications, feeding the same parser, dedupe, and approval queue as SMS. This is the Play-Store-compatible path (no SMS permissions needed). Limitation: it only sees notifications posted while enabled — history must come from an SMS inbox scan or a backup restore.
- **Recurring transactions** — rent, SIPs, salary, subscriptions posted automatically on a chosen day each month; missed months are back-filled on next launch or by the daily background worker.
- **Smart notifications** (opt-in) — budget alerts at 80%/100% per category (once per month each), a daily "SMS waiting for review" nudge, and a month-end summary on the 1st.
- **Transfers** — a third transaction type for money moved between your own accounts, excluded from all income/expense stats.
- **Subscription detector** — Insights flags likely subscriptions (same merchant, same amount, ~monthly cadence) with the expected next charge date.
- **Split transactions** — divide one bill across multiple categories from the edit screen.
- **Home-screen widget** — this month's net, income, and expenses (Jetpack Glance); tap to open the app.

### Backup & export
- *Encrypted backup*: AES-256-GCM, key derived from your passphrase (PBKDF2-HMAC-SHA256, 200k iterations; 8-character minimum). Saved through the system file picker, so you can write it **directly into Google Drive or OneDrive without the app having any cloud access**. Covers transactions, categories, budgets, assets, value history, goals, and contributions (v2 format; v1 backups still restore).
- *Restore* from a backup file (replaces current data).
- *CSV export* of all transactions.
- *CSV import*: transactions (`date,type,amount,category,merchant,note` — duplicates skipped, unknown categories created) and holdings (`name,platform,type,invested,current` — updates Wealth values in bulk; works with data exported from brokers/CAS tools).
- Android Auto Backup of the database to your Google account as a safety net.

### Design
- Material 3 with a fixed emerald brand palette (consistent identity in light and dark, instead of wallpaper-derived dynamic color).
- Five-tab navigation — Home, Activity, Insights, Wealth, More — with outlined/filled icon states and a pending-review badge.
- **Adaptive layout**: on wide windows (≥600dp — tablets, foldables, phones in landscape) the bottom bar becomes a side NavigationRail and content is capped at a readable 720dp; all dialogs scroll on short screens.
- Quick actions on Home (Goals, Wealth, Categories, Insights).

## Privacy model

1. All data lives in a local Room/SQLite database inside app-private storage.
2. The app has **no INTERNET permission** — it cannot send data anywhere.
3. Backups are encrypted before they leave the app; the passphrase is never stored.
4. SMS access is read-only, on-device, and used solely for transaction detection.

## Building

Open the project in Android Studio and run, or from the terminal:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

- compileSdk/targetSdk 36, minSdk 26 (Android 8.0+)
- Kotlin 2.1 + Jetpack Compose (Material 3), Room (KSP, schema v2 with v1→v2 migration), DataStore, Navigation Compose
- No third-party dependencies beyond AndroidX + kotlinx-serialization

## Project layout

```
app/src/main/java/com/neeraj/fin/
├── FinApp.kt                  # Application + manual DI container
├── MainActivity.kt            # NavHost + 5-tab bottom navigation
├── data/
│   ├── db/                    # Room entities (txns, categories, budgets, assets,
│   │                          # asset values, goals, contributions), DAOs, migration
│   ├── sms/                   # SmsParser (regex heuristics), SmsReceiver, SmsImporter
│   ├── backup/BackupManager.kt# Encrypted backup / restore / CSV (v2 format)
│   ├── FinRepository.kt       # Single repository over all DAOs
│   └── SettingsStore.kt       # DataStore preferences (currency, auto-capture)
├── ui/
│   ├── AppViewModel.kt        # Shared ViewModel (flows + actions)
│   ├── components/            # Charts (donut/bars/progress), shared rows & dialogs
│   ├── screens/               # Home, Transactions, EditTxn, Review, Insights,
│   │                          # Wealth, Categories, Budgets & Goals, More
│   └── theme/Theme.kt         # Kosh emerald palette (light/dark)
└── util/                      # Money/date formatting, period & comparison math
```

## Notes

- **Google Play**: `READ_SMS`/`RECEIVE_SMS` are restricted permissions; Play will not accept this app without a special exception. For personal use, install the APK directly (sideload) — which also fits the privacy story.
- SMS parsing is heuristic. Unrecognized formats simply don't appear; nothing is ever auto-added.
