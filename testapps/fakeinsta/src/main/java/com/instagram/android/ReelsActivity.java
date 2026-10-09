package com.instagram.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Reels: a full-screen vertical list, like/comment/share on the right, and the Reels tab selected. */
public class ReelsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);

        RecyclerView pager = new RecyclerView(this);
        pager.setLayoutManager(new LinearLayoutManager(this));
        pager.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextView v = new TextView(parent.getContext());
                v.setLayoutParams(new RecyclerView.LayoutParams(-1, parent.getHeight() > 0 ? parent.getHeight() : 2000));
                v.setTextColor(Color.WHITE);
                v.setTextSize(32);
                v.setGravity(Gravity.CENTER);
                return new RecyclerView.ViewHolder(v) { };
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                ((TextView) holder.itemView).setText("Reel " + (position + 1));
                holder.itemView.setBackgroundColor(position % 2 == 0 ? 0xFF223344 : 0xFF334422);
            }
            @Override public int getItemCount() { return 20; }
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        for (String label : new String[] {"Like", "Comment", "Share"}) {
            TextView b = new TextView(this);
            b.setText(label.substring(0, 1));
            b.setContentDescription(label);
            b.setTextColor(Color.WHITE);
            b.setTextSize(24);
            b.setGravity(Gravity.CENTER);
            actions.addView(b, new LinearLayout.LayoutParams(120, 140));
        }
        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.CENTER_VERTICAL);
        alp.rightMargin = 16;
        root.addView(actions, alp);

        TextView audio = new TextView(this);
        audio.setText("Original audio");
        audio.setTextColor(Color.WHITE);
        FrameLayout.LayoutParams aud = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        aud.bottomMargin = 220;
        aud.leftMargin = 40;
        root.addView(audio, aud);

        root.addView(FeedActivity.Nav.build(this, true), new FrameLayout.LayoutParams(-1, 180, Gravity.BOTTOM));
        setContentView(root);
    }
}
