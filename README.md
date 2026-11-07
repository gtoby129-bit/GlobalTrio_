# Global Trio – Android accounting app

Mirrors your workbook: Products per month (qty, buying, selling price), Customer sales
(date, customer, phone, category, sub-category, product, qty, Credit/Paid Up), and the
Month + Yearly summaries (Cost, Revenue, Profit, % Profit, Credit, Paid Up, Profit of items bought).
Works offline; syncs to your server when online.

## 1. Get the APK (no Android Studio needed)
1. Create a free GitHub repo, upload everything in this folder (keep the .github folder).
2. Open the Actions tab -> "Build APK" -> run it (or it runs on push).
3. Download artifact **GlobalTrio-apk** -> app-debug.apk -> install on the phone
   (allow "install unknown apps").
Alternative: open this folder in Android Studio and press Run / Build APK.

## 2. Run the online server
    cd server
    API_KEY=pick-a-long-secret PORT=3000 node server.js
Host it anywhere that runs Node (Render, Railway, Fly.io, a VPS). Put it behind HTTPS.
Data is saved in server/data.json (set DATA_FILE to a persistent disk path).

## 3. Connect the app
App -> Settings -> enter Server URL + API key -> Save -> Sync now.
Use Settings -> "Import workbook data" to load your October products and sales.

## 4. Link the OneDrive workbook (optional)
1. Upload Global_Trio_Business.xlsx to the top level of OneDrive (or set ONEDRIVE_FILE to its path).
2. portal.azure.com -> App registrations -> New registration. Supported account types:
   "any organizational directory and personal Microsoft accounts". Register, copy the Application (client) ID.
3. In that app: Authentication -> Allow public client flows = Yes. API permissions -> Add -> Microsoft Graph
   -> Delegated -> Files.ReadWrite.
4. On the server set MS_CLIENT_ID=<client id> and ONEDRIVE_FILE=/Global_Trio_Business.xlsx, redeploy.
5. App -> Settings -> Link OneDrive, enter the code shown at the Microsoft page, then Send everything to Excel.
New/edited/deleted sales and products then update the workbook's Customer Details and month tabs.

## Hosting alternatives
Render and Railway both deploy straight from the GitHub repo (set root directory to `server`,
start command `node server.js`, env vars API_KEY, DATA_FILE). Persistent storage is a paid add-on on both.
If the server's data is wiped, the app detects it and re-uploads everything from the phone.

### Excel workbook straight from the phone (Microsoft sign-in)
Settings -> Store my data in: Microsoft OneDrive -> set "Excel workbook path in OneDrive"
(e.g. /Global_Trio_Business.xlsx) -> "Write sales and products into the workbook": On -> Test Excel link -> Sync now.
Sales go to the month block on "Customer Details", products to the month tabs, so your formulas keep working.
Edits and deletes in the app update/clear the same rows. Custom categories are not written (not in the workbook layout).
Turn it on only after removing sales you already typed by hand, or they will appear twice.

### Export to Excel (works with any storage option)
Settings -> Data -> "Export to Excel (.xlsx)". Creates GlobalTrio-<date>.xlsx (sheets: Customer Details, Products, Summary)
in your Downloads folder and opens the Share menu, so you can send it to OneDrive, Drive or WhatsApp.
This is a standalone export of values, not your original workbook with formulas.
(Requires Android 10 or newer.)

### If sign-in says "not signed in" after approving
Open Settings -> tap "Finish sign-in". The app now remembers the pending sign-in even if Android restarts it
while you are in the browser, and finishes automatically when you return.

### Updating the app without losing data
app/debug.keystore is a fixed signing key, so every APK built from this repo is signed the same way and
installs OVER the previous one, keeping your data. (Before this key was added, each GitHub build had a random key,
and Android refused to update in place.) Keep the file in the repo.

### Credit customers who pay later
Sales tab -> "Owing (Credit)" filter (or search the customer's name) -> "Mark paid". The sale becomes Paid Up with today's
date, the Summary moves the amount from Credit to Paid, and (if linked) the Excel row's status updates too.
"Back to credit" undoes it. The top of the list shows the total outstanding credit.

## v8 additions
**Stock tab** - items with Bought / Sold / Left (bought across all months minus every sale). "Add stock" adds quantity to the
chosen month (creating the month's row with the latest prices if needed), so the workbook's month tab stays correct.
Low-stock warning level is adjustable. The Sales form also shows the stock left for the chosen product.

**Themes** - Settings -> Theme (Automatic / Light / Dark), or the moon/sun button in the header.

**Share with OneDrive** (Settings, and a button on Summary)
- Direct: sign in to Microsoft as the OneDrive owner (trio00011@outlook.com). The app uploads an Excel file and REPLACES
  Global_Trio_Business.xlsx in that OneDrive. The first time, the existing file is copied to
  Global_Trio_Business_ORIGINAL_BACKUP.xlsx and the app refuses to overwrite if it cannot confirm the backup.
  Needs the free Microsoft Application (client) ID (see section 4 - Azure registration, Files.ReadWrite + User.Read).
- Email: if Microsoft is not linked, the same button opens your email app with the Excel attached, addressed to trio00011@outlook.com.
  (An Outlook rule / Power Automate flow in that account can then save the attachment into OneDrive.)
- The shared file contains values (Customer Details, Products, Summary). To keep your formulas, turn on
  "Also fill my workbook's own sheets automatically" - the app then writes sales and products into the real workbook cells.

## v9 additions
- **Edit a sale** (Sales list -> Edit). Payments already recorded are kept; totals recalculate.
- **Part payments**: Add payment / Mark paid / Back to credit. Summary "Credit" = money still owed, "Paid" = cash received.
- **Customers tab**: balances, purchase history, WhatsApp reminder / statement / receipt, Call, and "Receive a payment"
  (applied to the oldest unpaid sales first). Set your country code in More > Settings so WhatsApp numbers work.
- **Expenses** (More > Expenses) and **Net profit** (Summary) = profit of items sold - expenses. Charts: sales per month, best sellers.
- **Stock history** with Undo; **low-stock alerts** (on-screen, plus notifications if enabled) when a sale/edit drops an item to the low level.
- **PIN lock, fingerprint, staff PIN** (More > Settings > Security). Staff see Sales, Customers and Stock only (no prices, profit,
  expenses, settings, sharing, deleting or new items). The PIN is stored as a salted hash. It stops casual access; it does not encrypt the data.
- **Weekly auto-backup**: when the app is opened and 7+ days have passed, an Excel backup is saved to Downloads (and to OneDrive/GlobalTrio Backups if linked).
- Navigation is now: Sales | Customers | Stock | Summary | More (Products, Expenses, Settings live under More).

## v10 - categories
- Shipping now has these built-in subcategories: N/A (Shipping), Temu, Amazon, P-Plug, Ebay, Shein, Dischem, TakeAlot.
- More > Categories & subcategories: add / rename / remove subcategories under ANY category, and add / rename / delete your own categories.
  Anything that already has products or sales can be renamed (all its records follow) but not removed.
  Built-in categories (Avroy Shlein, Inuka, Shipping) can have subcategories changed but cannot be renamed or deleted.
- Excel workbook link: every Shipping store is written into the workbook's single Shipping section (sub-category "N/A (Shipping)"),
  with the store added to the item name, e.g. "Temu - Parcel", so two stores never overwrite each other's rows.
  Other new subcategories/categories appear in the app, summaries and the Excel export, but are not written into the original workbook's fixed month-tab sections.

## v11 - protected category editing + manager alerts
- Changing categories/subcategories now needs the owner's fingerprint (or the owner PIN as the alternative). Without either set up, editing is blocked.
  The editor shows read-only until verified; verification lasts 2 minutes and is extended by each change. The staff PIN cannot unlock it.
- Every change is written to an Activity log (who, what, when, which phone) that syncs with the rest of your data (Google/OneDrive/own server).
- Other owner/manager phones are alerted when that change reaches them: an on-screen message and a phone notification
  (More > Settings > Alerts: your name + alerts On/Off). More > Activity & alerts lists the history; "Message managers" shares a summary on WhatsApp.
- There is no push service, so an alert appears when the other phone syncs: about every 3 minutes while the app is open, and when it is opened.
- All manager phones must run v11. The log is not tamper-proof against someone with technical access to a phone.

## v12 - Notepad
- More > Notepad (staff get a "Notes" tab). Write transactions or anything else as plain text: date, type (Note / Money in / Money out / Reminder),
  optional amount, free text (several lines). Search, filter (including Open reminders), edit, share on WhatsApp.
- Money in / Money out totals are shown for the notes on screen. Notes are NOT part of the Summary or profit.
- Reminders can be marked done / reopened. The More menu shows how many are open.
- Owner only: "Make expense" turns a Money-out note with an amount into a real expense (category Other, once only) and Delete (tap twice to confirm).
  Staff can add and edit notes (signed "Staff" unless a name is set) but cannot delete or convert them.
- Notes sync like the rest of your data (Google / OneDrive / own server) and appear in the Excel export (Notepad sheet).

## v13 - automatic sync
- Every change (a sale, a payment, stock, an expense, a note, a category...) is sent to your storage (Google / OneDrive / own server) about a second later. No button needed.
- Header status: "syncing…", "synced 10:32", "offline · 2 waiting" (changes are safe on the phone and are retried after 10 s, 20 s, 40 s ... up to 5 min), "sign in needed", "saved on phone only".
- A change made while another sync is running is sent straight afterwards (it used to wait for the next check). A record edited during an upload is never marked as sent by mistake.
- Other phones check every 45 s while the app is open, using a tiny version check; they only download when something really changed (plus a full refresh every 10 min). They also sync when opened, when the phone reconnects, and when you leave the app.
- There is no push service: a phone whose app is closed or in the background receives changes the moment it is opened.

## v14 - named changes to sales
- Any change to an EXISTING sale (Edit, Mark paid, Add payment, Back to credit, Delete, and Receive payment on the Customers page) first asks the person to TYPE THEIR NAME.
  The prompt shows exactly what is about to change. Blank / placeholder names ("Staff", "Owner", "Unknown", numbers) are refused.
- Cancel, or no answer within 60 seconds: the change is NOT made. A REVOKED entry ("no name given, attempted: ...") is logged and the other manager phones get a warning
  and a "change blocked" notification. A named change also alerts them, with the typed name.
- The Activity log records the typed name, the login type (owner/staff), what changed (old -> new), and revoked attempts. It is exported to the "Activity log" sheet.
- New sales are not interrupted; they are stamped with the login ("Recorded by" column in the Excel export).
- The name is self-declared. It identifies who SAYS they made the change; the login type (owner/staff PIN) is the part that cannot be typed.

## v15 - new look + business setup
- Modern design: gradient header with your logo, rounded cards, icon navigation, avatars, KPI cards, charts, light/dark themes.
- New Home dashboard (owner): today's sales, cash received, who owes, net profit, low stock, quick actions, 7-day chart and a "needs attention" list.
  Navigation (owner): Home | Sales | Customers | Stock | More. Detailed monthly summary lives under Home / More. Staff: Sales | Customers | Stock | Notes.
- Business setup: first run opens a 3-step wizard (name, logo, contact, currency, brand colour, receipt message). Edit it any time in More > Business profile.
  The profile is shared with all your phones (synced). Changes made after setup are listed in Activity & alerts and alert other managers.
- Currency is now yours everywhere (screens, receipts, reminders, activity text). Receipts include your phone, address and thank-you message.
- 7 brand colours, applied to the header, buttons, charts and icons in both light and dark mode.
- Logo: pick a photo from the phone (cropped square and shrunk automatically); without one, your initials are used.

## v16 - updates for your managers
Every manager (and staff) phone now learns when a new version is ready, shows an "Update vNN ready" banner (and a phone notification once),
downloads it, checks it is exactly the file that was published (SHA-256), and hands it to Android's installer for one confirming tap.
Android does not allow silent installs, and the update must be signed with the same key (it is - app/debug.keystore), so your data stays.

### One-time setup (about 10 minutes)
1. Create a NEW **public** GitHub repository, e.g. `globaltrio-updates`, and tick "Add a README file". (It only ever holds the APK and a tiny latest.json - no code, passwords or business data.
   Keep your main `globaltrio` repository private: it contains the signing key.)
2. Create a token: GitHub > your profile picture > Settings > Developer settings > Personal access tokens > Fine-grained tokens > Generate new token.
   Repository access: "Only select repositories" > `globaltrio-updates`. Permissions > Repository permissions > **Contents: Read and write**. Generate and copy the token.
3. In your private `globaltrio` repository: Settings > Secrets and variables > Actions.
   - Tab **Secrets** > New repository secret: Name `UPDATES_TOKEN`, value = the token.
   - Tab **Variables** > New repository variable: Name `UPDATES_REPO`, value = `yourusername/globaltrio-updates`.
4. Install this version (v16) on each manager phone ONCE the normal way. From then on, every build that completes in GitHub Actions is published automatically,
   and the managers' apps offer it (checks about every 6 hours, and when the app is opened). To see it immediately: More > Settings > App updates > Check now.
5. Optional: edit `release-notes.txt` before a build - its first line is the "what's new" text shown in the banner.

The first time a phone installs an update, Android asks to allow "Install unknown apps" for Global Trio - allow it, then tap Update again.
If you prefer, the owner can also type the repository (`owner/repository`) under More > Settings > App updates; it is saved in the shared business profile so all managers inherit it.

### The build workflow (.github/workflows/build.yml)
Checks the project layout and JavaScript, stamps the build number and update source into the app, builds the APK (GlobalTrio-vNN-buildN.apk),
prints its SHA-256 and signing fingerprint on the run page, uploads it as an artifact, and (if set up) publishes it for the managers.
Press "Run workflow" and tick "publish_release" to also publish a plain-download Release in the main repository.
