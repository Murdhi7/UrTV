#!/usr/bin/env python3
"""«صفوف القنوات» on the home screen (the idea and the defaults from talkies, MIT): «أكمل المشاهدة» and the rows apps publish to
Android TV, under the favourites, the owner choosing which and their order. TvRowsPick runs for real here (tests/TvRowsRun.java); the
reading of Android TV's lists, the permission, the screen and the home's wiring are checked in the source. Needs javac.
Run: python3 tests/tv_rows_test.py"""
import re, shutil, subprocess, sys, tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
HOME = ROOT / "android/launcher/src/main/java/sa/urtv/home"
failed = 0
def check(name, ok, detail=""):
    global failed
    print(("PASS " if ok else "FAIL ") + name + ("" if ok else "  -> " + str(detail)))
    failed += not ok
read = lambda p: p.read_text(encoding="utf-8")
main, rows, pick, screen = read(HOME / "MainActivity.java"), read(HOME / "TvRows.java"), read(HOME / "TvRowsPick.java"), read(HOME / "ChannelsActivity.java")
manifest = read(ROOT / "android/launcher/src/main/AndroidManifest.xml")
settings = read(ROOT / "android/app/src/main/java/sa/urtv/screensaver/SettingsActivity.java")
fn = lambda src, name: src[src.index(name):].split("\n    }\n")[0]

# ---- permission and privacy
check("the launcher declares the TV listings permissions", '<uses-permission android:name="android.permission.READ_TV_LISTINGS" />' in manifest
      and '<uses-permission android:name="com.android.providers.tv.permission.READ_EPG_DATA" />' in manifest)
check("without the permission nothing is read and the home has no rows, as before (load and arrange ask first)",
      "if (!allowed(c)) return out;" in fn(rows, "static List<Row> load(Context c)") and "if (!allowed(c)) return new ArrayList<>();" in fn(rows, "static List<TvRowsPick.Row> arrange(Context c)")
      and "if (!TvRows.allowed(this)) {" in fn(main, "private void loadTvRows()"))
check("the permission is asked only in «صفوف القنوات», by the owner pressing «السماح» (never by the home on its own)",
      "requestPermissions(new String[]{TvRows.PERMISSION, TvRows.EPG}, ASK);" in screen and "requestPermissions" not in main)
check("a card opens only into the app that published it (TvRowsPick.linkAllowed), a plain link pinned to it (setPackage); else that app itself",
      "if (TvRowsPick.linkAllowed(it.pkg, i.getPackage(), comp)) {" in rows and "i.setSelector(null);" in rows and "if (comp == null) i.setPackage(it.pkg);" in rows
      and "getLeanbackLaunchIntentForPackage(it.pkg)" in fn(rows, "static boolean open(Context c, Item it)"))
check("a card goes through the app's own family lock first (ParentalPolicy.GRID, as the apps' grid)",
      "card.setOnClickListener(v -> guarded(ParentalPolicy.GRID, it.pkg, it.title, () -> TvRows.open(this, it)));" in main)
check("a picture is read bounded (Bounded.IMAGE_MAX), from http(s) with timeouts or the publishing app's own provider only (no android.resource, no file)",
      "Bounded.readAll(in, Bounded.IMAGE_MAX)" in rows and "ART_W = 400, ART_H = 400" in rows and "android.resource" not in rows.split("private static InputStream stream(")[1]
      and "resolveContentProvider(u.getAuthority(), 0)" in rows and "TvRowsPick.pictureProviderAllowed(it.pkg, p.packageName)" in rows
      and "h.setConnectTimeout((int) PICTURE_MS);" in rows and "h.setReadTimeout((int) PICTURE_MS);" in rows)
small = fn(rows, "private static Bitmap small(byte[] bytes, Bitmap.Config config)")
check("a picture is never decoded at full size: its size first (inJustDecodeBounds), then at a fraction (inSampleSize), then fitted to 400×400, the bigger one recycled",
      "size.inJustDecodeBounds = true;" in small and "o.inSampleSize = sample;" in small and "TvRowsPick.fitted(b.getWidth(), b.getHeight(), ART_W, ART_H)" in small
      and "if (s != b) b.recycle();" in small and small.index("inJustDecodeBounds") < small.index("o.inSampleSize"))
check("running out of memory on a picture costs that picture only (OutOfMemoryError caught where pictures are made and read)",
      "catch (Exception | OutOfMemoryError e)" in fn(rows, "private static File store(") and "catch (Exception | OutOfMemoryError e)" in fn(rows, "private static Bitmap decode(File f)")
      and "if (b != null) b.recycle();" in fn(rows, "private static File store("))
check("each picture has its own few seconds (a hung provider or server is left behind, a fresh thread for the next), all of them a budget",
      "f.get(ms, TimeUnit.MILLISECONDS)" in rows and "fetcher = Executors.newSingleThreadExecutor();" in rows and "PICTURE_MS = 4000" in rows
      and "long end = System.currentTimeMillis() + budgetMs;" in rows and "TV_PICTURES_MS = 8000" in main)
load = fn(main, "private void loadTvRows()")
check("the rows have their own thread (never the apps' loader); their words are shown first, the pictures follow; a newer load wins",
      "tvLoader.execute(() -> {" in load and "loader.execute" not in load and load.index("showTvRows(rows)") < load.index("TvRows.pictures(")
      and "if (gen == tvRowsGen && !isDestroyed()) {" in load and "tvLoader.shutdownNow();" in main and "artUri" in rows and "cached(" not in rows)
check("coming back to the home with the same rows rebuilds nothing; a rebuild puts the focus back on the same card (its row's key, its place)",
      "if (!sig.equals(tvShownSig)) { tvShownSig = sig; showTvRows(rows); }" in load and "hadRow = tvShown.get(r).key; hadAt = i;" in main
      and "tvCards.get(back).get(Math.min(hadAt, tvCards.get(back).size() - 1)).requestFocus();" in main)
check("a picture that comes later takes the place of the card's title; only the cards still without one are fetched again",
      "img.setImageBitmap(b);" in load and "if (plain != null) plain.setVisibility(View.GONE);" in load and "if (!tvPictured.contains(it)) m.items.add(it);" in load)
check("a channel's id is looked up only from this reading (the old ones cleared first)", "synchronized (IDS) { IDS.clear(); }" in fn(rows, "private static List<TvRowsPick.Found> found(Context c)"))
check("a link the card opens grants no permission of the home's (FLAG_GRANT_* taken off)",
      "i.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION" in rows and "FLAG_GRANT_PREFIX_URI_PERMISSION" in rows)
check("the rows are read again each time the home comes back to the front («أكمل المشاهدة» moves on)", "loadTvRows();   // what one watched since" in fn(main, "protected void onResume()"))

# ---- the home's rows
check("the rows sit between the favourites and «كل التطبيقات»", main.index("content.addView(tvRowsBox") < main.index("content.addView(appsLabel"))
show = fn(main, "private void showTvRows(List<TvRows.Row> rows)")
check("RTL: each row is a horizontal line of box→card inside a horizontal scroll, as the walker of the home's rows expects; padded at the start like the grid",
      "line.setOrientation(LinearLayout.HORIZONTAL);" in show and "hs.addView(line);" in show and "cards.add(((ViewGroup) box).getChildAt(0));" in show
      and "hs.setPaddingRelative(dp(68), dp(4), dp(68), dp(4));" in show and "p.setMarginEnd(" in show)
check("a card is one grid tile wide", "int cardW = (screenW - 2 * dp(68) - (cols - 1) * dp(20)) / cols;" in show)
wire = fn(main, "private void wireFocus()")
check("▼ from the favourites goes to the first row; ▲ from the first apps row comes from the last row",
      "c.setNextFocusDownId(!tvCards.isEmpty() ? tvCards.get(0).get(" in wire and "lastTv != null ? lastTv.get(Math.min(col, lastTv.size() - 1)).getId()" in wire)
check("from a row: ▲ to the row above or the favourites, ▼ to the next row or the apps; the ends of a row stay",
      "c.setNextFocusUpId(r > 0 ? tvCards.get(r - 1)" in wire and ": d > 0 ? dockCards.get(Math.min(i, d - 1)).getId() : topId);" in wire
      and "c.setNextFocusDownId(r < tvCards.size() - 1 ? tvCards.get(r + 1)" in wire and "i == row.size() - 1 ? c.getId()" in wire and "i == 0 ? c.getId()" in wire)
check("when the rows go while the focus was in them, it comes back to the favourites", "else if (had && !dockCards.isEmpty()) dockCards.get(0).requestFocus();" in show)
card = fn(main, "private View tvCard(TvRows.Item it)")
check("a card: its title on a plain card, the picture over it when it comes; how far in as a gold line from the right; its name and line on the focus",
      "tvPics.put(it, img);" in card and "plain.setText(it.title);" in card and "img.setVisibility(View.GONE);" in card and "Gravity.BOTTOM | Gravity.RIGHT" in card and "it.progress" in card
      and "label.animate().alpha(has ? 1f : 0f)" in card)

# ---- the screen and the settings row
check("«صفوف القنوات» is a screen of the launcher, opened from the screensaver's settings by class name",
      'android:name=".ChannelsActivity"' in manifest and 'row("صفوف القنوات"' in settings and '"sa.urtv.home.ChannelsActivity"' in settings)
check("…behind «قفل الإعدادات» like «ترتيب المفضلة»", "ParentalPolicy.targets(ParentalPolicy.ARRANGE_SCREEN, null)" in screen and "Parental.guard(this" in screen)
check("…OK shows or hides a row, ▲ and ▼ move it, every change kept at once", "r.shown = !r.shown;" in screen and "TvRows.save(this, rows);" in screen
      and 'pill("▼", v -> move(at, 1))' in screen and 'pill("▲", v -> move(at, -1))' in screen)
check("…the TV's lists are read off the main thread", "new Thread(() -> {" in fn(screen, "private void render(int focus)"))
check("no Android in TvRowsPick (it runs on a plain JVM here)", "import android" not in pick)
notices = read(ROOT / "THIRD_PARTY_NOTICES.md")
check("talkies is credited (THIRD_PARTY_NOTICES.md, and in the new classes)", "Copyright (c) 2026 TechPP" in notices and all("talkies" in s and "THIRD_PARTY_NOTICES.md" in s for s in (rows, pick, screen)))
check("documented: SETTINGS.md, FEATURES.md and TEST-PLAN.md", "صفوف القنوات" in read(ROOT / "docs/SETTINGS.md") and "صفوف القنوات" in read(ROOT / "docs/FEATURES.md")
      and "صفوف القنوات" in read(ROOT / "docs/TEST-PLAN.md"))

# ---- TvRowsPick, for real
if not shutil.which("javac"):
    print("SKIP tv rows: no javac here")
    sys.exit(1 if failed else 0)
with tempfile.TemporaryDirectory() as tmp:
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", tmp, str(HOME / "TvRowsPick.java"), str(HOME / "DigitsText.java"), str(ROOT / "tests/TvRowsRun.java")], check=True)
    out = subprocess.run(["java", "-Dfile.encoding=UTF-8", "-cp", tmp, "sa.urtv.home.TvRowsRun"], check=True, capture_output=True).stdout.decode("utf-8")
r = dict(l.split(" = ", 1) for l in out.split("\n") if " = " in l)
check("a row's title: the app alone when named after it, else «app · row»",
      r["title_same"] == "Netflix" and r["title_two"] == "YouTube · Recommended for you" and r["title_inside"] == "Prime Video" and r["title_noapp"] == "Top Picks", r)
check("until the owner chooses: «أكمل المشاهدة» first, then the rows their apps asked to show; the rest hidden, by name",
      r["default"] == "watchnext:1,com.apple.atve|Up Next:0,com.netflix.ninja|Netflix:1,com.google.android.youtube.tv|Recommended for you:1,com.google.android.youtube.tv|Trending:0",
      r["default"])
check("«أكمل المشاهدة» only when it has something; a row with nothing in it is no row", r["default_no_watchnext"].split(",")[0] != "watchnext:1" and "com.empty.app" not in r["default"], r["default_no_watchnext"])
check("no app asked to show a row: each app's first row is shown", r["default_none_browsable"] == "a.one|Row A:1,a.one|Row B:0,b.two|Row C:1", r["default_none_browsable"])
check("an app that publishes one row twice: the fuller one", r["duplicate_count"] == "9", r["duplicate_count"])
check("«أكمل المشاهدة» is called so", r["watchnext_title"] == "أكمل المشاهدة", r["watchnext_title"])
check("the owner's order and choice are kept; a row that went is skipped; a new one goes after, by name",
      r["saved"] == "com.apple.atve|Up Next:1,watchnext:0,com.netflix.ninja|Netflix:1,com.google.android.youtube.tv|Recommended for you:0,com.google.android.youtube.tv|Trending:0", r["saved"])
check("what is saved reads back the same; «nothing shown» is a choice too (not the defaults again)",
      r["roundtrip"] == "true" and ":1" not in r["saved_nothing_shown"], r["saved_nothing_shown"])
check("▲ moves a row up; at the top and the bottom nothing moves", r["move_up"].startswith("true watchnext:0,com.apple.atve") and r["move_top_edge"] == "false" and r["move_bottom_edge"] == "false", r["move_up"])
check("«أكمل المشاهدة»: what one is in the middle of, and the next episode; not a new season or a watchlist; not hidden, not of an app that is gone",
      (r["wn_continue"], r["wn_next"], r["wn_new"], r["wn_watchlist"], r["wn_hidden"], r["wn_no_app"]) == ("true", "true", "false", "false", "false", "false"))
check("the line under a card: «الموسم ٢ · الحلقة ١ · Netflix», digits as the setting says", r["meta_episode"] == "الموسم ٢ · الحلقة ١ · Netflix" and r["meta_latin"] == "الموسم 2 · الحلقة 10 · Netflix",
      (r["meta_episode"], r["meta_latin"]))
check("…else the episode's name, else the description; «null» from an app is nothing",
      r["meta_eptitle"] == "The Long Night" and r["meta_same_title"] == "desc here" and r["meta_nulls"] == "", (r["meta_eptitle"], r["meta_same_title"], r["meta_nulls"]))
check("a link opens only into its own app: by package or component, or naming none (pinned to the app by the caller); never another app",
      (r["link_package"], r["link_component"], r["link_other_component"], r["link_other_package"], r["link_mixed"], r["link_none"], r["link_no_row"])
      == ("true", "true", "false", "false", "false", "true", "false"))
check("a huge picture (8000×6000, 190 MB at full size) is decoded at 1/16 (500×375, under 1 MB) and fitted to 400×300",
      r["sample_huge"] == "16 500x375 732KB" and r["fit_huge"] == "400x300", (r["sample_huge"], r["fit_huge"]))
check("a very tall picture is bounded by its height too (1000×30000: 1/64, then 9×400)", r["sample_tall"] == "64 15x468" and r["fit_tall"] == "13x400", (r["sample_tall"], r["fit_tall"]))
check("a small picture is taken as it is; a picture with no size is none", r["sample_small"] == "1 320" and r["sample_none"] == "0 0", (r["sample_small"], r["sample_none"]))
check("a content:// picture only from the publishing app's own provider", (r["provider_own"], r["provider_other"], r["provider_none"]) == ("true", "false", "false"))
check("the rows' signature tells the same rows from changed ones", r["sig_same"] == "true" and r["sig_diff"] == "false", (r["sig_same"], r["sig_diff"]))
check("how far in: a fraction up to 1, unknown without a position or a length",
      r["progress_half"] == "0.5" and r["progress_over"] == "1.0" and r["progress_unknown"] == "-1.0 -1.0", (r["progress_half"], r["progress_over"], r["progress_unknown"]))
sys.exit(1 if failed else 0)
