# Changelog

Newest first. `next` is where changes land first; a release is what was tested there.

## On `next` (not released yet)

**Prayer times and adhan**
- Gulf cities, each country's own prayer method, and Ja'fari.
- «تعديل وقت الصلاة»: move each prayer by its own minutes.
- The adhan bar names the prayer before the adhan; the Fajr voice and a short adhan; honest buffering.
- The adhan keeps its right time through previews and city changes; the TV's volume comes back after any adhan.
- «أذان من ملفي»: the person's own adhan; the old one stays until the new one is in.

**Home screen and screensaver**
- «تعديل المفضلة»: press a favourite to pick it up and move it along the row, × to remove it with «تراجع»; «ترتيب التطبيقات المفضلة» is now in the settings only.
- Prayer times as boxes, new pills (icons only, the name shows on focus), glass panels, softer glow on the focused app.
- «يُتلى الآن» card while تلاوة plays; the screen is now «تلاوة وأذان».
- A year strip under the clock, the date beside the clubs, club crests when fetch is refused, fixes to the league card.
- The screensaver starts from its pill; the page survives a WebView renderer that dies.
- The weather and the dust are told apart; forecast and weather show only as old as they are; the last price is kept and marked.
- The medium and small clocks lay the screen out again (the large one is unchanged); «بطاقة ثانية»: a second card beside the small clock.

**Speed and safety**
- Fetches and the live poll stay inside their deadlines; apps load off the UI thread; downloads are capped.
- The Unsplash key goes only to the Unsplash API.
- App caches are trimmed only when storage is low.
- Install no longer dies on a silent TV; the sleep timer is forgotten on restart; the stock home comes back at once if ours does not open.
- Android's own core packages are never disabled; reset puts back the values the backup recorded.

**Setup and tools**
- The home switch can always be undone: a nonce on the safety switch, a heartbeat check before switching, a journal read on the next run, and `optimize/restore-home.sh`.
- Pair over Wireless debugging, find the TV again, and check first that it is Android TV with a new enough WebView.
- Community lists for TVs our own lists do not know; the setup wizard and TCL auto-start are handled.
- The setup shows what was turned off and lets a person keep any of it.
- Problems are reported through the form, never GitHub; share steps never print a negative time; Windows 11 is told from 10.
- The ADB key and the saved TV stay outside the clone.
- `AGENTS.md` for agents; new rows for the `!!` lines; the models seen in reports in `docs/TVS.md`.
- Reports keep only the lines the script builds, and the scrub (the net for what the scripts printed on `!!` lines)
  also covers IPv6 addresses, emails, serials, tokens, links with tokens, Wi-Fi names and user names.
- `SECURITY.md`, `CONTRIBUTING.md` and this file.
- For testing only: `optimize/saver.sh updatechannel next` makes «تحديث تلفزيوني» read the apk-next build (the CI now publishes its `version.txt`); `tests/android_compile.sh` compiles both apps against API 34 (SKIP when offline) and runs in `tests/run.sh`. It also fails on a source that uses a class hidden in the public SDK (`tests/hidden_api_scan.py`; that is what broke the release build once), and uses the real public `android.jar` when the Android SDK is installed.

## 23 October 2026 (51b9680)

Release: everything tested on `next` up to that day became the release.
