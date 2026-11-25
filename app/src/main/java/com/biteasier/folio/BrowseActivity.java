package com.biteasier.folio;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists the folders and text files inside a folder the user added. */
public class BrowseActivity extends Activity {
    private static final class Entry {
        String id, name, mime, detail;
        boolean dir;
    }

    private Uri tree;
    private List<Entry> entries;
    private boolean failed;

    static String folderName(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        String tail = id.substring(id.lastIndexOf('/') + 1);
        tail = tail.substring(tail.lastIndexOf(':') + 1);
        return tail.isEmpty() ? "Storage" : tail;
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        tree = getIntent().getData();
        build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        String id = getIntent().getStringExtra("id");
        new Thread(() -> {
            List<Entry> found = null;
            try {
                found = load(id);
            } catch (RuntimeException ignored) {
                // the folder was deleted, moved, or its provider failed
            }
            List<Entry> result = found;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                failed = result == null;
                entries = result;
                build();
            });
        }).start();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    private List<Entry> load(String id) {
        List<Entry> list = new ArrayList<>();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
        String[] columns = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
        try (Cursor c = getContentResolver().query(children, columns, null, null, null)) {
            if (c == null) throw new IllegalStateException();
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.id = c.getString(0);
                e.name = c.getString(1);
                e.mime = c.getString(2);
                if (e.id == null || e.name == null || e.name.startsWith(".")) continue;
                e.dir = Document.MIME_TYPE_DIR.equals(e.mime);
                if (!e.dir && !readable(e)) continue;
                if (!e.dir) {
                    e.detail = Formatter.formatShortFileSize(this, c.getLong(3));
                    if (c.getLong(4) > 0) e.detail += " · " + DateUtils.formatDateTime(this, c.getLong(4), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
                }
                list.add(e);
            }
        }
        java.text.Collator order = java.text.Collator.getInstance();
        list.sort((a, b) -> a.dir != b.dir ? (a.dir ? -1 : 1) : order.compare(a.name, b.name));
        return list;
    }

    private static boolean readable(Entry e) {
        String n = e.name.toLowerCase(Locale.ROOT);
        return Renderer.looksLikeMarkdown(n, e.mime) || n.matches(".*\\.(txt|text|log)")
                || (e.mime != null && e.mime.startsWith("text/"))
                || n.matches("readme|license|changelog|notice");
    }

    private void build() {
        Ui.Colors k = Ui.colors(this);
        LinearLayout root = Ui.root(this);
        LinearLayout bar = Ui.bar(this);
        bar.addView(Ui.icon(this, R.drawable.ic_back, "Back", this::finish));
        bar.addView(Ui.title(this, getIntent().getStringExtra("name")));
        root.addView(bar);
        if (entries == null || entries.isEmpty()) {
            TextView t = Ui.text(this, failed ? "This folder can’t be opened. It may have been moved or deleted."
                    : entries == null ? "Loading…" : "No Markdown or text files here.", 15, k.muted);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 32), 0, Ui.dp(this, 32), 0);
            root.addView(t, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ListView list = new ListView(this);
            list.setDivider(null);
            list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return entries.size(); }
                @Override public Object getItem(int i) { return entries.get(i); }
                @Override public long getItemId(int i) { return i; }
                @Override public View getView(int i, View old, ViewGroup parent) {
                    Entry e = entries.get(i);
                    return Ui.row(BrowseActivity.this, e.dir ? R.drawable.ic_folder : R.drawable.ic_file, e.name, e.detail, () -> open(e));
                }
            });
            root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        }
        setContentView(root);
        Ui.paint(this, root);
    }

    private void open(Entry e) {
        Intent i = new Intent(this, e.dir ? BrowseActivity.class : ReaderActivity.class).putExtra("name", e.name);
        if (e.dir) i.setData(tree).putExtra("id", e.id);
        else i.setData(DocumentsContract.buildDocumentUriUsingTree(tree, e.id));
        startActivity(i);
    }
}
