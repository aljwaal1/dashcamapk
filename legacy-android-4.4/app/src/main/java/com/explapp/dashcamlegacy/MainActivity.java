package com.explapp.dashcamlegacy;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback, LocationListener {
    private static final int PERMISSION_REQUEST = 41;
    private Camera camera;
    private MediaRecorder recorder;
    private SurfaceHolder holder;
    private LocationManager locationManager;
    private boolean recording;
    private boolean audioEnabled;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private Button recordButton;
    private Button audioButton;
    private Button switchButton;
    private TextView status;
    private TextView timer;
    private TextView speed;
    private File currentOutput;
    private long recordingStarted;
    private final Handler handler = new Handler();
    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long total = (System.currentTimeMillis() - recordingStarted) / 1000L;
            timer.setText(String.format(Locale.US, "%02d:%02d", total / 60L, total % 60L));
            handler.postDelayed(this, 500L);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();
        if (Build.VERSION.SDK_INT >= 23 && !hasPermissions()) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST);
        } else {
            startLocationUpdates();
        }
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView preview = new SurfaceView(this);
        holder = preview.getHolder();
        holder.addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(14, 8, 14, 8);
        top.setBackgroundColor(0xAA101820);
        status = label("جاهز للتسجيل", 17);
        timer = label("00:00", 22);
        speed = label("0 كم/س", 18);
        top.addView(status, new LinearLayout.LayoutParams(0, 58, 1));
        top.addView(timer, new LinearLayout.LayoutParams(120, 58));
        top.addView(speed, new LinearLayout.LayoutParams(150, 58));
        root.addView(top, new FrameLayout.LayoutParams(-1, 74, Gravity.TOP));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(8, 8, 8, 8);
        controls.setBackgroundColor(0xAA101820);

        audioButton = button("الصوت: مغلق");
        audioButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) { toast("أوقف التسجيل لتغيير الصوت"); return; }
                audioEnabled = !audioEnabled;
                audioButton.setText(audioEnabled ? "الصوت: يعمل" : "الصوت: مغلق");
            }
        });
        recordButton = button("● بدء التسجيل");
        recordButton.setTextSize(18);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (recording) stopRecording(); else startRecording(); }
        });
        switchButton = button("تبديل الكاميرا");
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (!recording) switchCamera(); else toast("أوقف التسجيل أولًا"); }
        });
        controls.addView(audioButton, new LinearLayout.LayoutParams(0, 68, 1));
        controls.addView(recordButton, new LinearLayout.LayoutParams(0, 68, 1.35f));
        controls.addView(switchButton, new LinearLayout.LayoutParams(0, 68, 1));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, 88, Gravity.BOTTOM);
        root.addView(controls, cp);
        setContentView(root);
    }

    private TextView label(String text, float size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.WHITE); view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setAllCaps(false); return b;
    }

    private boolean hasPermissions() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST && hasPermissions()) startLocationUpdates();
        else status.setText("يلزم السماح بالكاميرا والتخزين");
    }

    @Override public void surfaceCreated(SurfaceHolder h) { openCamera(); }
    @Override public void surfaceChanged(SurfaceHolder h, int format, int width, int height) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { releaseAll(); }

    private void openCamera() {
        releaseCamera();
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters p = camera.getParameters();
            if (p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                camera.setParameters(p);
            }
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            status.setText("جاهز للتسجيل");
        } catch (Exception e) {
            status.setText("تعذر فتح الكاميرا");
            releaseCamera();
        }
    }

    private void switchCamera() {
        int count = Camera.getNumberOfCameras();
        if (count < 2) { toast("لا توجد كاميرا أخرى"); return; }
        cameraId = cameraId == Camera.CameraInfo.CAMERA_FACING_BACK
                ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        openCamera();
    }

    private void startRecording() {
        if (camera == null) { toast("الكاميرا غير جاهزة"); return; }
        try {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DashCamTrip");
            if (!folder.exists() && !folder.mkdirs()) throw new Exception("folder");
            String name = "DC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
            currentOutput = new File(folder, name);
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            if (audioEnabled) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            CamcorderProfile profile = CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)
                    ? CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                    : CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            if (!audioEnabled) profile.audioCodec = -1;
            recorder.setOutputFormat(profile.fileFormat);
            recorder.setVideoEncoder(profile.videoCodec);
            recorder.setVideoFrameRate(profile.videoFrameRate);
            recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            recorder.setVideoEncodingBitRate(profile.videoBitRate);
            if (audioEnabled) {
                recorder.setAudioEncoder(profile.audioCodec);
                recorder.setAudioChannels(profile.audioChannels);
                recorder.setAudioSamplingRate(profile.audioSampleRate);
                recorder.setAudioEncodingBitRate(profile.audioBitRate);
            }
            recorder.setPreviewDisplay(holder.getSurface());
            recorder.setOutputFile(currentOutput.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recordingStarted = System.currentTimeMillis();
            recordButton.setText("■ إيقاف وحفظ");
            switchButton.setEnabled(false); audioButton.setEnabled(false);
            status.setText("جاري التسجيل");
            handler.post(ticker);
        } catch (Exception e) {
            safeRecorderRelease();
            try { camera.lock(); camera.startPreview(); } catch (Exception ignored) { }
            if (currentOutput != null && currentOutput.exists()) currentOutput.delete();
            status.setText("تعذر بدء التسجيل");
        }
    }

    private void stopRecording() {
        if (!recording) return;
        boolean valid = false;
        try { recorder.stop(); valid = currentOutput != null && currentOutput.exists() && currentOutput.length() > 1024; }
        catch (Exception ignored) { if (currentOutput != null) currentOutput.delete(); }
        safeRecorderRelease();
        recording = false;
        handler.removeCallbacks(ticker);
        recordButton.setText("● بدء التسجيل");
        switchButton.setEnabled(true); audioButton.setEnabled(true);
        try { camera.lock(); camera.startPreview(); } catch (Exception ignored) { }
        status.setText(valid ? "تم الحفظ في Movies/DashCamTrip" : "فشل حفظ الفيديو");
        if (valid) toast("تم حفظ الفيديو بنجاح");
    }

    private void startLocationUpdates() {
        try {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
        } catch (Exception ignored) { speed.setText("GPS غير متاح"); }
    }

    @Override public void onLocationChanged(Location location) {
        float kmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        speed.setText(Math.round(kmh) + " كم/س");
    }
    @Override public void onProviderDisabled(String provider) { speed.setText("GPS مغلق"); }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int statusValue, Bundle extras) { }

    private void safeRecorderRelease() {
        if (recorder != null) { try { recorder.reset(); recorder.release(); } catch (Exception ignored) { } recorder = null; }
    }
    private void releaseCamera() {
        if (camera != null) { try { camera.stopPreview(); camera.release(); } catch (Exception ignored) { } camera = null; }
    }
    private void releaseAll() {
        if (recording) stopRecording();
        safeRecorderRelease(); releaseCamera(); handler.removeCallbacksAndMessages(null);
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }

    @Override protected void onPause() { releaseAll(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (holder != null && holder.getSurface() != null && holder.getSurface().isValid() && camera == null) openCamera(); }
    @Override protected void onDestroy() {
        if (locationManager != null) try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        releaseAll(); super.onDestroy();
    }
}