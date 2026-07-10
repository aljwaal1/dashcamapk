package com.explapp.dashcamlegacy;

import android.app.Activity;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceHolder holder;
    private boolean recording = false;
    private Button recordButton;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        SurfaceView preview = new SurfaceView(this);
        holder = preview.getHolder();
        holder.addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        recordButton = new Button(this);
        recordButton.setText("بدء التسجيل");
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (recording) stopRecording(); else startRecording(); }
        });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(260, 90, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bp.bottomMargin = 24;
        root.addView(recordButton, bp);

        status = new TextView(this);
        status.setText("داش كام 4.4 - بدون صوت");
        status.setTextSize(18);
        status.setGravity(Gravity.CENTER);
        status.setBackgroundColor(0x88000000);
        status.setTextColor(0xFFFFFFFF);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-1, 60, Gravity.TOP);
        root.addView(status, sp);
        setContentView(root);
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            status.setText("تعذر فتح الكاميرا");
        }
    }

    private void startRecording() {
        if (camera == null) return;
        try {
            File folder = new File(Environment.getExternalStorageDirectory(), "ExplAppDashCam");
            if (!folder.exists()) folder.mkdirs();
            String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            File output = new File(folder, name);
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setVideoSize(640, 480);
            recorder.setVideoFrameRate(24);
            recorder.setVideoEncodingBitRate(2000000);
            recorder.setPreviewDisplay(holder.getSurface());
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recordButton.setText("إيقاف وحفظ");
            status.setText("جاري التسجيل: " + name);
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); } catch (Exception ignored) {}
            status.setText("تعذر بدء التسجيل");
        }
    }

    private void stopRecording() {
        try { recorder.stop(); } catch (Exception ignored) {}
        releaseRecorder();
        try { camera.lock(); camera.startPreview(); } catch (Exception ignored) {}
        recording = false;
        recordButton.setText("بدء التسجيل");
        status.setText("تم حفظ الفيديو في ExplAppDashCam");
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { releaseAll(); }
    @Override protected void onPause() { super.onPause(); releaseAll(); }

    private void releaseAll() {
        if (recording) stopRecording();
        releaseRecorder();
        if (camera != null) {
            try { camera.stopPreview(); camera.release(); } catch (Exception ignored) {}
            camera = null;
        }
    }
}
