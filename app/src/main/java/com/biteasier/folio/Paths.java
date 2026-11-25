package com.biteasier.folio;

/** File ids of Android's storage provider look like "primary:Notes/a.md". */
final class Paths {
    static final String STORAGE = "com.android.externalstorage.documents";

    private Paths() {}

    static boolean isInside(String folderId, String docId) {
        if (docId.equals(folderId)) return true;
        return docId.startsWith(folderId.endsWith(":") ? folderId : folderId + "/");
    }

    /** "primary:Notes/a.md" to {"primary", "Notes", "a.md"}; null if it has another form. */
    static String[] toSegments(String docId) {
        int colon = docId == null ? -1 : docId.indexOf(':');
        if (colon <= 0 || colon == docId.length() - 1) return null;
        return (docId.substring(0, colon) + "/" + docId.substring(colon + 1)).split("/");
    }

    /** The reverse of toSegments; null if the path does not name a file or tries to climb out. */
    static String toDocId(java.util.List<String> segments) {
        if (segments.size() < 2) return null;
        StringBuilder b = new StringBuilder(segments.get(0)).append(':');
        for (int i = 1; i < segments.size(); i++) {
            String s = segments.get(i);
            if (s.isEmpty() || s.equals(".") || s.equals("..") || s.indexOf('/') >= 0 || s.indexOf(0) >= 0) return null;
            if (i > 1) b.append('/');
            b.append(s);
        }
        return b.toString();
    }
}
