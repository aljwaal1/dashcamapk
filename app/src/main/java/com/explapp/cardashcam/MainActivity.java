package com.explapp.cardashcam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity implements SurfaceHolder.Callback, LocationListener {
    private static final int REQUEST_PERMISSIONS = 81;
    private static final int REQUEST_AUDIO = 82;
    private static final long MIN_FREE_BYTES = 150L * 1024L * 1024L;
    private static final long RESERVED_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_RECORDING_BYTES = 1500L * 1024L * 1024L;
    private static final int NAVY = Color.rgb(7, 17, 31);
    private static final int BLUE = Color.rgb(14, 165, 233);
    private static final int RED = Color.rgb(239, 68, 68);
    private static final int GREEN = Color.rgb(16, 185, 129);
    private static final int SLATE = Color.rgb(100, 116, 139);

    private SurfaceView preview;
    private PreviewFrame previewFrame;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private MediaRecorder recorder;
    private LocationManager locationManager;
    private int cameraId = -1;
    private boolean surfaceReady;
    private boolean recording;
    private boolean audioEnabled;
    private boolean pendingAudioEnable;
    private long startedAt;
    private float speedKmh;
    private float maxSpeed;
    private File currentFile;

    private TextView speedText;
    private TextView clockText;
    private TextView statusText;
    private TextView timerText;
    private TextView cameraLabel;
    private TextView storageText;
    private Button recordButton;
    private Button switchButton;
    private Button audioButton;
    private final Handler handler = new Handler();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US);

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            clockText.setText(clockFormat.format(new Date()));
            if (recording) {
                timerText.setText("REC  " + formatDuration(System.currentTimeMillis() - startedAt));
            } else {
                timerText.setText("جاهز للتسجيل");
            }
            speedText.setText(String.valueOf(Math.round(speedKmh)));
            handler.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(NAVY);
            getWindow().setNavigationBarColor(NAVY);
        }
        audioEnabled = getPreferences(MODE_PRIVATE).getBoolean("audio", false);
        buildInterface();
        handler.post(ticker);
        requestNeededPermissions();
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238, 243, 248));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        header.setBackgroundColor(NAVY);

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("داش كام الرحلة", 20, Color.WHITE, Typeface.BOLD);
        TextView subtitle = text("تسجيل سريع وآمن • Android 4.4+", 12, Color.rgb(186, 230, 253), Typeface.NORMAL);
        titles.addView(title);
        titles.addView(subtitle);
        header.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        cameraLabel = badge("خلفية", BLUE);
        header.addView(cameraLabel);
        root.addView(header);

        previewFrame = new PreviewFrame(this);
        previewFrame.setBackgroundColor(Color.BLACK);
        preview = new SurfaceView(this);
        surfaceHolder = preview.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        previewFrame.setPreview(preview);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout topOverlay = new LinearLayout(this);
        topOverlay.setGravity(Gravity.CENTER_VERTICAL);
        topOverlay.setPadding(dp(12), dp(12), dp(12), 0);
        timerText = badge("جاهز للتسجيل", GREEN);
        topOverlay.addView(timerText);
        clockText = badge("--", Color.argb(185, 7, 17, 31));
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clockParams.gravity = Gravity.RIGHT;
        clockParams.leftMargin = dp(8);
        topOverlay.addView(clockText, clockParams);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        previewFrame.addView(topOverlay, topParams);

        LinearLayout speedPanel = new LinearLayout(this);
        speedPanel.setOrientation(LinearLayout.VERTICAL);
        speedPanel.setGravity(Gravity.CENTER);
        speedPanel.setPadding(dp(14), dp(8), dp(14), dp(8));
        speedPanel.setBackground(rounded(Color.argb(190, 7, 17, 31), dp(18)));
        speedText = text("0", 36, Color.WHITE, Typeface.BOLD);
        TextView unit = text("كم/س", 11, Color.rgb(186, 230, 253), Typeface.BOLD);
        speedPanel.addView(speedText);
        speedPanel.addView(unit);
        FrameLayout.LayoutParams speedParams = new FrameLayout.LayoutParams(dp(94), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT);
        speedParams.setMargins(0, 0, dp(12), dp(12));
        previewFrame.addView(speedPanel, speedParams);

        LinearLayout.LayoutParams cameraParams = new LinearLayout.LayoutParams(-1, 0, 1);
        cameraParams.setMargins(dp(10), dp(10), dp(10), 0);
        root.addView(previewFrame, cameraParams);

        LinearLayout dashboard = new LinearLayout(this);
        dashboard.setGravity(Gravity.CENTER_VERTICAL);
        dashboard.setPadding(dp(14), dp(8), dp(14), dp(6));
        statusText = text("جاري تجهيز الكاميرا…", 13, NAVY, Typeface.BOLD);
        dashboard.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        storageText = text(storageSummary(), 11, SLATE, Typeface.NORMAL);
        dashboard.addView(storageText);
        root.addView(dashboard);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(10), dp(4), dp(10), dp(12));

        switchButton = actionButton("تبديل", NAVY);
        switchButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { switchCamera(); }
        });
        controls.addView(switchButton, buttonParams());

        recordButton = actionButton("بدء التسجيل", RED);
        recordButton.setTextSize(16);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (recording) stopRecording(true); else startRecording();
            }
        });
        LinearLayout.LayoutParams mainButtonParams = buttonParams();
        mainButtonParams.weight = 1.35f;
        controls.addView(recordButton, mainButtonParams);

        audioButton = actionButton(audioEnabled ? "الصوت: نعم" : "الصوت: لا", audioEnabled ? GREEN : SLATE);
        audioButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { toggleAudio(); }
        });
        controls.addView(audioButton, buttonParams());
        root.addView(controls);

        TextView safety = text("ثبّت الهاتف قبل الحركة ولا تستخدم الأزرار أثناء القيادة", 11, Color.rgb(146, 64, 14), Typeface.BOLD);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(dp(8), dp(6), dp(8), dp(8));
        root.addView(safety);
        setContentView(root);
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> missing = new ArrayList<String>();
        addIfMissing(missing, Manifest.permission.CAMERA);
        addIfMissing(missing, Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT <= 28) addIfMissing(missing, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[missing.size()]), REQUEST_PERMISSIONS);
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) permissions.add(permission);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            pendingAudioEnable = false;
            if (granted) setAudioEnabled(true); else {
                setAudioEnabled(false);
                Toast.makeText(this, "لن يُسجل الصوت دون إذن الميكروفون", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == REQUEST_PERMISSIONS) {
            if (surfaceReady) openCamera(cameraId);
            if (camera != null) startLocation();
        }
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        openCamera(cameraId);
    }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null && !recording) startPreview();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        if (recording) stopRecording(false);
        releaseCamera();
    }

    private void openCamera(int requestedId) {
        if (!surfaceReady || camera != null) return;
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            statusText.setText("امنح إذن الكاميرا للبدء");
            return;
        }
        try {
            if (requestedId < 0) requestedId = findCamera(Camera.CameraInfo.CAMERA_FACING_BACK);
            if (requestedId < 0) requestedId = 0;
            cameraId = requestedId;
            camera = Camera.open(cameraId);
            configureCamera();
            camera.setPreviewDisplay(surfaceHolder);
            startPreview();
            startLocation();
            statusText.setText("الكاميرا جاهزة");
            switchButton.setEnabled(Camera.getNumberOfCameras() > 1);
        } catch (Exception e) {
            releaseCamera();
            statusText.setText("تعذر فتح الكاميرا");
            Toast.makeText(this, "أغلق أي تطبيق آخر يستخدم الكاميرا ثم أعد المحاولة", Toast.LENGTH_LONG).show();
        }
    }

    private void configureCamera() {
        Camera.Parameters params = camera.getParameters();
        List<Camera.Size> sizes = params.getSupportedPreviewSizes();
        Camera.Size best = chooseSize(sizes, 1280, 720);
        if (best != null) {
            params.setPreviewSize(best.width, best.height);
            int orientation = displayOrientation(cameraId);
            float ratio = (orientation == 90 || orientation == 270) ? best.height / (float) best.width : best.width / (float) best.height;
            previewFrame.setPreviewAspect(ratio);
        }
        if (params.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }
        camera.setParameters(params);
        camera.setDisplayOrientation(displayOrientation(cameraId));
    }

    private Camera.Size chooseSize(List<Camera.Size> sizes, int wantedWidth, int wantedHeight) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        long bestScore = Long.MAX_VALUE;
        for (Camera.Size size : sizes) {
            long score = Math.abs(size.width - wantedWidth) + Math.abs(size.height - wantedHeight) * 2L;
            if (score < bestScore) { best = size; bestScore = score; }
        }
        return best;
    }

    private void startPreview() {
        if (camera == null) return;
        try { camera.stopPreview(); } catch (Exception ignored) { }
        try { camera.setPreviewDisplay(surfaceHolder); camera.startPreview(); } catch (Exception ignored) { }
    }

    private int displayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (360 - ((info.orientation + degrees) % 360)) % 360;
        return (info.orientation - degrees + 360) % 360;
    }

    private int findCamera(int facing) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == facing) return i;
        }
        return -1;
    }

    private void switchCamera() {
        if (recording || Camera.getNumberOfCameras() < 2) return;
        Camera.CameraInfo current = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, current);
        int wanted = current.facing == Camera.CameraInfo.CAMERA_FACING_BACK ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
        int next = findCamera(wanted);
        if (next < 0) return;
        releaseCamera();
        cameraId = next;
        cameraLabel.setText(wanted == Camera.CameraInfo.CAMERA_FACING_BACK ? "خلفية" : "أمامية");
        openCamera(cameraId);
    }

    private void toggleAudio() {
        if (recording) {
            Toast.makeText(this, "غيّر إعداد الصوت قبل بدء التسجيل", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!audioEnabled && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!pendingAudioEnable) {
                pendingAudioEnable = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            }
            return;
        }
        setAudioEnabled(!audioEnabled);
    }

    private void setAudioEnabled(boolean enabled) {
        audioEnabled = enabled;
        getPreferences(MODE_PRIVATE).edit().putBoolean("audio", audioEnabled).apply();
        audioButton.setText(audioEnabled ? "الصوت: نعم" : "الصوت: لا");
        audioButton.setBackground(rounded(audioEnabled ? GREEN : SLATE, dp(16)));
    }

    private void startRecording() {
        if (camera == null || !surfaceReady) {
            Toast.makeText(this, "انتظر حتى تصبح الكاميرا جاهزة", Toast.LENGTH_SHORT).show();
            return;
        }
        if (audioEnabled && Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingAudioEnable = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_AUDIO);
            return;
        }
        File directory = recordingsDirectory();
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(this, "تعذر إنشاء مجلد الحفظ", Toast.LENGTH_LONG).show();
            return;
        }
        long available = availableBytes(directory);
        if (available < MIN_FREE_BYTES) {
            Toast.makeText(this, "المساحة المتاحة أقل من 150 MB. حرر مساحة قبل التسجيل", Toast.LENGTH_LONG).show();
            storageText.setText(storageSummary());
            return;
        }
        currentFile = new File(directory, "DASH_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4");
        try {
            camera.unlock();
            recorder = new MediaRecorder();
            recorder.setCamera(camera);
            if (audioEnabled) recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            CamcorderProfile profile = bestProfile(cameraId);
            if (audioEnabled) {
                recorder.setProfile(profile);
            } else {
                recorder.setOutputFormat(profile.fileFormat);
                recorder.setVideoEncoder(profile.videoCodec);
                recorder.setVideoFrameRate(profile.videoFrameRate);
                recorder.setVideoSize(profile.videoFrameWidth, profile.videoFrameHeight);
                recorder.setVideoEncodingBitRate(Math.min(profile.videoBitRate, 5000000));
            }
            recorder.setOutputFile(currentFile.getAbsolutePath());
            long safeLimit = Math.min(MAX_RECORDING_BYTES, available - RESERVED_BYTES);
            if (safeLimit > 10L * 1024L * 1024L) recorder.setMaxFileSize(safeLimit);
            recorder.setPreviewDisplay(surfaceHolder.getSurface());
            recorder.setOrientationHint(recordingOrientation(cameraId));
            recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override public void onError(MediaRecorder mediaRecorder, int what, int extra) { stopRecording(false); }
            });
            recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override public void onInfo(MediaRecorder mediaRecorder, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stopRecording(true);
                }
            });
            recorder.prepare();
            recorder.start();
            recording = true;
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            startedAt = System.currentTimeMillis();
            maxSpeed = 0;
            recordButton.setText("إيقاف وحفظ");
            recordButton.setBackground(rounded(NAVY, dp(16)));
            switchButton.setEnabled(false);
            audioButton.setEnabled(false);
            timerText.setBackground(rounded(RED, dp(16)));
            statusText.setText("جاري التسجيل • الشاشة ستبقى مضاءة");
        } catch (Exception e) {
            releaseRecorder();
            try { camera.lock(); startPreview(); } catch (Exception ignored) { }
            if (currentFile != null) currentFile.delete();
            Toast.makeText(this, "تعذر بدء التسجيل بهذه الجودة", Toast.LENGTH_LONG).show();
        }
    }

    private CamcorderProfile bestProfile(int id) {
        if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_720P)) return CamcorderProfile.get(id, CamcorderProfile.QUALITY_720P);
        if (CamcorderProfile.hasProfile(id, CamcorderProfile.QUALITY_480P)) return CamcorderProfile.get(id, CamcorderProfile.QUALITY_480P);
        return CamcorderProfile.get(id, CamcorderProfile.QUALITY_LOW);
    }

    private int recordingOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) return (info.orientation + degrees) % 360;
        return (info.orientation - degrees + 360) % 360;
    }

    private void stopRecording(boolean userRequested) {
        if (!recording) return;
        recording = false;
        boolean saved = false;
        try {
            recorder.stop();
            saved = currentFile != null && currentFile.exists() && currentFile.length() > 1024L;
            if (!saved && currentFile != null) currentFile.delete();
        } catch (RuntimeException e) {
            if (currentFile != null) currentFile.delete();
        }
        releaseRecorder();
        try { camera.lock(); startPreview(); } catch (Exception ignored) { }
        recordButton.setText("بدء التسجيل");
        recordButton.setBackground(rounded(RED, dp(16)));
        switchButton.setEnabled(Camera.getNumberOfCameras() > 1);
        audioButton.setEnabled(true);
        timerText.setBackground(rounded(GREEN, dp(16)));
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        statusText.setText(saved ? "تم الحفظ • أعلى سرعة " + Math.round(maxSpeed) + " كم/س" : "فشل حفظ المقطع • حاول تسجيل مقطع أطول");
        storageText.setText(storageSummary());
        if (saved) {
            MediaScannerConnection.scanFile(this, new String[]{currentFile.getAbsolutePath()}, new String[]{"video/mp4"}, null);
            if (userRequested) Toast.makeText(this, "تم حفظ الفيديو في مجلد DashCamTrip", Toast.LENGTH_LONG).show();
        } else if (userRequested) {
            Toast.makeText(this, "لم يتم حفظ الفيديو", Toast.LENGTH_LONG).show();
        }
    }

    private File recordingsDirectory() {
        File movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        if (!Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
            File appMovies = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (appMovies != null) movies = appMovies;
        }
        return new File(movies, "DashCamTrip");
    }

    private String storageSummary() {
        File dir = recordingsDirectory();
        File[] files = dir.listFiles();
        int count = files == null ? 0 : files.length;
        long freeMb = availableBytes(dir) / (1024L * 1024L);
        return count + " مقطع • " + freeMb + " MB متاح";
    }

    private long availableBytes(File directory) {
        File probe = directory;
        while (probe != null && !probe.exists()) probe = probe.getParentFile();
        return probe == null ? 0 : probe.getUsableSpace();
    }

    private void startLocation() {
        if (locationManager == null) locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try { locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, this); } catch (Exception ignored) { }
    }

    @Override public void onLocationChanged(Location location) {
        boolean reliable = location.hasSpeed() && (!location.hasAccuracy() || location.getAccuracy() <= 50f);
        speedKmh = reliable ? Math.max(0, Math.min(250f, location.getSpeed() * 3.6f)) : 0;
        if (recording && speedKmh > maxSpeed) maxSpeed = speedKmh;
    }
    @Override public void onProviderDisabled(String provider) { statusText.setText("فعّل GPS لعرض السرعة"); }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }

    private void releaseRecorder() {
        if (recorder != null) {
            try { recorder.reset(); recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    private void releaseCamera() {
        releaseRecorder();
        if (camera != null) {
            try { camera.stopPreview(); camera.release(); } catch (Exception ignored) { }
            camera = null;
        }
    }

    @Override protected void onPause() {
        if (recording) stopRecording(false);
        releaseCamera();
        if (locationManager != null) {
            try { locationManager.removeUpdates(this); } catch (Exception ignored) { }
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (surfaceReady) openCamera(cameraId);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseCamera();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (recording) {
            new AlertDialog.Builder(this).setTitle("إيقاف التسجيل؟").setMessage("سيتم حفظ المقطع الحالي قبل الخروج.")
                    .setNegativeButton("متابعة التسجيل", null)
                    .setPositiveButton("حفظ وخروج", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) { stopRecording(true); finish(); }
                    }).show();
        } else super.onBackPressed();
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        return view;
    }

    private TextView badge(String value, int color) {
        TextView view = text(value, 12, Color.WHITE, Typeface.BOLD);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11), dp(7), dp(11), dp(7));
        view.setBackground(rounded(color, dp(16)));
        return view;
    }

    private Button actionButton(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(6), 0, dp(6), 0);
        button.setMinHeight(dp(52));
        button.setBackground(rounded(color, dp(16)));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String formatDuration(long milliseconds) {
        long total = milliseconds / 1000;
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60);
    }

    /** Keeps the camera preview correctly proportioned and center-cropped instead of stretched. */
    private static final class PreviewFrame extends FrameLayout {
        private SurfaceView preview;
        private float previewAspect;
        PreviewFrame(Activity context) { super(context); setClipChildren(true); }
        void setPreview(SurfaceView value) { preview = value; }
        void setPreviewAspect(float value) { previewAspect = value; requestLayout(); }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            if (preview == null || previewAspect <= 0f) return;
            int width = right - left, height = bottom - top;
            int childWidth = width, childHeight = Math.round(width / previewAspect);
            if (childHeight < height) { childHeight = height; childWidth = Math.round(height * previewAspect); }
            int childLeft = (width - childWidth) / 2, childTop = (height - childHeight) / 2;
            preview.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);
        }
    }
}
