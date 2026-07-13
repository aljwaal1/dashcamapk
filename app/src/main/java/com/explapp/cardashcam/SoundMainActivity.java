package com.explapp.cardashcam;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Lightweight, app-specific interaction feedback for the Android 4.4 build only. */
public class SoundMainActivity extends MainActivity {
    private final Handler observer = new Handler();
    private ToneGenerator uiTones;
    private float downX;
    private float downY;
    private long lastSoundAt;
    private String lastSnapshot = "";

    @Override public void onCreate(Bundle savedInstanceState) {
        uiTones = new ToneGenerator(AudioManager.STREAM_MUSIC, 48);
        super.onCreate(savedInstanceState);
        observer.post(statusWatcher);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downX = event.getRawX();
            downY = event.getRawY();
        } else if (event.getAction() == MotionEvent.ACTION_UP
                && Math.abs(event.getRawX() - downX) < 20f
                && Math.abs(event.getRawY() - downY) < 20f) {
            View target = findClickableView(getWindow().getDecorView(), event.getRawX(), event.getRawY());
            if (target != null) playFor(target);
        }
        return super.dispatchTouchEvent(event);
    }

    private void playFor(View view) {
        String label = view instanceof TextView ? ((TextView) view).getText().toString() : "";
        if (containsAny(label, "حذف", "إلغاء")) {
            play(ToneGenerator.TONE_PROP_NACK, 100);
        } else if (containsAny(label, "بدء التسجيل")) {
            play(ToneGenerator.TONE_PROP_ACK, 125);
        } else if (containsAny(label, "إيقاف التسجيل", "إيقاف")) {
            play(ToneGenerator.TONE_PROP_BEEP2, 95);
        } else if (containsAny(label, "تبديل")) {
            play(ToneGenerator.TONE_DTMF_6, 75);
        } else if (containsAny(label, "المقاطع", "الإعدادات", "التسجيل")) {
            play(ToneGenerator.TONE_DTMF_5, 65);
        } else if (containsAny(label, "تشغيل", "حفظ")) {
            play(ToneGenerator.TONE_PROP_ACK, 110);
        } else if (containsAny(label, "رجوع", "العودة")) {
            play(ToneGenerator.TONE_PROP_BEEP2, 80);
        } else {
            play(ToneGenerator.TONE_PROP_BEEP, 55);
        }
    }

    private final Runnable statusWatcher = new Runnable() {
        @Override public void run() {
            if (uiTones == null) return;
            String snapshot = collectText(getWindow().getDecorView());
            if (lastSnapshot.length() == 0) {
                lastSnapshot = snapshot;
            } else if (!snapshot.equals(lastSnapshot)) {
                if (containsAny(snapshot, "REC  ", "جاري التسجيل")) {
                    play(ToneGenerator.TONE_CDMA_CONFIRM, 100);
                } else if (containsAny(snapshot, "تم حفظ", "تم إيقاف التسجيل")) {
                    play(ToneGenerator.TONE_PROP_ACK, 150);
                } else if (containsAny(snapshot, "أوقف التسجيل قبل مغادرة", "تعذر", "غير متاح")) {
                    play(ToneGenerator.TONE_PROP_NACK, 150);
                }
                lastSnapshot = snapshot;
            }
            observer.postDelayed(this, 300L);
        }
    };

    private String collectText(View view) {
        StringBuilder builder = new StringBuilder();
        appendText(view, builder);
        return builder.toString();
    }

    private void appendText(View view, StringBuilder builder) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) builder.append('|').append(((TextView) view).getText());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) appendText(group.getChildAt(i), builder);
        }
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

    private void play(int tone, int durationMs) {
        if (uiTones == null || System.currentTimeMillis() - lastSoundAt < 70L) return;
        lastSoundAt = System.currentTimeMillis();
        uiTones.startTone(tone, durationMs);
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    @Override public void onBackPressed() {
        play(ToneGenerator.TONE_PROP_BEEP2, 80);
        super.onBackPressed();
    }

    @Override protected void onDestroy() {
        observer.removeCallbacksAndMessages(null);
        if (uiTones != null) {
            uiTones.release();
            uiTones = null;
        }
        super.onDestroy();
    }
}
