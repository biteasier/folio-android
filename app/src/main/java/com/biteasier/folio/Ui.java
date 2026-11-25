package com.biteasier.folio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small toolkit: colors, icons and rows shared by the three screens. */
final class Ui {
    static final String[] THEMES = {"System", "Light", "Sepia", "Dark", "Black"};

    static final class Colors {
        final String css;
        final boolean dark;
        final int bg, text, muted, line, accent, card;

        Colors(String css, boolean dark, int bg, int text, int muted, int line, int accent, int card) {
            this.css = css;
            this.dark = dark;
            this.bg = bg;
            this.text = text;
            this.muted = muted;
            this.line = line;
            this.accent = accent;
            this.card = card;
        }
    }

    private static final Colors LIGHT = new Colors("light", false, 0xFFFFFFFF, 0xFF1C2230, 0xFF5B6474, 0xFFE1E5EC, 0xFF1D4ED8, 0xFFF3F5F9);
    private static final Colors SEPIA = new Colors("sepia", false, 0xFFF8F1E3, 0xFF3B3024, 0xFF75664F, 0xFFE3D7BF, 0xFF8A4B14, 0xFFF0E6D2);
    private static final Colors DARK = new Colors("dark", true, 0xFF15181E, 0xFFDDE2EA, 0xFF98A2B3, 0xFF2C323D, 0xFF8AB0FF, 0xFF1F242D);
    private static final Colors BLACK = new Colors("black", true, 0xFF000000, 0xFFD5DAE2, 0xFF919BAB, 0xFF262B33, 0xFF8AB0FF, 0xFF14171C);

    private Ui() {}

    static Colors colors(Context c) {
        switch (Library.prefs(c).getInt("theme", 0)) {
            case 1: return LIGHT;
            case 2: return SEPIA;
            case 3: return DARK;
            case 4: return BLACK;
            default:
                int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                return night == Configuration.UI_MODE_NIGHT_YES ? DARK : LIGHT;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, colors(c).dark
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert
                : android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
    }

    /** Vertical root that draws behind the system bars and keeps content clear of them. */
    static LinearLayout root(Activity a) {
        a.getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v, in) -> {
            Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(i.left, i.top, i.right, i.bottom);
            return WindowInsets.CONSUMED;
        });
        return root;
    }

    static void paint(Activity a, View root) {
        Colors c = colors(a);
        root.setBackgroundColor(c.bg);
        a.getWindow().setStatusBarColor(0);
        a.getWindow().setNavigationBarColor(0);
        WindowInsetsController w = a.getWindow().getInsetsController();
        int bars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        if (w != null) w.setSystemBarsAppearance(c.dark ? 0 : bars, bars);
    }

    static LinearLayout bar(Context c) {
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(c, 4), 0, dp(c, 4), 0);
        bar.setMinimumHeight(dp(c, 56));
        return bar;
    }

    static ImageButton icon(Context c, int res, String label, Runnable action) {
        ImageButton b = new ImageButton(c);
        b.setImageResource(res);
        b.setContentDescription(label);
        b.setTooltipText(label);
        b.setImageTintList(ColorStateList.valueOf(colors(c).text));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808898), null, null));
        b.setOnClickListener(v -> action.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 48), dp(c, 48)));
        return b;
    }

    static TextView text(Context c, CharSequence s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    static TextView title(Context c, CharSequence s) {
        TextView t = text(c, s, 17, colors(c).text);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        return t;
    }

    static TextView section(Context c, String s) {
        TextView t = text(c, s, 13, colors(c).muted);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setPadding(dp(c, 20), dp(c, 20), dp(c, 20), dp(c, 6));
        t.setAccessibilityHeading(true);
        return t;
    }

    static TextView button(Context c, String s, boolean filled, Runnable action) {
        Colors k = colors(c);
        TextView t = text(c, s, 15, filled ? k.bg : k.accent);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(c, 14));
        shape.setColor(filled ? k.accent : k.card);
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44808898), shape, null));
        t.setMinHeight(dp(c, 50));
        t.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        t.setClickable(true);
        t.setFocusable(true);
        t.setOnClickListener(v -> action.run());
        t.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View v, android.view.accessibility.AccessibilityNodeInfo i) {
                super.onInitializeAccessibilityNodeInfo(v, i);
                i.setClassName("android.widget.Button");
            }
        });
        return t;
    }

    static LinearLayout row(Context c, int icon, String title, String detail, Runnable action) {
        Colors k = colors(c);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 20), dp(c, 9), dp(c, 20), dp(c, 9));
        row.setMinimumHeight(dp(c, 56));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808898), null, new android.graphics.drawable.ColorDrawable(-1)));
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(ColorStateList.valueOf(k.accent));
        row.addView(i, new LinearLayout.LayoutParams(dp(c, 24), dp(c, 24)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 16), 0, 0, 0);
        TextView t = text(c, title, 16, k.text);
        t.setSingleLine();
        t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        col.addView(t);
        if (detail != null) col.addView(text(c, detail, 13, k.muted));
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(v -> action.run());
        return row;
    }
}
