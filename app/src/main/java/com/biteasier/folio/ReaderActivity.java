package com.biteasier.folio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintManager;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Shows one document. */
public class ReaderActivity extends Activity {
    /** Reserved name that can never be a real site. Pages and images are served by this class. */
    private static final String HOST = "folio.invalid";
    private static final String POLICY = "default-src 'none'; img-src 'self' data:; style-src 'nonce-folio'; "
            + "script-src 'none'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'";
    private static final int MAX_MARKDOWN = 2_000_000;
    private static final int MAX_IMAGE = 30 * 1024 * 1024;
    private static final String[] FONTS = {"Sans", "Serif", "Mono"};
    private static final String[] CHARSETS = {"Detect automatically", "UTF-8", "UTF-16LE", "UTF-16BE", "windows-1252",
            "windows-1250", "windows-1251", "windows-1253", "windows-1254", "windows-1255", "windows-1256", "ISO-8859-2",
            "KOI8-R", "Shift_JIS", "EUC-JP", "EUC-KR", "GB18030", "Big5"};

    private LinearLayout root, bar, findBar;
    private FrameLayout body;
    private TextView title, notice, message, count;
    private EditText query;
    private WebView web;
    private View moreButton, tocButton, findButton;

    private Library.Item item;
    private volatile byte[] page;
    private String pageUrl;
    private String text = "";
    private String charset = "", fileSize = "";
    private boolean markdown, ready, openBinary, clearHistory;
    private String anchor;
    private int generation;
    private OnBackInvokedCallback back;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildViews();
        start(getIntent(), state == null);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        start(i, true);
    }

    // ---- Views ----

    private void buildViews() {
        root = Ui.root(this);
        body = new FrameLayout(this);
        message = new TextView(this);
        notice = new TextView(this);
        styleChrome();
        setContentView(root);
        Ui.paint(this, root);
    }

    /** Builds the toolbars in the current colors. Also used when the theme changes. */
    private void styleChrome() {
        Ui.Colors k = Ui.colors(this);
        boolean finding = findBar != null && findBar.getVisibility() == View.VISIBLE;
        CharSequence oldQuery = query == null ? "" : query.getText();
        root.removeAllViews();

        bar = Ui.bar(this);
        bar.addView(Ui.icon(this, R.drawable.ic_back, "Back", this::finish));
        title = Ui.title(this, item == null ? "" : item.name);
        bar.addView(title);
        findButton = Ui.icon(this, R.drawable.ic_search, "Find in file", this::openFind);
        tocButton = Ui.icon(this, R.drawable.ic_toc, "Contents", this::showToc);
        moreButton = Ui.icon(this, R.drawable.ic_more, "More", this::showMenu);
        bar.addView(findButton);
        bar.addView(tocButton);
        bar.addView(moreButton);
        root.addView(bar);

        findBar = Ui.bar(this);
        findBar.setVisibility(View.GONE);
        findBar.addView(Ui.icon(this, R.drawable.ic_close, "Close find", this::closeFind));
        query = new EditText(this);
        query.setHint("Find in file");
        query.setTextColor(k.text);
        query.setHintTextColor(k.muted);
        query.setTextSize(16);
        query.setBackground(null);
        query.setSingleLine();
        query.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        query.setText(oldQuery);
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { find(s.toString()); }
        });
        query.setOnEditorActionListener((v, id, e) -> {
            if (e == null || e.getAction() == KeyEvent.ACTION_DOWN) next(true);
            return true;
        });
        findBar.addView(query, new LinearLayout.LayoutParams(0, -2, 1));
        count = Ui.text(this, "", 13, k.muted);
        count.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        findBar.addView(count);
        findBar.addView(Ui.icon(this, R.drawable.ic_up, "Previous match", () -> next(false)));
        findBar.addView(Ui.icon(this, R.drawable.ic_down, "Next match", () -> next(true)));
        root.addView(findBar);

        notice.setTextSize(13);
        notice.setTextColor(k.muted);
        notice.setBackgroundColor(k.card);
        notice.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8));
        if (notice.length() == 0) notice.setVisibility(View.GONE);
        root.addView(notice);

        message.setTextSize(15);
        message.setTextColor(k.muted);
        message.setGravity(Gravity.CENTER);
        message.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
        if (message.getParent() == null) body.addView(message, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        if (web != null) web.setBackgroundColor(k.bg);
        setControls(ready);
        if (finding) openFind();
    }

    private void setControls(boolean on) {
        findButton.setVisibility(on ? View.VISIBLE : View.GONE);
        tocButton.setVisibility(on && markdown ? View.VISIBLE : View.GONE);
        moreButton.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        restyle();
    }

    private void restyle() {
        styleChrome();
        Ui.paint(this, root);
        if (ready) js("folio.keep(function(){" + setCall() + "})", null);
    }

    private String setCall() {
        SharedPreferences p = Library.prefs(this);
        return "folio.set('" + Ui.colors(this).css + "','" + font() + "'," + p.getBoolean("wrap", true) + ")";
    }

    private String font() {
        return FONTS[Math.min(2, Math.max(0, Library.prefs(this).getInt("font", 0)))].toLowerCase(Locale.ROOT);
    }

    // ---- Opening ----

    private void start(Intent in, boolean fresh) {
        savePosition();
        item = null;
        openBinary = false;
        anchor = fresh ? in.getStringExtra("anchor") : null;
        Uri u = in.getData();
        CharSequence shared = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            u = in.getParcelableExtra(Intent.EXTRA_STREAM);
            shared = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        }
        if (u != null && "content".equals(u.getScheme()) && !mine(u)) {
            Library.Item known = Library.find(this, u.toString());
            item = known != null ? known : new Library.Item();
            item.uri = u.toString();
            if (item.mime == null) item.mime = in.getType();
            if (known == null) item.name = in.getStringExtra("name") != null ? in.getStringExtra("name") : u.getLastPathSegment();
        } else if (u != null && "folio".equals(u.getScheme())) {
            item = Library.find(this, u.toString());
        } else if (u == null && shared != null && shared.length() > 0) {
            openShared(shared.toString(), in.getStringExtra(Intent.EXTRA_SUBJECT), fresh);
        }
        if (item == null) fail("This file can’t be opened.\nTry opening it again from your file manager.");
        else load();
    }

    /** Content from this app itself is never opened on another app's request. */
    private boolean mine(Uri u) {
        String a = u.getAuthority();
        return a == null || a.contains(getPackageName());
    }

    private void openShared(String s, String subject, boolean fresh) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        String uri = "folio:text/" + Integer.toHexString(s.hashCode()) + "-" + s.length();
        item = Library.find(this, uri);
        if (item != null) return;
        item = new Library.Item();
        item.uri = uri;
        String first = subject != null && !subject.trim().isEmpty() ? subject.trim() : s.trim().split("\n", 2)[0].replaceFirst("^#+\\s*", "");
        item.name = first.isEmpty() ? "Shared text" : first.length() > 60 ? first.substring(0, 60) + "…" : first;
        item.mode = 1;
        item.copy = Library.saveCopy(this, null, data);
    }

    private static final class Loaded {
        String text, name, charset, error, notice, html;
        boolean binary, markdown;
        int size;
        String copy;
    }

    private void load() {
        final int gen = ++generation;
        ready = false;
        setControls(false);
        if (web != null) web.setVisibility(View.INVISIBLE);
        showNotice(null);
        message.setText("Opening…");
        message.setVisibility(View.VISIBLE);
        message.setOnClickListener(null);
        final Library.Item it = item;
        final Context app = getApplicationContext();
        title.setText(it.name);
        // Extra stack: nested Markdown is rendered recursively.
        new Thread(null, () -> {
            Loaded r = prepare(app, it, openBinary);
            runOnUiThread(() -> {
                if (gen == generation && !isDestroyed()) show(it, r);
            });
        }, "load", 8L << 20).start();
    }

    private static Loaded prepare(Context c, Library.Item it, boolean openBinary) {
        Loaded r = new Loaded();
        r.name = it.name;
        r.copy = it.copy;
        try {
            Uri uri = Uri.parse(it.uri);
            boolean content = "content".equals(uri.getScheme());
            if (content) {
                String n = displayName(c, uri);
                if (n != null) r.name = n;
            }
            boolean lasting = content && Library.keepAccess(c, uri);
            Library.Bytes b = Library.read(c, it);
            r.size = b.data.length;
            List<String> notes = new ArrayList<>();
            if (b.fromCopy && content) notes.add("The original isn’t available. Showing the saved copy.");
            if (b.truncated) notes.add("Large file: showing the first 10 MB.");

            TextDecoder.Result d = TextDecoder.decode(b.data, it.charset, b.truncated);
            r.charset = d.charset;
            if (d.binary && !openBinary) {
                r.binary = true;
                return r;
            }
            if (content && !lasting && !b.fromCopy && !d.binary && b.data.length <= Library.MAX_COPY) {
                r.copy = Library.saveCopy(c, it.copy, b.data);
            }
            r.text = d.text.indexOf('\r') < 0 ? d.text : d.text.replace("\r\n", "\n").replace('\r', '\n');
            r.markdown = it.mode == 1 || (it.mode == 0 && Renderer.looksLikeMarkdown(r.name, it.mime));
            if (r.markdown && r.text.length() > MAX_MARKDOWN) {
                r.markdown = false;
                notes.add("Too large to format, so it is shown as plain text.");
            }
            if (r.markdown) {
                try {
                    r.html = Renderer.markdown(r.text);
                } catch (StackOverflowError | RuntimeException e) {
                    r.markdown = false;
                    notes.add("The formatting couldn’t be read, so it is shown as plain text.");
                }
            }
            if (r.html == null) r.html = Renderer.plain(r.text);
            if (!notes.isEmpty()) r.notice = String.join("\n", notes);
        } catch (IOException | RuntimeException e) {
            r.error = "This file can’t be opened.\nIt may have been moved, renamed or deleted, or the app that shared it no longer allows access.";
        } catch (OutOfMemoryError e) {
            r.error = "This file is too large for the memory available.";
        }
        return r;
    }

    private static String displayName(Context c, Uri uri) {
        try (Cursor q = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst()) return q.getString(0);
        } catch (RuntimeException ignored) {
            // the read that follows reports real problems
        }
        return null;
    }

    private void show(Library.Item it, Loaded r) {
        if (r.error != null) {
            fail(r.error);
            return;
        }
        it.name = r.name;
        title.setText(r.name);
        if (r.binary) {
            fail("This doesn’t look like a text file.\n\nTap here to open it anyway.");
            message.setOnClickListener(v -> {
                openBinary = true;
                load();
            });
            return;
        }
        it.copy = r.copy;
        it.time = System.currentTimeMillis();
        Library.put(this, it, true);
        text = r.text;
        charset = r.charset;
        fileSize = Formatter.formatShortFileSize(this, r.size);
        markdown = r.markdown;
        showNotice(r.notice);
        page = wrap(r.html).getBytes(StandardCharsets.UTF_8);
        pageUrl = buildUrl(Uri.parse(it.uri));
        if (text.trim().isEmpty()) showNotice("This file is empty.");
        try {
            if (web == null) makeWeb();
        } catch (RuntimeException e) {
            fail("Folio needs Android System WebView. Install or enable it, then try again.");
            return;
        }
        clearHistory = true;
        web.loadUrl(pageUrl);
    }

    private String wrap(String html) {
        return "<!doctype html><html data-theme=" + Ui.colors(this).css + " data-font=" + font()
                + (Library.prefs(this).getBoolean("wrap", true) ? " data-wrap=1" : "")
                + "><head><meta charset=utf-8><meta name=viewport content=\"width=device-width,initial-scale=1\">"
                + "<style nonce=folio>" + asset("page.css") + "</style></head><body><main"
                + (markdown ? "" : " class=plain") + ">" + html + "</main></body></html>";
    }

    /** Puts the file's folder in the address, so the page resolves relative links by itself. */
    private static String buildUrl(Uri u) {
        Uri.Builder b = new Uri.Builder().scheme("https").authority(HOST).appendPath("f");
        String[] parts = null;
        if (Paths.STORAGE.equals(u.getAuthority())) {
            try {
                parts = Paths.toSegments(DocumentsContract.getDocumentId(u));
            } catch (IllegalArgumentException ignored) {
                // not a document address
            }
        }
        if (parts == null) b.appendPath("x").appendPath("document");
        else for (String s : parts) b.appendPath(s);
        return b.build().toString();
    }

    /** The file an address inside the page refers to, if it is in a folder the user added. */
    private Uri resolve(Uri link) {
        List<String> s = link.getPathSegments();
        if (s.size() < 3 || !s.get(0).equals("f") || s.get(1).equals("x")) return null;
        String id = Paths.toDocId(s.subList(1, s.size()));
        return id == null ? null : Library.inFolders(this, id);
    }

    private void fail(String s) {
        generation++;
        ready = false;
        setControls(false);
        showNotice(null);
        if (web != null) web.setVisibility(View.INVISIBLE);
        message.setText(s);
        message.setVisibility(View.VISIBLE);
        message.setOnClickListener(null);
    }

    private void showNotice(String s) {
        notice.setText(s == null ? "" : s);
        notice.setVisibility(s == null ? View.GONE : View.VISIBLE);
    }

    // ---- Page ----

    @SuppressLint("SetJavaScriptEnabled")
    private void makeWeb() {
        web = new WebView(this);
        WebSettings s = web.getSettings();
        // Only Folio's own calls run: the page policy blocks every script in the document.
        s.setJavaScriptEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setBlockNetworkLoads(true);
        s.setGeolocationEnabled(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setSupportMultipleWindows(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setTextZoom(zoom());
        web.setBackgroundColor(Ui.colors(this).bg);
        web.setWebViewClient(new Client());
        web.setFindListener((active, total, done) -> {
            if (done && findBar.getVisibility() == View.VISIBLE) {
                count.setText(query.length() == 0 ? "" : total == 0 ? "0" : (active + 1) + "/" + total);
            }
        });
        web.setVisibility(View.INVISIBLE);
        body.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    private final class Client extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            byte[] p = page;
            if (r.isForMainFrame() && p != null && HOST.equals(u.getHost())) {
                WebResourceResponse w = new WebResourceResponse("text/html", "utf-8", new ByteArrayInputStream(p));
                w.setResponseHeaders(java.util.Map.of("Content-Security-Policy", POLICY, "X-Content-Type-Options", "nosniff", "Referrer-Policy", "no-referrer"));
                return w;
            }
            if (!r.isForMainFrame() && HOST.equals(u.getHost())) {
                Uri file = resolve(u);
                String mime = imageType(u.getLastPathSegment());
                if (file != null && mime != null) {
                    try {
                        return new WebResourceResponse(mime, null, new Limited(Library.open(getContentResolver(), file), MAX_IMAGE));
                    } catch (IOException | RuntimeException ignored) {
                        // falls through to "missing"
                    }
                }
            }
            return new WebResourceResponse("text/plain", "utf-8", 404, "Not found", null, new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (r.isForMainFrame() && r.hasGesture()) follow(r.getUrl());
            return true;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            if (ready || pageUrl == null || !url.startsWith(pageUrl)) return;
            ready = true;
            if (clearHistory) {
                clearHistory = false;
                v.clearHistory();
            }
            js(asset("page.js"), null);
            js(setCall(), null);
            if (anchor != null) js("folio.anchor(" + org.json.JSONObject.quote(anchor) + ")", null);
            else if (item != null && !item.pos.isEmpty() && item.pos.matches("\\d+:[0-9.]+")) js("folio.restore('" + item.pos + "')", null);
            anchor = null;
            js("1", x -> {
                message.setVisibility(View.GONE);
                web.setVisibility(View.VISIBLE);
                setControls(ready);
            });
        }

        @Override
        public void doUpdateVisitedHistory(WebView v, String url, boolean reload) {
            updateBack();
        }

        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            body.removeView(v);
            v.destroy();
            web = null;
            fail("The page ran out of memory.\n\nTap here to try again as plain text.");
            message.setOnClickListener(x -> {
                item.mode = 2;
                item.pos = "";
                load();
            });
            return true;
        }
    }

    /** Stops an image that is larger than the app is willing to load. */
    private static final class Limited extends java.io.FilterInputStream {
        private long left;

        Limited(InputStream in, long max) {
            super(in);
            left = max;
        }

        @Override
        public int read() throws IOException {
            int b = left <= 0 ? -1 : super.read();
            if (b >= 0) left--;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(b, off, (int) Math.min(len, left));
            if (n > 0) left -= n;
            return n;
        }
    }

    private static String imageType(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String ext = n.substring(n.lastIndexOf('.') + 1);
        switch (ext) {
            case "png": case "gif": case "webp": case "bmp": case "avif": return "image/" + ext;
            case "jpg": case "jpeg": return "image/jpeg";
            case "svg": return "image/svg+xml";
            default: return null;
        }
    }

    private void follow(Uri u) {
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
        if (HOST.equals(u.getHost())) {
            String name = u.getLastPathSegment();
            Uri file = resolve(u);
            if (file == null) {
                toast("Add the folder that holds this file to follow its links");
            } else if (imageType(name) != null || !openable(name)) {
                view(new Intent(Intent.ACTION_VIEW).setDataAndType(file, getContentResolver().getType(file))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            } else {
                startActivity(new Intent(this, ReaderActivity.class).setData(file).putExtra("name", name)
                        .putExtra("anchor", u.getFragment()));
            }
        } else if (Arrays.asList("http", "https", "mailto", "tel").contains(scheme)) {
            view(new Intent(scheme.equals("tel") ? Intent.ACTION_DIAL : Intent.ACTION_VIEW, u));
        } else {
            toast("Folio doesn’t open this kind of link");
        }
    }

    private static boolean openable(String name) {
        return name != null && (Renderer.looksLikeMarkdown(name, null) || name.matches("(?i).*\\.(txt|text|log|csv)") || !name.contains("."));
    }

    private void view(Intent i) {
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            toast("No app can open this link");
        }
    }

    private void js(String code, android.webkit.ValueCallback<String> result) {
        if (web != null) web.evaluateJavascript(code, result);
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- Reading position ----

    private void savePosition() {
        final Library.Item it = item;
        if (!ready || it == null || web == null) return;
        final Context app = getApplicationContext();
        js("folio.pos()", v -> {
            if (v == null || v.equals("null")) return;
            it.pos = v.replace("\"", "");
            Library.put(app, it, false);
        });
    }

    @Override
    protected void onPause() {
        savePosition();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        generation++;
        if (web != null) {
            body.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    // ---- Back ----

    private boolean handleBack() {
        if (findBar.getVisibility() == View.VISIBLE) closeFind();
        else if (web != null && ready && web.canGoBack()) web.goBack();
        else return false;
        return true;
    }

    /** Takes over the back gesture only while there is something to go back to in the page. */
    private void updateBack() {
        if (Build.VERSION.SDK_INT < 33) return;
        boolean need = findBar.getVisibility() == View.VISIBLE || (web != null && web.canGoBack() && !clearHistory);
        if (need && back == null) {
            back = this::handleBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        } else if (!need && back != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
            back = null;
        }
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (!handleBack()) super.onBackPressed();
    }

    // ---- Find ----

    private void openFind() {
        bar.setVisibility(View.GONE);
        findBar.setVisibility(View.VISIBLE);
        query.requestFocus();
        query.selectAll();
        query.post(() -> getSystemService(InputMethodManager.class).showSoftInput(query, 0));
        if (query.length() > 0) find(query.getText().toString());
        updateBack();
    }

    private void closeFind() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(query.getWindowToken(), 0);
        findBar.setVisibility(View.GONE);
        bar.setVisibility(View.VISIBLE);
        if (web != null) web.clearMatches();
        updateBack();
    }

    private void find(String s) {
        if (web == null) return;
        if (s.isEmpty()) {
            count.setText("");
            web.clearMatches();
        } else {
            web.findAllAsync(s);
        }
    }

    private void next(boolean forward) {
        if (web != null && query.length() > 0) web.findNext(forward);
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_F && e.isCtrlPressed() && ready) {
            openFind();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    // ---- Contents and menu ----

    private void showToc() {
        js("[folio.toc(),folio.now()]", v -> {
            try {
                JSONArray a = new JSONArray(v), heads = a.getJSONArray(0);
                int now = a.getInt(1), n = heads.length(), min = 6;
                if (n == 0) {
                    toast("No headings in this file");
                    return;
                }
                for (int i = 0; i < n; i++) min = Math.min(min, heads.getJSONArray(i).getInt(0));
                final int top = min;
                Ui.Colors k = Ui.colors(this);
                List<String> names = new ArrayList<>();
                for (int i = 0; i < n; i++) names.add(heads.getJSONArray(i).getString(1));
                ArrayAdapter<String> rows = new ArrayAdapter<String>(this, 0, names) {
                    @Override
                    public View getView(int i, View old, ViewGroup parent) {
                        int level = heads.optJSONArray(i).optInt(0) - top;
                        TextView t = Ui.text(ReaderActivity.this, getItem(i), level == 0 ? 16 : 15, i == now ? k.accent : k.text);
                        t.setPadding(Ui.dp(ReaderActivity.this, 24 + level * 16), Ui.dp(ReaderActivity.this, 12), Ui.dp(ReaderActivity.this, 20), Ui.dp(ReaderActivity.this, 12));
                        return t;
                    }
                };
                AlertDialog d = Ui.dialog(this).setTitle("Contents").setAdapter(rows, (x, i) -> js("folio.go(" + i + ")", null)).create();
                d.show();
                if (now > 2) d.getListView().setSelection(now - 2);
            } catch (JSONException e) {
                toast("The contents can’t be read");
            }
        });
    }

    private void showMenu() {
        List<String> names = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        names.add("Text and theme");
        actions.add(this::showAppearance);
        names.add(markdown ? "View source" : "View as Markdown");
        actions.add(() -> {
            item.mode = markdown ? 2 : 1;
            item.pos = "";
            ready = false;
            Library.put(this, item, false);
            load();
        });
        names.add("Reload");
        actions.add(() -> js("folio.pos()", v -> {
            if (v != null && !v.equals("null")) item.pos = v.replace("\"", "");
            load();
        }));
        names.add("Text encoding");
        actions.add(this::showEncoding);
        names.add("Share");
        actions.add(this::share);
        names.add("Print or save as PDF");
        actions.add(() -> getSystemService(PrintManager.class).print(item.name, web.createPrintDocumentAdapter(item.name), null));
        names.add("Details");
        actions.add(this::showDetails);
        Ui.dialog(this).setItems(names.toArray(new String[0]), (d, i) -> actions.get(i).run()).show();
    }

    private int zoom() {
        return Library.prefs(this).getInt("zoom", 100);
    }

    private void showAppearance() {
        Ui.Colors k = Ui.colors(this);
        SharedPreferences p = Library.prefs(this);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        v.setPadding(pad, pad, pad, pad);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(k.bg);
        scroll.addView(v);
        AlertDialog d = Ui.dialog(this).setView(scroll).create();

        TextView size = Ui.text(this, "Text size  " + zoom() + "%", 14, k.muted);
        v.addView(size);
        SeekBar bar = new SeekBar(this);
        bar.setContentDescription("Text size");
        bar.setMax(22);
        bar.setProgress((zoom() - 70) / 10);
        bar.setPadding(Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
            @Override public void onProgressChanged(SeekBar s, int i, boolean user) {
                int z = 70 + i * 10;
                p.edit().putInt("zoom", z).apply();
                size.setText("Text size  " + z + "%");
                if (web == null) return;
                js("folio.pos()", pos -> {
                    if (web == null) return;
                    web.getSettings().setTextZoom(z);
                    if (pos != null && pos.length() > 4) web.postDelayed(() -> js("folio.restore(" + pos + ")", null), 60);
                });
            }
        });
        v.addView(bar);
        choices(v, "Theme", Ui.THEMES, p.getInt("theme", 0), i -> {
            p.edit().putInt("theme", i).apply();
            restyle();
            d.dismiss();
            showAppearance();
        });
        choices(v, "Font", FONTS, p.getInt("font", 0), i -> {
            p.edit().putInt("font", i).apply();
            restyle();
            d.dismiss();
            showAppearance();
        });
        choices(v, "Long lines in code and text", new String[]{"Wrap", "Scroll"}, p.getBoolean("wrap", true) ? 0 : 1, i -> {
            p.edit().putBoolean("wrap", i == 0).apply();
            restyle();
            d.dismiss();
            showAppearance();
        });
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setGravity(Gravity.BOTTOM);
            d.getWindow().setDimAmount(0.2f);
        }
    }

    private void choices(LinearLayout v, String label, String[] names, int selected, java.util.function.IntConsumer pick) {
        Ui.Colors k = Ui.colors(this);
        TextView t = Ui.text(this, label, 14, k.muted);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        v.addView(t);
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < names.length; i++) {
            final int n = i;
            TextView b = Ui.button(this, names[i], i == selected, () -> pick.accept(n));
            b.setTextSize(13);
            b.setSelected(i == selected);
            b.setMaxLines(1);
            b.setMinHeight(Ui.dp(this, 48));
            b.setPadding(Ui.dp(this, 2), 0, Ui.dp(this, 2), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.setMarginStart(Ui.dp(this, 6));
            row.addView(b, lp);
        }
        v.addView(row);
    }

    private void showEncoding() {
        List<String> names = new ArrayList<>();
        for (String s : CHARSETS) if (s.contains(" ") || java.nio.charset.Charset.isSupported(s)) names.add(s);
        int at = item.charset == null ? 0 : Math.max(0, names.indexOf(item.charset));
        Ui.dialog(this).setTitle("Text encoding").setSingleChoiceItems(names.toArray(new String[0]), at, (d, i) -> {
            item.charset = i == 0 ? null : names.get(i);
            d.dismiss();
            load();
        }).show();
    }

    private void share() {
        Uri u = Uri.parse(item.uri);
        Intent i = new Intent(Intent.ACTION_SEND);
        if ("content".equals(u.getScheme()) && checkUriPermission(u, android.os.Process.myPid(), android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) {
            i.setType(markdown ? "text/markdown" : "text/plain").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.setClipData(ClipData.newRawUri(item.name, u));
        } else if (text.length() <= 100_000) {
            i.setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
        } else {
            toast("This file is too large to share as text");
            return;
        }
        view(Intent.createChooser(i, null));
    }

    private void showDetails() {
        int words = 0, lines = 1;
        boolean in = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') lines++;
            boolean w = Character.isLetterOrDigit(c);
            if (w && !in) words++;
            if (w || c != '\'' && c != '’' && c != '-') in = w;
        }
        String s = item.name + "\n\n" + fileSize + " · " + charset
                + String.format(Locale.getDefault(), "\n%,d lines · %,d words", lines, words)
                + (words > 0 ? "\nAbout " + Math.max(1, Math.round(words / 220f)) + " min to read" : "");
        Ui.dialog(this).setTitle("Details").setMessage(s).setPositiveButton("Close", null).show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
