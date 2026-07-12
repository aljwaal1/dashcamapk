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
import android.os.Handler;
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
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SurfaceHolder.Callback, LocationListener {
    private static final int REQ_PERMISSIONS = 44;

    private final Handler timerHandler = new Handler();
    private SurfaceView preview;
    private Camera camera;
    private MediaRecorder recorder;
    private boolean recording;
    private boolean recordAudio;
    private File currentFile;
    private TextView speedText;
    private TextView statusText;
    private TextView timerText;
    private Button recordButton;
    private Button audioButton;
    private Button cameraButton;
    private LocationManager locationManager;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private long recordingStartedAt;

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            if (!recording) return;
            long seconds = Math.max(0L, (System.currentTimeMillis() - recordingStartedAt) / 1000L);
            long minutes = seconds / 60L;
            seconds %= 60L;
            timerText.setText(String.format(Locale.US, "%02d:%02d", minutes, seconds));
            timerHandler.postDelayed(this, 500L);
        }
    };

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
        topBar.setPadding(dp(12), dp(7), dp(12), dp(7));
        topBar.setBackgroundColor(0xAA000000);

        statusText = label("جاهز للتسجيل", Gravity.LEFT);
        timerText = label("00:00", Gravity.CENTER);
        speedText = label("0 كم/س", Gravity.RIGHT);
        topBar.addView(statusText, new LinearLayout.LayoutParams(0, dp(46), 1.4f));
        topBar.addView(timerText, new LinearLayout.LayoutParams(0, dp(46), .7f));
        topBar.addView(speedText, new LinearLayout.LayoutParams(0, dp(46), 1f));
        root.addView(topBar, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(60), Gravity.TOP));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(8), dp(7), dp(8), dp(7));
        controls.setBackgroundColor(0xAA000000);

        audioButton = controlButton("الصوت: مغلق");
        audioButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) {
                    Toast.makeText(MainActivity.this, "غيّر إعداد الصوت قبل بدء التسجيل", Toast.LENGTH_SHORT).show();
                    return;
                }
                recordAudio = !recordAudio;
                audioButton.setText(recordAudio ? "الصوت: يعمل" : "الصوت: مغلق");
            }
        });

        recordButton = controlButton("ابدأ التسجيل");
        recordButton.setTextSize(18);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) stopRecording(); else startRecording();
            }
        });

        cameraButton = controlButton("تبديل الكاميرا");
        cameraButton.setEnabled(Camera.getNumberOfCameras() > 1);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (recording) {
                    Toast.makeText(MainActivity.this, "أوقف التسجيل أولاً", Toast.LENGTH_SHORT).show();
                    return;
                }
                cameraId = cameraId == Camera.CameraInfo.CAMERA_FACING_BACK
                        ? Camera.CameraInfo.CAMERA_FACING_FRONT
                        : Camera.CameraInfo.CAMERA_FACING_BACK;
                openCamera(preview.getHolder());
            }
        });

        controls.addView(audioButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        controls.addView(recordButton, new LinearLayout.LayoutParams(0, dp(58), 1.35f));
        controls.addView(cameraButton, new LinearLayout.LayoutParams(0, dp(54), 1f));
        root.addView(controls, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.BOTTOM));

        setContentView(root);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        requestNeededPermissions();
    }

    private TextView label(String text, int gravity) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(19);
        view.setGravity(gravity | Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button controlButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestNeededPermissions() {
        if (android.os.Build.VERSION.SDK_INT < 23) {
            startLocation();
            return;
        }
        requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQ_PERMISSIONS);
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

    @Override public void surfaceCreated(SurfaceHolder holder) { openCamera(holder); }

    private void openCamera(SurfaceHolder holder) {
        if (recording) return;
        try {
            releaseCamera();
            camera = Camera.open(cameraId);
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(cameraId, info);
            camera.setDisplayOrientation(info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? 180 : 0);
            Camera.Parameters params = camera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
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
            if (recordAudio) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            CamcorderProfile profile = CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)
                    ? CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_480P)
                    : CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_LOW);
            if (recordAudio) {
                recorder.setProfile(profile);
            } else {
                recorder.setOutputFormat(profile.fileFormat);
                recorder.setVideoEncoder(profile.videoCodec);
                recorder.setVideoEncodingBitRate(profile.videoBitRate);
                recorder.setVideoFrameRate(profile.videoFrameRate);
                recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
            }
            recorder.setOutputFile(currentFile.getAbsolutePath());
            recorder.setPreviewDisplay(preview.getHolder().getSurface());
            recorder.prepare();
            recorder.start();

            recording = true;
            recordingStartedAt = System.currentTimeMillis();
            timerText.setText("00:00");
            timerHandler.post(timerTick);
            recordButton.setText("إيقاف وحفظ");
            statusText.setText("● جاري التسجيل");
            audioButton.setEnabled(false);
            cameraButton.setEnabled(false);
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); } catch (Exception ignored) { }
            Toast.makeText(this, "تعذر بدء التسجيل: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        if (!recording) return;
        timerHandler.removeCallbacks(timerTick);
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            if (currentFile != null) currentFile.delete();
        }
        releaseRecorder();
        recording = false;
        recordButton.setText("ابدأ التسجيل");
        statusText.setText("تم حفظ الفيديو");
        audioButton.setEnabled(true);
        cameraButton.setEnabled(Camera.getNumberOfCameras() > 1);

        try {
            camera.lock();
            camera.reconnect();
            camera.setPreviewDisplay(preview.getHolder());
            camera.startPreview();
        } catch (Exception ignored) { }

        if (currentFile != null && currentFile.exists() && currentFile.length() > 0) {
            MediaScannerConnection.scanFile(this,
                    new String[]{currentFile.getAbsolutePath()},
                    new String[]{"video/mp4"}, null);
            Toast.makeText(this, "تم الحفظ في Movies/DashCamTrip", Toast.LENGTH_LONG).show();
        } else {
            statusText.setText("لم يُحفظ الفيديو");
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
        timerHandler.removeCallbacks(timerTick);
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