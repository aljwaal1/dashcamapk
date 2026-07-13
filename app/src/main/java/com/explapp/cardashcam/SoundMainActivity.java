package com.explapp.cardashcam;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Lightweight interaction-sound layer for the Android 4.4 build.
 * It uses ToneGenerator only, so it adds no media files or external libraries.
 */
public class SoundMainActivity extends MainActivity {
    private ToneGenerator uiTones;
    private long lastSoundAt;

    @Override public void onCreate(Bundle savedInstanceState) {
        uiTones = new ToneGenerator(AudioManager.STREAM_MUSIC, 48);
        super.onCreate(savedInstanceState);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            View target = findClickableView(getWindow().getDecorView(), event.getRawX(), event.getRawY());
            if (target != null) playFor(target);
        }
        return super.dispatchTouchEvent(event);
    }

    private void playFor(View view) {
        long now = System.currentTimeMillis();
        if (now - lastSoundAt < 70L || uiTones == null) return;
        lastSoundAt = now;

        String label = view instanceof TextView ? ((TextView) view).getText().toString() : "";
        if (containsAny(label, "حذف", "إلغاء")) {
            uiTones.startTone(ToneGenerator.TONE_PROP_NACK, 90);
        } else if (containsAny(label, "بدء التسجيل", "تشغيل", "حفظ")) {
            uiTones.startTone(ToneGenerator.TONE_PROP_ACK, 115);
        } else if (containsAny(label, "إيقاف", "رجوع", "العودة")) {
            uiTones.startTone(ToneGenerator.TONE_PROP_BEEP2, 80);
        } else {
            uiTones.startTone(ToneGenerator.TONE_PROP_BEEP, 55);
        }
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private View findClickableView(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE) return null;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        boolean inside = rawX >= location[0] && rawX <= location[0] + view.getWidth()
                && rawY >= location[1] && rawY <= location[1] + view.getHeight();
        if (!inside) return null;

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View match = findClickableView(group.getChildAt(i), rawX, rawY);
                if (match != null) return match;
            }
        }
        return view.isClickable() ? view : null;
    }

    @Override protected void onDestroy() {
        if (uiTones != null) {
            uiTones.release();
            uiTones = null;
        }
        super.onDestroy();
    }
}
