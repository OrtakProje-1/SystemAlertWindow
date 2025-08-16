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
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowMetrics;
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

import android.graphics.drawable.GradientDrawable;

public class WindowServiceNew extends Service implements View.OnTouchListener {

    private static final String TAG = WindowServiceNew.class.getSimpleName();
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    public static final String INTENT_EXTRA_IS_UPDATE_WINDOW = "IsUpdateWindow";
    public static final String INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow";
    public static final String INTENT_EXTRA_NOTIFICATION_TITLE = "NotificationTitle";


    private WindowManager windowManager;

    private String windowGravity;
    private int windowWidth;
    private int windowHeight;
    private View dismissAreaView;
    private boolean isDismissAreaVisible = false;
    private String lastNotificationTitle = "Overlay window service is running";

    private FlutterView flutterView;
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
    private float offsetY, offsetX;
    private int initialX, initialY;
    private boolean moving;

    private GradientDrawable dismissNormalDrawable;
    private GradientDrawable dismissActiveDrawable;

    WindowManager.LayoutParams dismissParam;



    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void onCreate() {
        createNotificationChannel();
        startInitialForeground();
        initDrawables();
    }

    private void startInitialForeground() {
        Intent notificationIntent = new Intent(this, SystemAlertWindowPlugin.class);
        PendingIntent pendingIntent;
        pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_MUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(lastNotificationTitle)
                .setSmallIcon(R.drawable.ic_desktop_windows_black_24dp)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotificationTitle(String newTitle) {
        if (newTitle != null && !newTitle.equals(lastNotificationTitle)) {
            lastNotificationTitle = newTitle;

            Intent notificationIntent = new Intent(this, SystemAlertWindowPlugin.class);
            PendingIntent pendingIntent;
            pendingIntent = PendingIntent.getActivity(this,
                    0, notificationIntent, PendingIntent.FLAG_MUTABLE);

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle(lastNotificationTitle)
                    .setSmallIcon(R.drawable.ic_desktop_windows_black_24dp)
                    .setContentIntent(pendingIntent)
                    .build();

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, notification);
            }
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (null != intent && intent.getExtras() != null) {
            String titleFromIntent = intent.getStringExtra(INTENT_EXTRA_NOTIFICATION_TITLE);
            if (titleFromIntent != null) {
                updateNotificationTitle(titleFromIntent);
            }

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
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Notification channel for overlay window service");
            serviceChannel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                LogUtils.getInstance().d(TAG, "Notification channel created successfully");
            }
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
                    float X = event.getRawX();
                    float Y = event.getRawY();
                    float dx = X - offsetX;
                    float dy = Y - offsetY;

                    if (!moving && (Math.abs(dy) > 10 || Math.abs(dx) > 10)) {
                        moving = true;
                        showDismissArea(true);
                    }
                    if (moving) {
                        if (Math.abs(dx) < 2 && Math.abs(dy) < 2) return false;

                        params.x = initialX + (int) dx;
                        params.y = initialY + (int) dy;

                        windowManager.updateViewLayout(flutterView, params);


                        boolean inDismissArea = isPointInArea(
                                params.x,
                                params.y,
                                dismissParam.x,
                                dismissParam.y,
                                120
                        );
                        LogUtils.getInstance().i(TAG, "---onTouch: params.x=" + params.x + ", params.y=" + params.y * 2 + "\n");
                        LogUtils.getInstance().i(TAG, "---onTouch: dismissParam.x=" + dismissParam.x + ", dismissParam.y=" + dismissParam.y + "\n");
                        LogUtils.getInstance().i(TAG, "---onTouch: inDismissArea=" + inDismissArea + "\n");
                        checkDismissArea(inDismissArea);
                        if(inDismissArea){
                            moveTheOverlayByDismissArea();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (moving) {
                        int[] overlayLocation = new int[2];
                        flutterView.getLocationOnScreen(overlayLocation);
                        int overlayX = overlayLocation[0];
                        int overlayY = overlayLocation[1];
                        boolean inDismissArea = isInDismissArea(overlayX, overlayY);
                        if (inDismissArea) {
                            closeOverlay();
                        } else {
                            snapToEdge(params);
                        }
                        showDismissArea(false);
                    }
                    moving = false;
                    break;
            }
            return false;
        }
        return false;
    }

    private void moveTheOverlayByDismissArea() {

        int width = flutterView.getWidth();
        int height = flutterView.getHeight();

        LogUtils.getInstance().i(TAG,"---moveTheOverlayByDismissArea: width=" + width + ", height=" + height + ", dismissAreaView-Width" + dismissAreaView.getWidth() + "dismissAreaView-Height" + dismissAreaView.getHeight());
        //width=61, height=61, dismissAreaView-Width80dismissAreaView-Height80
        WindowManager.LayoutParams dismissParams = (WindowManager.LayoutParams) dismissAreaView.getLayoutParams();
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
        params.gravity = dismissParams.gravity;
        params.x = dismissParams.x + ((80 - width)/2);
        params.y = dismissParams.y + ((80 - height)/2);
        windowManager.updateViewLayout(flutterView, params);
    }

    private boolean isPointInArea(int x1, int y1, int x2, int y2, int radius) {
        return x1 >= x2 - radius && x1 <= x2 + radius &&
                y1 >= y2 - radius && y1 <= y2 + radius;
    }

    @Override
    public void onDestroy() {
        try {
            closeWindow(false);
            NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            assert notificationManager != null;
            notificationManager.cancel(NOTIFICATION_ID);
        } catch (Exception e) {
            LogUtils.getInstance().i(TAG, "on Destroy " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void initDrawables() {
        dismissNormalDrawable = new GradientDrawable();
        dismissNormalDrawable.setShape(GradientDrawable.OVAL);
        dismissNormalDrawable.setColor(Color.parseColor("#80000000"));
        dismissNormalDrawable.setStroke(3, Color.parseColor("#FFFFFF"));
        dismissNormalDrawable.setSize(120, 120);

        dismissActiveDrawable = new GradientDrawable();
        dismissActiveDrawable.setShape(GradientDrawable.OVAL);
        dismissActiveDrawable.setColor(Color.parseColor("#FF4444"));
        dismissActiveDrawable.setStroke(3, Color.parseColor("#FFFFFF"));
        dismissActiveDrawable.setSize(120, 120);
    }

    private void snapToEdge(@NonNull WindowManager.LayoutParams params) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        boolean metricsObtained = false;

        if (windowManager != null) {
            try {
                Display display = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowMetrics maxMetrics = windowManager.getMaximumWindowMetrics();
                    Rect bounds = maxMetrics.getBounds();
                    displayMetrics.widthPixels = bounds.width();
                    displayMetrics.heightPixels = bounds.height();
                    metricsObtained = true;
                } else {
                    display = windowManager.getDefaultDisplay();
                }
                if (display != null) {
                    display.getRealMetrics(displayMetrics);
                    metricsObtained = true;
                }
            } catch (Exception e) {
                LogUtils.getInstance().e(TAG, "snapToEdge: Error getting display from WindowManager: " + e.getMessage());
            }
        }

        if (!metricsObtained) {
            try {
                Resources resources = getResources();
                if (resources != null) {
                    DisplayMetrics resMetrics = resources.getDisplayMetrics();
                    if (resMetrics != null && resMetrics.widthPixels > 0) {
                        displayMetrics.widthPixels = resMetrics.widthPixels;
                        displayMetrics.heightPixels = resMetrics.heightPixels;
                        metricsObtained = true;
                    }
                }
            } catch (Exception e) {
                LogUtils.getInstance().e(TAG, "snapToEdge: Error getting display from Resources: " + e.getMessage());
            }
        }

        if (!metricsObtained) {
            LogUtils.getInstance().e(TAG, "snapToEdge: Could not obtain display metrics, using default values");
            displayMetrics.widthPixels = 1080;
            displayMetrics.heightPixels = 1920;
        }

        int screenWidth = displayMetrics.widthPixels;
        if (screenWidth <= 0) {
            LogUtils.getInstance().e(TAG, "snapToEdge: Invalid screen width: " + screenWidth);
            return;
        }

        int overlayWidth = 200;
        if (flutterView != null) {
            overlayWidth = flutterView.getWidth();
            if (overlayWidth <= 0) {
                overlayWidth = 200;
            }
        }
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
        animateToPosition(currentX, targetX, params);
    }

    private void animateToPosition(int startX, int endX, WindowManager.LayoutParams params) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            params.x = (int) (startX + (endX - startX) * progress);
            try {
                windowManager.updateViewLayout(flutterView, params);
            } catch (Exception e) {
                animator.cancel();
            }
        });
        animator.start();
    }

    private void checkDismissArea(boolean inDismissArea) {
        if (dismissAreaView == null) {
            LogUtils.getInstance().d(TAG, "handleDismissAreaSnapping: dismissAreaView is null");
            return;
        }
        if (inDismissArea) {
            dismissAreaView.setBackground(dismissActiveDrawable);
            LogUtils.getInstance().i(TAG, "---handleDismissAreaSnapping: Set to RED and LARGE");
        } else {
            dismissAreaView.setBackground(dismissNormalDrawable);
            LogUtils.getInstance().i(TAG, "---handleDismissAreaSnapping: Set to NORMAL");
        }
    }

    private void createDismissArea() {
        try {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(displayMetrics);
            int screenHeight = displayMetrics.heightPixels;
            int screenWidth = displayMetrics.widthPixels;
            LogUtils.getInstance().i(TAG, "---createDismissArea: Starting to create dismiss area");

            final int myParamFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

            final int myParamType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                myParamType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                myParamType = WindowManager.LayoutParams.TYPE_PHONE;
            } else {
                myParamType = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            }

            LogUtils.getInstance().i(TAG, "---createDismissArea: Using window type: " + myParamType);

            dismissAreaView = View.inflate(this, R.layout.dismis_area, null);

            GradientDrawable ovalBackground = new GradientDrawable();
            ovalBackground.setShape(GradientDrawable.OVAL);
            ovalBackground.setSize(120, 120);
            dismissAreaView.setBackground(ovalBackground);


            WindowManager.LayoutParams dismissParams = new WindowManager.LayoutParams(
                    80,
                    80,
                    myParamType,
                    myParamFlags,
                    PixelFormat.TRANSLUCENT);
            dismissParams.gravity = Gravity.START | Gravity.CENTER;
            dismissParams.y = (screenHeight / 2) - 160;
            dismissParams.x = (screenWidth /2) - 60;
            dismissAreaView.setVisibility(View.GONE);
            dismissParam = dismissParams;

            if (windowManager != null) {
                windowManager.addView(dismissAreaView, dismissParams);
                LogUtils.getInstance().d(TAG, "createDismissArea: Successfully added dismiss area to window manager");
            } else {
                LogUtils.getInstance().e(TAG, "createDismissArea: windowManager is null");
            }
        } catch (SecurityException e) {
            LogUtils.getInstance().e(TAG, "createDismissArea: Permission denied - " + e.getMessage());
        } catch (Exception e) {
            LogUtils.getInstance().e(TAG, "createDismissArea: Error creating dismiss area - " + e.getMessage());
        }
    }


    private void showDismissArea(boolean show) {
        if (dismissAreaView != null) {
            dismissAreaView.setVisibility(show ? View.VISIBLE : View.GONE);
            isDismissAreaVisible = show;
            LogUtils.getInstance().i(TAG, "---showDismissArea: " + (show ? "SHOWING" : "HIDING") + " dismiss area");
        } else {
            LogUtils.getInstance().i(TAG, "---showDismissArea: dismissAreaView is null");
        }
    }

    private boolean isInDismissArea(int x, int y) {
        if (!isDismissAreaVisible || dismissAreaView == null) {
            LogUtils.getInstance().i(TAG, "---[]--isInDismissArea: dismissArea not visible or null");
            return false;
        }
        try {
            int[] dismissAreaLocation = new int[2];
            dismissAreaView.getLocationOnScreen(dismissAreaLocation);
            int dismissAreaX = dismissAreaLocation[0];
            int dismissAreaY = dismissAreaLocation[1];
            int dismissAreaWidth = dismissAreaView.getWidth();
            int dismissAreaHeight = dismissAreaView.getHeight();
            if (dismissAreaWidth <= 0) dismissAreaWidth = 80;
            if (dismissAreaHeight <= 0) dismissAreaHeight = 80;
            int overlayWidth = flutterView.getWidth();
            int overlayHeight = flutterView.getHeight();
            if (overlayWidth <= 0) overlayWidth = 30;
            if (overlayHeight <= 0) overlayHeight = 30;
            int overlayCenterX = x + (overlayWidth / 2);
            int overlayCenterY = y + (overlayHeight / 2);
            int dismissCenterX = dismissAreaX + (dismissAreaWidth / 2);
            int dismissCenterY = dismissAreaY + (dismissAreaHeight / 2);
            double distance = Math.sqrt(
                    Math.pow(overlayCenterX - dismissCenterX, 2) +
                            Math.pow(overlayCenterY - dismissCenterY, 2)
            );
            int snapDistance = (dismissAreaWidth / 2) + (overlayWidth / 2) + 20;
            boolean inArea = distance <= snapDistance;
            LogUtils.getInstance().d(TAG, "---[]--isInDismissArea: distance=" + distance + ", snapDistance=" + snapDistance + ", inArea=" + inArea);
            return inArea;
        } catch (Exception e) {
            LogUtils.getInstance().e(TAG, "---[]--isInDismissArea error: " + e.getMessage());
            return false;
        }
    }


    private void closeOverlay() {
        try {
            closeWindow(true);
            if (dismissAreaView != null) {
                windowManager.removeView(dismissAreaView);
            }
            stopSelf();
        } catch (Exception e) {
            LogUtils.getInstance().i(TAG, "Error closing overlay:" + e.getMessage());
        }
    }

}
