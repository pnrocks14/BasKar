package com.instagram.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Home feed: a list with an inline like button and a bottom nav where "Home" is selected.
 * Like the real app, it keeps a Reels player alive off screen, which must not count as Reels.
 */
public class FeedActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        TextView title = new TextView(this);
        title.setText("Fake feed");
        title.setTextSize(28);
        title.setPadding(40, 160, 40, 40);
        root.addView(title);
        TextView like = new TextView(this);
        like.setText("♡");
        like.setContentDescription("Like");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(120, 120, Gravity.START | Gravity.CENTER_VERTICAL);
        lp.leftMargin = 40;
        root.addView(like, lp);
        root.addView(Nav.build(this, false), new FrameLayout.LayoutParams(-1, 180, Gravity.BOTTOM));
        root.addView(hiddenReels(), new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    /** A full Reels player with its buttons, still attached and visible, but moved off screen. */
    private FrameLayout hiddenReels() {
        FrameLayout player = new FrameLayout(this);
        player.setId(R.id.clips_viewer_view_pager);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        for (String label : new String[] {"Like", "Comment", "Share"}) {
            TextView b = new TextView(this);
            b.setText(label.substring(0, 1));
            b.setContentDescription(label);
            actions.addView(b, new LinearLayout.LayoutParams(120, 140));
        }
        player.addView(actions, new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL));
        player.setTranslationX(5000);
        return player;
    }

    static final class Nav {
        static LinearLayout build(Activity a, boolean reelsSelected) {
            LinearLayout nav = new LinearLayout(a);
            nav.setOrientation(LinearLayout.HORIZONTAL);
            nav.setBackgroundColor(0xFFF0F0F0);
            nav.addView(tab(a, "Home", !reelsSelected, null), new LinearLayout.LayoutParams(0, -1, 1));
            nav.addView(tab(a, "Reels", reelsSelected, reelsSelected ? null : () -> a.startActivity(new Intent(a, ReelsActivity.class))),
                new LinearLayout.LayoutParams(0, -1, 1));
            return nav;
        }

        static TextView tab(Activity a, String label, boolean selected, Runnable onClick) {
            TextView t = new TextView(a);
            t.setText(label);
            t.setContentDescription(label);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(18);
            t.setSelected(selected);
            if (onClick != null) t.setOnClickListener(v -> onClick.run());
            return t;
        }
    }
}
