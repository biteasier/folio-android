package com.biteasier.folio;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** Home: open a file, open a folder, recent files. */
public class MainActivity extends Activity {
    private static final int FILE = 1, FOLDER = 2;

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build();
    }

    private void build() {
        Ui.Colors k = Ui.colors(this);
        LinearLayout root = Ui.root(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, 0, Ui.dp(this, 24));
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView name = Ui.text(this, "Folio", 30, k.text);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", 0));
        name.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), 0, Ui.dp(this, 16));
        list.addView(name);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -1, 1);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -1, 1);
        half2.setMarginStart(Ui.dp(this, 12));
        buttons.addView(Ui.button(this, "Open file", true, () -> pick(FILE)), half);
        buttons.addView(Ui.button(this, "Add folder", false, () -> pick(FOLDER)), half2);
        list.addView(buttons);

        List<Uri> folders = Library.folders(this);
        if (!folders.isEmpty()) {
            list.addView(Ui.section(this, "Folders"));
            for (Uri u : folders) {
                View row = Ui.row(this, R.drawable.ic_folder, BrowseActivity.folderName(u), null, () -> browse(u));
                row.setOnLongClickListener(v -> {
                    confirm("Remove this folder from Folio?", "The folder and its files are not changed.", () ->
                            getContentResolver().releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION));
                    return true;
                });
                list.addView(row);
            }
        }

        List<Library.Item> recent = Library.recent(this);
        if (recent.isEmpty()) {
            TextView hint = Ui.text(this, "Open a Markdown or text file to start reading.\n\nYou can also choose Folio from Open with or Share in any app.", 15, k.muted);
            hint.setPadding(Ui.dp(this, 20), Ui.dp(this, 28), Ui.dp(this, 20), 0);
            hint.setLineSpacing(0, 1.25f);
            list.addView(hint);
        } else {
            LinearLayout head = new LinearLayout(this);
            head.setGravity(android.view.Gravity.BOTTOM);
            head.addView(Ui.section(this, "Recent"), new LinearLayout.LayoutParams(0, -2, 1));
            TextView clear = Ui.section(this, "Clear");
            clear.setTextColor(k.accent);
            clear.setAccessibilityHeading(false);
            clear.setMinHeight(Ui.dp(this, 48));
            clear.setOnClickListener(v -> confirm("Clear recent files?", "Reading positions and saved copies are removed. Your original files are not changed.", () -> Library.clear(this)));
            head.addView(clear);
            list.addView(head);
            for (Library.Item i : recent) {
                String when = DateUtils.getRelativeTimeSpanString(i.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString();
                if (i.copy != null) when += " · saved copy";
                View row = Ui.row(this, R.drawable.ic_file, i.name, when, () -> startActivity(new Intent(this, ReaderActivity.class)
                        .setData(Uri.parse(i.uri)).putExtra("name", i.name)));
                row.setOnLongClickListener(v -> {
                    confirm("Remove from recent files?", i.name, () -> Library.remove(this, i.uri));
                    return true;
                });
                list.addView(row);
            }
        }
        setContentView(root);
        Ui.paint(this, root);
    }

    private void confirm(String title, String message, Runnable yes) {
        Ui.dialog(this).setTitle(title).setMessage(message).setNegativeButton("Cancel", null)
                .setPositiveButton(title.startsWith("Clear") ? "Clear" : "Remove", (d, w) -> {
                    yes.run();
                    build();
                }).show();
    }

    private void pick(int what) {
        // No type filter: pickers grey out .md files when they do not know the type.
        Intent i = what == FOLDER ? new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                : new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try {
            startActivityForResult(i, what);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No file picker found on this phone", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (request == FOLDER) {
            try {
                getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException e) {
                Toast.makeText(this, "This folder can’t be added", Toast.LENGTH_LONG).show();
                return;
            }
            browse(u);
        } else {
            startActivity(new Intent(this, ReaderActivity.class).setData(u));
        }
    }

    private void browse(Uri tree) {
        startActivity(new Intent(this, BrowseActivity.class).setData(tree)
                .putExtra("id", DocumentsContract.getTreeDocumentId(tree))
                .putExtra("name", BrowseActivity.folderName(tree)));
    }
}
