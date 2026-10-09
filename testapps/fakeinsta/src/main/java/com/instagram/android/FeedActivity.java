package com.instagram.android;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Home feed: a list with an inline like button and a bottom nav where "Home" is selected. */
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
        setContentView(root);
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
