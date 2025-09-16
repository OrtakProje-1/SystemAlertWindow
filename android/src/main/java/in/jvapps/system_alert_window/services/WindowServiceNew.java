package in.jvapps.system_alert_window.services;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
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
    private View gradientView;
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
    private boolean isInDismissArea = false;
    private boolean isSmallLayout = true;
    private int currentOrientation;
    private int screenHeight;
    private int screenWidth;
    private int dpDismissArea = 75;

    @SuppressLint("UnspecifiedImmutableFlag")
    @Override
    public void onCreate() {
        //createNotificationChannel();
        //startInitialForeground();
        initDrawables();
        initOrientation();
        getScreenSize();

    }

    private void getScreenSize() {
        try {
            setWindowManager();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            Display display = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowMetrics maxMetrics = windowManager.getMaximumWindowMetrics();
                Rect bounds = maxMetrics.getBounds();
                displayMetrics.widthPixels = bounds.width();
                displayMetrics.heightPixels = bounds.height();
                screenWidth = displayMetrics.widthPixels;
                screenHeight = displayMetrics.heightPixels;
            } else {
                display = windowManager.getDefaultDisplay();
            }
            if (display != null) {
                display.getRealMetrics(displayMetrics);
                screenHeight = displayMetrics.heightPixels;
                screenWidth = displayMetrics.widthPixels;
            }
        } catch (Exception e) {
            LogUtils.getInstance().e(TAG, "---Error getting screen size: " + e.getMessage());
        }
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
//            String titleFromIntent = intent.getStringExtra(INTENT_EXTRA_NOTIFICATION_TITLE);
//            if (titleFromIntent != null) {
//                updateNotificationTitle(titleFromIntent);
//            }
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
        isSmallLayout = windowHeight == windowWidth;
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
        // overlay küçültülmüş ise snapToEdge ile sağa sola yaslicaz
        if (isSmallLayout) {
            snapToEdge(previousParams);
        }
        windowManager.updateViewLayout(flutterView, previousParams);
    }

    private void closeWindow(boolean isStopService) {
        try {
            if (windowManager != null && flutterView != null) {
                windowManager.removeView(flutterView);
                windowManager = null;
                flutterView.detachFromFlutterEngine();
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
            int dimension = Commons.getPixelsFromDp(this, dpDismissArea);
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
                        int oldParamsX = params.x;
                        int oldParamsY = params.y;
                        boolean inDismissArea = isPointInArea(
                                initialX + (int) dx,
                                initialY + (int) dy,
                                screenWidth / 2,
                                (screenHeight / 2) - (int) (1.5 * dimension)
                        );
                        checkDismissArea(inDismissArea);
                        if (!isInDismissArea && inDismissArea) {
                            moveTheOverlayByDismissArea();
                        }
                        if (isInDismissArea && !inDismissArea) {
                            animateToPosition(oldParamsX, (initialX + (int) dx), oldParamsY, (initialY + (int) dy), 50, params);
                        }
                        if (!inDismissArea) {
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(flutterView, params);
                        }
                        isInDismissArea = inDismissArea;
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
        WindowManager.LayoutParams dismissParams = (WindowManager.LayoutParams) dismissAreaView.getLayoutParams();
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) flutterView.getLayoutParams();
        int flutterWidth = params.width > 0 ? params.width : flutterView.getWidth();
        int dismissWidth = dismissParams.width > 0 ? dismissParams.width : dismissAreaView.getWidth();
        animateToPosition(params.x, dismissParams.x + ((dismissWidth - flutterWidth) / 2) + 1, params.y, dismissParams.y - 2, 50, params);
    }

    private boolean isPointInArea(int x1, int y1, int x2, int y2) {
        int radius = currentOrientation == Configuration.ORIENTATION_LANDSCAPE ? 250 : 200;
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
        dismissNormalDrawable.setColor(Color.parseColor("#000000"));
        dismissActiveDrawable = new GradientDrawable();
        dismissActiveDrawable.setShape(GradientDrawable.OVAL);
        dismissActiveDrawable.setColor(Color.parseColor("#FF4444"));
    }

    private void snapToEdge(@NonNull WindowManager.LayoutParams params) {
        if (screenWidth <= 0) {
            getScreenSize();
        }
        int overlayWidth = 200;
        if (flutterView != null) {
            overlayWidth = params.width; //  flutterView.getWidth()
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
            targetX = 8;
        } else {
            targetX = screenWidth - overlayWidth - 8;
        }
        if (currentX == targetX) return;
        animateToPosition(currentX, targetX, null, null, 200, params);
    }

    private void animateToPosition(int startX, int endX, @Nullable Integer startY, @Nullable Integer endY, Integer duration, WindowManager.LayoutParams params) {
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            params.x = (int) (startX + (endX - startX) * progress);
            if (startY != null && endY != null) {
                params.y = (int) (startY + (endY - startY) * progress);
            }
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
            return;
        }
        if (inDismissArea) {
            dismissAreaView.setBackground(dismissActiveDrawable);
        } else {
            dismissAreaView.setBackground(dismissNormalDrawable);
        }
    }

    private void createDismissArea() {
        try {
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
            int dimension = Commons.getPixelsFromDp(this, dpDismissArea);

            // View
            dismissAreaView = View.inflate(this, R.layout.dismis_area, null);
            GradientDrawable ovalBackground = new GradientDrawable();
            ovalBackground.setShape(GradientDrawable.OVAL);
            ovalBackground.setSize(dimension, dimension);
            dismissAreaView.setBackground(ovalBackground);
            WindowManager.LayoutParams dismissParams = new WindowManager.LayoutParams(
                    dimension,
                    dimension,
                    myParamType,
                    myParamFlags | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            dismissParams.gravity = Gravity.START | Gravity.CENTER;
            dismissParams.y = (screenHeight / 2) + dimension;
            dismissParams.x = (screenWidth / 2) - dimension / 2;
            dismissAreaView.setVisibility(View.GONE);
            dismissParam = dismissParams;
            createGradientView(myParamType);
            if (windowManager != null) {
                windowManager.addView(dismissAreaView, dismissParams);
            } 
        } catch (SecurityException e) {
            LogUtils.getInstance().e(TAG, "createDismissArea: Permission denied - " + e.getMessage());
        } catch (Exception e) {
            LogUtils.getInstance().e(TAG, "createDismissArea: Error creating dismiss area - " + e.getMessage());
        }
    }

    private void createGradientView(int myParamType) {
        gradientView = View.inflate(this, R.layout.gradient_view, null);
        WindowManager.LayoutParams gradientParams = new WindowManager.LayoutParams(
                screenWidth,
                screenHeight / 2,
                myParamType,
                262184 | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        gradientParams.gravity = Gravity.START | Gravity.CENTER;
        gradientParams.x = 0;
        gradientParams.y = screenHeight / 2;
        gradientView.setVisibility(View.GONE);
        if (windowManager != null) {
            windowManager.addView(gradientView, gradientParams);
        }
    }

    private void showDismissArea(boolean show) {
        if (!isSmallLayout) return;
        if (dismissAreaView != null) {
            showDismissAreaAnimation(show);
            isDismissAreaVisible = show;
        }
    }

    private void showDismissAreaAnimation(boolean show) {
        if (!isSmallLayout) return;
        int dimension = Commons.getPixelsFromDp(this, dpDismissArea);
        int startY = (screenHeight / 2) + dimension;
        int endY = (screenHeight / 2) - (int) (1.5 * dimension);

        int dismissStartByShow = show ? startY : endY;
        int dismissEndByShow = show ? endY : startY;

        if (show) {
            dismissAreaView.setVisibility(View.VISIBLE);
            gradientView.setVisibility(View.VISIBLE);
        }

        WindowManager.LayoutParams gradientParam = (WindowManager.LayoutParams) gradientView.getLayoutParams();
        int startGradientY = gradientParam.y;
        int endGradientY = show ? (int) (screenHeight / 3.5) : screenHeight;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(250);
        /*
        LinearInterpolator() → sabit hız (şu anki gibi düz).
        AccelerateInterpolator() → yavaş başlar, hızlanarak biter.
        DecelerateInterpolator() → hızlı başlar, yavaşlayarak biter.
        AccelerateDecelerateInterpolator() → yavaş başlar, hızlanır, sonra yine yavaşlar (daha doğal).
        OvershootInterpolator() → hedefine varır, biraz ileri taşar sonra geri gelir (yay efekti).
        AnticipateInterpolator() → başlamadan önce geri çekilir, sonra ileri gider.
        BounceInterpolator() → hedefe çarpar gibi seker. 🎾
         */
        animator.setInterpolator(new DecelerateInterpolator());

        animator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    dismissParam.y = (int) (dismissStartByShow + (dismissEndByShow - dismissStartByShow) * progress);
                    dismissParam.alpha = show ? progress : 1 - progress;
                    gradientParam.alpha = show ? progress : 1 - progress;
                    gradientParam.y = (int) (startGradientY + (endGradientY - startGradientY) * progress);
                    try {
                        windowManager.updateViewLayout(dismissAreaView, dismissParam);
                        windowManager.updateViewLayout(gradientView, gradientParam);
                    } catch (Exception e) {
                        animator.cancel();
                    }
                }
        );
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!show) {
                    dismissAreaView.setVisibility(View.GONE);
                    gradientView.setVisibility(View.GONE);
                }
            }
        });
        animator.start();

    }

    private boolean isInDismissArea(int x, int y) {
        if (!isDismissAreaVisible || dismissAreaView == null) {
            return false;
        }
        try {
            int[] dismissAreaLocation = new int[2];
            dismissAreaView.getLocationOnScreen(dismissAreaLocation);
            int dismissAreaX = dismissAreaLocation[0];
            int dismissAreaY = dismissAreaLocation[1];
            int dismissAreaWidth = dismissAreaView.getWidth();
            int dismissAreaHeight = dismissAreaView.getHeight();
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
            return distance <= snapDistance;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateOverlayPosition() {
        if (dismissParam != null) {
            int dimension = Commons.getPixelsFromDp(this, 50);
            dismissParam.y = (screenHeight / 2) + dimension;
            dismissParam.x = (screenWidth / 2) - dimension / 2;
            windowManager.updateViewLayout(dismissAreaView, dismissParam);
        }
    }

    private void closeOverlay() {
        try {
            closeWindow(true);
            if (dismissAreaView != null) {
                windowManager.removeView(dismissAreaView);
                dismissAreaView = null;
            }
            if (gradientView != null) {
                windowManager.removeView(gradientView);
                gradientView = null;
            }
            stopSelf();
        } catch (Exception e) {
            LogUtils.getInstance().i(TAG, "Error closing overlay:" + e.getMessage());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        currentOrientation = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        int oldScreenHeight = screenHeight;
        screenHeight = screenWidth;
        screenWidth = oldScreenHeight;
        updateOverlayPosition();
        configurationChangedMoveToFlutterView();
        updateViewSize();
    }

    private void initOrientation() {
        currentOrientation = getResources().getConfiguration().orientation;
    }

    private void configurationChangedMoveToFlutterView() {
        if (flutterView != null && flutterView.getLayoutParams() != null) {
        WindowManager.LayoutParams flutterParams = (WindowManager.LayoutParams) flutterView.getLayoutParams();
        snapToEdge(flutterParams);
    }
    }

    private void updateViewSize() {
        if (gradientView != null) {
            WindowManager.LayoutParams gradientParam = (WindowManager.LayoutParams) gradientView.getLayoutParams();
            gradientParam.height = screenHeight / 2;
            gradientParam.width = screenWidth;
            windowManager.updateViewLayout(gradientView, gradientParam);
        }
    }
}