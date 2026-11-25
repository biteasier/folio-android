package com.biteasier.folio;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.provider.DocumentsContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Recent files, reading positions and access to files. Everything stays on the phone. */
final class Library {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    static final int MAX_COPY = 2 * 1024 * 1024;
    private static final int MAX_RECENT = 30;

    static final class Item {
        String uri;
        String name;
        /** File in private storage, for files that Android lets the app read only once. */
        String copy;
        long time;
        /** "block:fraction", or empty for the top. */
        String pos = "";
        /** 0 automatic, 1 Markdown, 2 plain text. */
        int mode;
        String charset;
        String mime;
    }

    static final class Bytes {
        byte[] data;
        boolean truncated;
        boolean fromCopy;
    }

    private Library() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("folio", Context.MODE_PRIVATE);
    }

    static synchronized List<Item> recent(Context c) {
        List<Item> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("recent", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.uri = o.getString("u");
                it.name = o.getString("n");
                it.copy = o.optString("c", null);
                it.time = o.optLong("t");
                it.pos = o.optString("p", "");
                it.mode = o.optInt("m");
                it.charset = o.optString("e", null);
                it.mime = o.optString("y", null);
                list.add(it);
            }
        } catch (JSONException e) {
            list.clear();
        }
        return list;
    }

    static synchronized Item find(Context c, String uri) {
        for (Item i : recent(c)) if (i.uri.equals(uri)) return i;
        return null;
    }

    /** Adds or updates an entry. Existing entries keep their place unless toTop is set. */
    static synchronized void put(Context c, Item item, boolean toTop) {
        List<Item> list = recent(c);
        int at = -1;
        for (int i = 0; i < list.size(); i++) if (list.get(i).uri.equals(item.uri)) at = i;
        if (at < 0 && !toTop) return; // it was removed from the list meanwhile
        if (toTop) {
            if (at >= 0) list.remove(at);
            list.add(0, item);
        } else {
            list.set(at, item);
        }
        while (list.size() > MAX_RECENT) forget(c, list.remove(list.size() - 1));
        save(c, list);
    }

    static synchronized void remove(Context c, String uri) {
        List<Item> list = recent(c);
        for (Iterator<Item> it = list.iterator(); it.hasNext(); ) {
            Item i = it.next();
            if (i.uri.equals(uri)) {
                it.remove();
                forget(c, i);
            }
        }
        save(c, list);
    }

    static synchronized void clear(Context c) {
        for (Item i : recent(c)) forget(c, i);
        save(c, new ArrayList<>());
    }

    private static void forget(Context c, Item i) {
        if (i.copy != null) new File(c.getFilesDir(), i.copy).delete();
        try {
            c.getContentResolver().releasePersistableUriPermission(Uri.parse(i.uri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // access was never kept for this one
        }
    }

    private static void save(Context c, List<Item> list) {
        JSONArray a = new JSONArray();
        try {
            for (Item i : list) {
                a.put(new JSONObject().put("u", i.uri).put("n", i.name).putOpt("c", i.copy).put("t", i.time)
                        .put("p", i.pos).put("m", i.mode).putOpt("e", i.charset).putOpt("y", i.mime));
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(c).edit().putString("recent", a.toString()).apply();
    }

    /** True if the app can open this file again later. */
    static boolean keepAccess(Context c, Uri uri) {
        try {
            c.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // not offered by the sending app, but a saved folder may still cover it
        }
        return hasLastingAccess(c, uri);
    }

    static boolean hasLastingAccess(Context c, Uri uri) {
        for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
            if (!p.isReadPermission()) continue;
            if (p.getUri().equals(uri)) return true;
            if (uri.toString().startsWith(p.getUri() + "/document/")) return true;
        }
        return false;
    }

    static List<Uri> folders(Context c) {
        List<Uri> list = new ArrayList<>();
        for (UriPermission p : c.getContentResolver().getPersistedUriPermissions()) {
            if (p.isReadPermission() && DocumentsContract.isTreeUri(p.getUri())) list.add(p.getUri());
        }
        return list;
    }

    /** Finds a readable address for a file id, using the folders the user has added. */
    static Uri inFolders(Context c, String docId) {
        for (Uri tree : folders(c)) {
            if (!Paths.STORAGE.equals(tree.getAuthority())) continue;
            if (Paths.isInside(DocumentsContract.getTreeDocumentId(tree), docId)) {
                return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            }
        }
        return null;
    }

    static Bytes read(Context c, Item item) throws IOException {
        Bytes r = new Bytes();
        Uri u = Uri.parse(item.uri);
        if ("content".equals(u.getScheme())) {
            try (InputStream in = open(c.getContentResolver(), u)) {
                fill(r, in);
                return r;
            } catch (IOException | RuntimeException e) {
                // Providers are other apps and may throw anything.
                if (item.copy == null) throw e instanceof IOException ? (IOException) e : new IOException(e);
            }
        }
        if (item.copy == null) throw new IOException("No file");
        try (InputStream in = new FileInputStream(new File(c.getFilesDir(), item.copy))) {
            fill(r, in);
            r.fromCopy = true;
        }
        return r;
    }

    static InputStream open(ContentResolver cr, Uri uri) throws IOException {
        InputStream in = cr.openInputStream(uri);
        if (in == null) throw new IOException("Empty stream");
        return in;
    }

    private static void fill(Bytes r, InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            int room = MAX_BYTES - out.size();
            if (n > room) {
                out.write(buf, 0, room);
                r.truncated = true;
                break;
            }
            out.write(buf, 0, n);
        }
        r.data = out.toByteArray();
    }

    static String saveCopy(Context c, String existing, byte[] data) {
        String name = existing != null ? existing : "copy-" + java.util.UUID.randomUUID();
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), name))) {
            out.write(data);
            return name;
        } catch (IOException e) {
            return existing;
        }
    }
}
