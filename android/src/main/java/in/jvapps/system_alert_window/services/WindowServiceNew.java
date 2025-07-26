package in.jvapps.system_alert_window.services;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.Objects;

import in.jvapps.system_alert_window.R;
import in.jvapps.system_alert_window.SystemAlertWindowPlugin;
import in.jvapps.system_alert_window.utils.Commons;
import in.jvapps.system_alert_window.utils.Constants;
import in.jvapps.system_alert_window.utils.ContextHolder;
import in.jvapps.system_alert_window.utils.LogUtils;
import in.jvapps.system_alert_window.utils.NumberUtils;
import io.flutter.embedding.android.FlutterTextureView;
import io.flutter.embedding.android.FlutterView;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;

public class WindowServiceNew extends Service implements View.OnTouchListener {

    private static final String TAG = WindowServiceNew.class.getSimpleName();
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String INTENT_EXTRA_IS_UPDATE_WINDOW = "IsUpdateWindow";
    public static final String INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow";


    private WindowManager windowManager;

    private String windowGravity;
    private int windowWidth;
    private int windowHeight;
    private View dismissAreaView;
    private boolean isDismissAreaVisible = false;


    private View flutterView;
    private final int paramFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            : WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED |
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    private int paramsFromDart;
    private final int paramType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    private float offsetY;
    private float offsetX;
    private int initialX, initialY;
    private boolean moving;

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void onCreate() {
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, SystemAlertWindowPlugin.class);
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pendingIntent = PendingIntent.getActivity(this,
                    0, notificationIntent, PendingIntent.FLAG_MUTABLE);
        } else {
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Overlay window service is running")
                .setSmallIcon(R.drawable.ic_desktop_windows_black_24dp)
                .setContentIntent(pendingIntent)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        if (null != intent && intent.getExtras() != null) {
            ContextHolder.setApplicationContext(this);
            @SuppressWarnings("unchecked")
            HashMap<String, Object> paramsMap = (HashMap<String, Object>) intent.getSerializableExtra(Constants.INTENT_EXTRA_PARAMS_MAP);
            boolean isCloseWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, false);
            if (!isCloseWindow) {
                assert paramsMap != null;
                boolean isUpdateWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_UPDATE_WINDOW, false);
                if (isUpdateWindow && windowManager != null && flutterView != null) {
                    if (flutterView.isAttachedToWindow()) {
                        updateWindow(paramsMap);
                    } else {
                        createWindow(paramsMap);
                    }
                } else {
                    createWindow(paramsMap);
                }
            } else {
                closeWindow(true);
            }
        }
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void setWindowManager() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
    }

    private void setWindowLayoutFromMap(HashMap<String, Object> paramsMap) {
        paramsFromDart = Commons.getLayoutParamFlags(paramsMap);
        windowGravity = (String) paramsMap.get(Constants.KEY_GRAVITY);
        windowWidth = NumberUtils.getInt(paramsMap.get(Constants.KEY_WIDTH));
        windowHeight = NumberUtils.getInt(paramsMap.get(Constants.KEY_HEIGHT));
    }

    private WindowManager.LayoutParams getLayoutParams() {
        final WindowManager.LayoutParams params;
        params = new WindowManager.LayoutParams();
        params.width = (windowWidth == 0) ? WindowManager.LayoutParams.MATCH_PARENT : Commons.getPixelsFromDp(this, windowWidth);
        params.height = (windowHeight == 0) ? WindowManager.LayoutParams.WRAP_CONTENT : Commons.getPixelsFromDp(this, windowHeight);
        params.format = PixelFormat.TRANSLUCENT;
        params.type = paramType;
        params.flags |= paramFlags;
        params.flags |= paramsFromDart;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Commons.isClickDisabled) {
            params.alpha = 0.8f;
        }
        params.gravity = Commons.getGravity(windowGravity, Gravity.TOP);
        return params;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createWindow(HashMap<String, Object> paramsMap) {
        try {
            closeWindow(false);
            setWindowManager();
            setWindowLayoutFromMap(paramsMap);
            WindowManager.LayoutParams params = getLayoutParams();
            FlutterEngine engine = FlutterEngineCache.getInstance().get(Constants.FLUTTER_CACHE_ENGINE);
            if (engine == null) {
                throw new IllegalStateException("FlutterEngine not available");
            }
            engine.getLifecycleChannel().appIsResumed();
            flutterView = new FlutterView(getApplicationContext(), new FlutterTextureView(getApplicationContext()));
            flutterView.attachToFlutterEngine(Objects.requireNonNull(FlutterEngineCache.getInstance().get(Constants.FLUTTER_CACHE_ENGINE)));
            flutterView.setFitsSystemWindows(true);
            flutterView.setFocusable(false);
            flutterView.setFocusableInTouchMode(false);
            flutterView.setBackgroundColor(Color.TRANSPARENT);
            flutterView.setOnTouchListener(this);
            createDismissArea();
            try {
                windowManager.addView(flutterView, params);
            } catch (Exception ex) {
                LogUtils.getInstance().e(TAG, ex.toString());
                retryCreateWindow(paramsMap);
            }
        } catch (Exception ex) {
            LogUtils.getInstance().e(TAG, "createWindow " + ex.getMessage());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void retryCreateWindow(HashMap<String, Object> paramsMap) {
        try {
            LogUtils.getInstance().d(TAG, "Retrying create window");
            closeWindow(false);
            setWindowManager();
            setWindowLayoutFromMap(paramsMap);
            WindowManager.LayoutParams params = getLayoutParams();
            FlutterEngine engine = FlutterEngineCache.getInstance().get(Constants.FLUTTER_CACHE_ENGINE);
            if (engine == null) {
                throw new IllegalStateException("FlutterEngine not available");
            }
            engine.getLifecycleChannel().appIsResumed();
            flutterView = new FlutterView(getApplicationContext(), new FlutterTextureView(getApplicationContext()));
            flutterView.attachToFlutterEngine(Objects.requireNonNull(FlutterEngineCache.getInstance().get(Constants.FLUTTER_CACHE_ENGINE)));
            flutterView.setFitsSystemWindows(true);
            flutterView.setFocusable(false);
            flutterView.setFocusableInTouchMode(false);
            flutterView.setBackgroundColor(Color.TRANSPARENT);
            flutterView.setOnTouchListener(this);
            windowManager.addView(flutterView, params);
        } catch (Exception ex) {
            LogUtils.getInstance().e(TAG, "retryCreateWindow " + ex.getMessage());
        }
    }

    private void updateWindow(HashMap<String, Object> paramsMap) {
        setWindowLayoutFromMap(paramsMap);
        WindowManager.LayoutParams newParams = getLayoutParams();
        WindowManager.LayoutParams previousParams = (WindowManager.LayoutParams) flutterView.getLayoutParams();
        previousParams.width = (windowWidth == 0) ? WindowManager.LayoutParams.MATCH_PARENT : Commons.getPixelsFromDp(this, windowWidth);
        previousParams.height = (windowHeight == 0) ? WindowManager.LayoutParams.WRAP_CONTENT : Commons.getPixelsFromDp(this, windowHeight);
        previousParams.flags = newParams.flags;
        previousParams.alpha = newParams.alpha;
        windowManager.updateViewLayout(flutterView, previousParams);
    }

    private void closeWindow(boolean isStopService) {
        LogUtils.getInstance().i(TAG, "Closing the overlay window");
        try {
            if (windowManager != null && flutterView != null) {
                    windowManager.removeView(flutterView);
                    windowManager = null;
                    flutterView.detachFromFlutterEngine();
                    LogUtils.getInstance().i(TAG, "Successfully closed overlay window");
            }
        } catch (IllegalArgumentException e) {
            LogUtils.getInstance().e(TAG, "view not found");
        }
        if (isStopService) {
            stopSelf();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (windowManager != null) {
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    moving = false;
                    offsetX = event.getRawX();
                    offsetY = event.getRawY();
                    initialX = params.x;
                    initialY = params.y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - offsetX;
                    float dy = event.getRawY() - offsetY;
                    if (!moving && (Math.abs(dy) > 10 || Math.abs(dx) > 10)) {
                        moving = true;
                        showDismissArea(true);
                    }
                    if (moving) {
                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;
                        windowManager.updateViewLayout(flutterView, params);
                        checkDismissArea(params.y, params.x);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (moving) {
                        // Kapatma alanında mı kontrol et
                        if (isInDismissArea(params.y, params.x)) {
                            // Overlay'i kapat
                            closeOverlay();
                        } else {
                            // Normal kenara yapışma
                            snapToEdge(params);
                        }
                        // Sürükleme bittiğinde kapatma alanını gizle
                        showDismissArea(false);
                    }
                    moving = false;
                    break;
            }
            return false;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        try {
            closeWindow(false);
            LogUtils.getInstance().d(TAG, "Destroying the overlay window service");
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.cancel(NOTIFICATION_ID);
            LogUtils.getInstance().i(TAG, "Successfully destroyed overlay window service");
        } catch (java.lang.Exception e) {
            LogUtils.getInstance().i(TAG, "on Destroy " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void snapToEdge(@NonNull WindowManager.LayoutParams params) {
        if (params == null) {
            LogUtils.getInstance().e(TAG, "snapToEdge: params is null");
            return;
        }

        DisplayMetrics displayMetrics = new DisplayMetrics();
        boolean metricsObtained = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Display display = getDisplay();
            if (display != null) {
                display.getRealMetrics(displayMetrics);
                metricsObtained = true;
            } else {
                Context context = getApplicationContext();
                if (context != null) {
                    display = context.getDisplay();
                    if (display != null) {
                        display.getRealMetrics(displayMetrics);
                        metricsObtained = true;
                    }
                }
            }
        } else {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                if (display != null) {
                    display.getMetrics(displayMetrics);
                    metricsObtained = true;
                }
            }
        }

        if (!metricsObtained) {
            LogUtils.getInstance().e(TAG, "snapToEdge: Could not obtain display metrics");
            return;
        }

        int screenWidth = displayMetrics.widthPixels;
        if (screenWidth <= 0) {
            LogUtils.getInstance().e(TAG, "snapToEdge: Invalid screen width: " + screenWidth);
            return;
        }

        int overlayWidth = 200; // Default değer
        if (flutterView != null) {
            overlayWidth = flutterView.getWidth();
            if (overlayWidth <= 0) {
                overlayWidth = 200; // Fallback değer
            }
        }

        // Overlay genişliğinin ekran genişliğini aşmamasını sağla
        if (overlayWidth > screenWidth) {
            overlayWidth = screenWidth;
        }

        int overlayCenter = overlayWidth / 2;
        int currentX = params.x;

        int screenCenter = screenWidth / 2;

        int targetX;
        if (currentX + overlayCenter < screenCenter) {
            targetX = 0;
        } else {
            targetX = screenWidth - overlayWidth;
        }

        // Negatif koordinat kontrolü
        if (targetX < 0) {
            targetX = 0;
        }

        // Animasyon
        animateToPosition(currentX, targetX, params);
    }

    private void animateToPosition(int startX, int endX, WindowManager.LayoutParams params) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                float progress = (float) animation.getAnimatedValue();
                params.x = (int) (startX + (endX - startX) * progress);
                try {
                    windowManager.updateViewLayout(flutterView, params);
                } catch (Exception e) {
                    animator.cancel();
                }
            }
        });
        animator.start();
    }
    private void checkDismissArea(int y, int x) {
        if (isInDismissArea(y, x)) {
            // Kapatma alanında - görsel geri bildirim
            dismissAreaView.setBackgroundColor(Color.parseColor("#FF6666")); // Daha parlak kırmızı
        } else {
            // Kapatma alanında değil
            dismissAreaView.setBackgroundColor(Color.parseColor("#FF4444")); // Normal kırmızı
        }
    }

    private void createDismissArea() {
        dismissAreaView = new View(this);
        dismissAreaView.setBackgroundColor(Color.parseColor("#FF4444")); // Kırmızı arka plan

        // Kapatma alanı layout parametreleri
        WindowManager.LayoutParams dismissParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, // Genişlik
                120, // Yükseklik (120px)
                paramType,
                paramFlags,
                PixelFormat.TRANSLUCENT);

        dismissParams.gravity = Gravity.BOTTOM | Gravity.START;
        dismissParams.y = 0; // Alt kenarda

        // Başlangıçta gizli
        dismissAreaView.setVisibility(View.GONE);

        // Kapatma alanını ekrana ekle
        windowManager.addView(dismissAreaView, dismissParams);
    }
    private void showDismissArea(boolean show) {
        if (dismissAreaView != null) {
            dismissAreaView.setVisibility(show ? View.VISIBLE : View.GONE);
            isDismissAreaVisible = show;
        }
    }
    private boolean isInDismissArea(int y, int x) {
        if (!isDismissAreaVisible)
            return false;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);
        int screenHeight = displayMetrics.heightPixels;

        // Ekranın alt 120px'lik alanı
        return y > screenHeight - 120;
    }
    private void closeOverlay() {
        try {
            // Flutter overlay'ini kapat
            if (flutterView != null) {
                windowManager.removeView(flutterView);
            }
            // Kapatma alanını kapat
            if (dismissAreaView != null) {
                windowManager.removeView(dismissAreaView);
            }
            // Servisi durdur
            stopSelf();
        } catch (Exception e) {
            LogUtils.getInstance().i(TAG, "Error closing overlay:" + e.getMessage());
        }
    }
}
