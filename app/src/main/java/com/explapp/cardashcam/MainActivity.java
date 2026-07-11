package com.explapp.cardashcam;

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
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
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
    private static final int REQ_PERMISSIONS = 44;

    private SurfaceView preview;
    private Camera camera;
    private MediaRecorder recorder;
    private boolean recording;
    private File currentFile;
    private TextView speedText;
    private TextView statusText;
    private Button recordButton;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        root.addView(preview, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        topBar.setBackgroundColor(0x99000000);

        statusText = label("جاهز للتسجيل");
        speedText = label("0 كم/س");
        speedText.setGravity(Gravity.RIGHT);
        topBar.addView(statusText, new LinearLayout.LayoutParams(0, dp(44), 1f));
        topBar.addView(speedText, new LinearLayout.LayoutParams(0, dp(44), 1f));

        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP);
        root.addView(topBar, topParams);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        controls.setBackgroundColor(0x99000000);

        recordButton = new Button(this);
        recordButton.setText("ابدأ التسجيل");
        recordButton.setTextSize(18);
        recordButton.setAllCaps(false);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording(); else startRecording();
            }
        });
        controls.addView(recordButton, new LinearLayout.LayoutParams(dp(210), dp(54)));

        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM);
        root.addView(controls, controlParams);
        setContentView(root);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestNeededPermissions();
    }

    private TextView label(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(20);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestNeededPermissions() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            startLocation();
            return;
        }
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        requestPermissions(permissions, REQ_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMISSIONS) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "إذن الكاميرا ضروري لتشغيل التطبيق", Toast.LENGTH_LONG).show();
            }
            startLocation();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        openCamera(holder);
    }

    private void openCamera(SurfaceHolder holder) {
        try {
            releaseCamera();
            camera = Camera.open();
            camera.setDisplayOrientation(0);
            Camera.Parameters params = camera.getParameters();
            if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            camera.setParameters(params);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
            statusText.setText("الكاميرا جاهزة");
        } catch (Exception e) {
            statusText.setText("تعذر فتح الكاميرا");
            Toast.makeText(this, "تعذر تشغيل الكاميرا: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera == null || recording) return;
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception ignored) { }
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        if (recording) stopRecording();
        releaseCamera();
    }

    private void startRecording() {
        if (camera == null) {
            Toast.makeText(this, "الكاميرا غير جاهزة", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "DashCamTrip");
            if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء مجلد الحفظ");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            currentFile = new File(dir, "dashcam_" + stamp + ".mp4");

            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            CamcorderProfile profile;
            if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
            } else {
                profile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
            }
            recorder.setProfile(profile);
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.prepare();
            recorder.start();

            recording = true;
            recordButton.setText("إيقاف وحفظ");
            statusText.setText("● جاري التسجيل");
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); } catch (Exception ignored) { }
            Toast.makeText(this, "تعذر بدء التسجيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            if (currentFile != null) currentFile.delete();
        }
        releaseRecorder();
        recording = false;
        recordButton.setText("ابدأ التسجيل");
        statusText.setText("تم حفظ الفيديو");

        try {
            camera.lock();
            camera.reconnect();
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (Exception ignored) { }

        if (currentFile != null && currentFile.exists()) {
            MediaScannerConnection.scanFile(this,
                    new String[]{currentFile.getAbsolutePath()},
                    new String[]{"video/mp4"}, null);
            Toast.makeText(this, "تم الحفظ في Movies/DashCamTrip", Toast.LENGTH_LONG).show();
        }
    }

    private void startLocation() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this);
        } catch (SecurityException ignored) {
            speedText.setText("GPS غير متاح");
        } catch (IllegalArgumentException ignored) {
            speedText.setText("لا يوجد GPS");
        }
    }

    @Override public void onLocationChanged(Location location) {
        float kmh = location.hasSpeed() ? location.getSpeed() * 3.6f : 0f;
        speedText.setText(Math.round(kmh) + " كم/س");
    }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { speedText.setText("GPS متوقف"); }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            try { camera.stopPreview(); } catch (Exception ignored) { }
            try { camera.release(); } catch (Exception ignored) { }
            camera = null;
        }
    }

    @Override protected void onPause() {
        if (recording) stopRecording();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (SecurityException ignored) { }
        }
        releaseCamera();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (preview != null && preview.getHolder().getSurface().isValid()) openCamera(preview.getHolder());
        startLocation();
    }
}
