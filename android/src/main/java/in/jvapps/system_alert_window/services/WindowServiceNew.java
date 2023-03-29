package in.jvapps.system_alert_window.services;

import static android.content.Intent.getIntent;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;

import in.jvapps.system_alert_window.R;
import in.jvapps.system_alert_window.SystemAlertWindowPlugin;
import in.jvapps.system_alert_window.utils.Commons;
import in.jvapps.system_alert_window.utils.NumberUtils;

import static in.jvapps.system_alert_window.utils.Constants.INTENT_EXTRA_PARAMS_MAP;
import static in.jvapps.system_alert_window.utils.Constants.KEY_HEIGHT;
import static in.jvapps.system_alert_window.utils.Constants.KEY_IMAGE_PATH;
import static in.jvapps.system_alert_window.utils.Constants.KEY_WIDTH;

public class WindowServiceNew extends Service implements View.OnTouchListener,View.OnClickListener {

    private static final String ACTION_STOP_SERVICE="actionStopService";
    private static final String TAG = WindowServiceNew.class.getSimpleName();
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private static int NOTIFICATION_ID = 1;
    public static final String INTENT_EXTRA_IS_UPDATE_WINDOW = "IsUpdateWindow";
    public static final String INTENT_EXTRA_IS_CLOSE_WINDOW = "IsCloseWindow";

    private WindowManager wm;

    private int windowWidth;
    private int windowHeight;
    private String windowTitle;
    private String windowBody;
    private RelativeLayout windowView;
    private String imagePath;

    private double doubleClickLastTime = 0L;

    private Context mContext;


    @Override
    public void onCreate() {
        createNotificationChannel();

        int requestID = (int) System.currentTimeMillis();

        Intent stopSelf = new Intent(this, WindowServiceNew.class);
        stopSelf.setAction(ACTION_STOP_SERVICE);
        stopSelf.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);



        //Intent notificationIntent = new Intent(this, SystemAlertWindowPlugin.class);
        PendingIntent pendingIntent = PendingIntent.getService(this,
                requestID, stopSelf, Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(windowTitle !=null ? windowTitle : "Cross gösteriliyor")
                .setContentText(windowBody != null ? windowBody : "Kapatmak için tıklayın")
                .setSmallIcon(R.drawable.cross)
                .setContentIntent(pendingIntent)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
            Log.d(TAG,"called to cancel service");
            closeWindow(true);
            stopSelf();
        }
        if (null != intent && intent.getExtras() != null) {
            @SuppressWarnings("unchecked")
            HashMap<String, Object> paramsMap = (HashMap<String, Object>) intent.getSerializableExtra(INTENT_EXTRA_PARAMS_MAP);
            mContext = this;
            windowTitle = intent.getStringExtra("title");
            windowBody = intent.getStringExtra("body");
            boolean isCloseWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, false);
            if (!isCloseWindow) {
                assert paramsMap != null;
                boolean isUpdateWindow = intent.getBooleanExtra(INTENT_EXTRA_IS_UPDATE_WINDOW, false);
                if (wm != null && isUpdateWindow && windowView != null) {
                    updateWindow(paramsMap);
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
                    "Cross servis bildirimi",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            assert manager != null;
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void setWindowManager() {
        if (wm == null) {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }
    }

    private void setWindowLayoutFromMap(HashMap<String, Object> paramsMap) {
        windowWidth = NumberUtils.getInt(paramsMap.get(KEY_WIDTH));
        windowHeight = NumberUtils.getInt(paramsMap.get(KEY_HEIGHT));
        imagePath = paramsMap.get(KEY_IMAGE_PATH).toString();

    }

    private WindowManager.LayoutParams getLayoutParams() {
        final WindowManager.LayoutParams params;
        params = new WindowManager.LayoutParams();
        params.width = (windowWidth == 0) ? android.view.WindowManager.LayoutParams.MATCH_PARENT : Commons.getPixelsFromDp(mContext, windowWidth);
        params.height = (windowHeight == 0) ? android.view.WindowManager.LayoutParams.WRAP_CONTENT : Commons.getPixelsFromDp(mContext, windowHeight);
        params.format = PixelFormat.TRANSLUCENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        } else {
            params.type = android.view.WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
            params.flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        params.gravity =  Gravity.CENTER; //Commons.getGravity(windowGravity, Gravity.TOP);

        return params;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setWindowView(WindowManager.LayoutParams params, boolean isCreate) {
        if (isCreate) {
            windowView = new RelativeLayout(mContext);
        }
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(windowWidth*4, windowHeight*4);
        lp.addRule(RelativeLayout.CENTER_IN_PARENT);
        ImageView imageView = new ImageView(getApplicationContext());
        imageView.setLayoutParams(lp);
        imageView.setImageBitmap(BitmapFactory.decodeFile(imagePath));


        // windowView.setOrientation(LinearLayout.VERTICAL);
        windowView.setBackgroundColor(Color.TRANSPARENT);
        windowView.setLayoutParams(params);
        windowView.removeAllViews();
        windowView.addView(imageView);

            windowView.setOnTouchListener(this);
            windowView.setOnClickListener(this);

    }

    private void createWindow(HashMap<String, Object> paramsMap) {
        closeWindow(false);
        setWindowManager();
        setWindowLayoutFromMap(paramsMap);
        WindowManager.LayoutParams params = getLayoutParams();
        setWindowView(params, true);
        try {
            wm.addView(windowView, params);
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
            retryCreateWindow(paramsMap);
        }
    }

    private void retryCreateWindow(HashMap<String, Object> paramsMap) {
        if(wm != null){
            wm.removeViewImmediate(windowView);
        }
        closeWindow(false);
        setWindowManager();
        //setWindowLayoutFromMap(paramsMap);
        WindowManager.LayoutParams params = getLayoutParams();
        setWindowView(params, true);
        try {
            wm.addView(windowView, params);
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }
    }

    private void updateWindow(HashMap<String, Object> paramsMap) {
        setWindowLayoutFromMap(paramsMap);
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) windowView.getLayoutParams();
        params.width = (windowWidth == 0) ? android.view.WindowManager.LayoutParams.MATCH_PARENT : Commons.getPixelsFromDp(mContext, windowWidth);
        params.height = (windowHeight == 0) ? android.view.WindowManager.LayoutParams.WRAP_CONTENT : Commons.getPixelsFromDp(mContext, windowHeight);
        setWindowView(params, false);
        wm.updateViewLayout(windowView, params);
    }

    private void closeWindow(boolean isEverythingDone) {
        Log.i(TAG, "Closing the overlay window");
        try {
            if (wm != null) {
                if (windowView != null) {
                    wm.removeView(windowView);
                    windowView = null;
                }
            }
            wm = null;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "view not found");
        }
        if (isEverythingDone) {
            stopSelf();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouch(View v, MotionEvent event) {
       /* if (null != wm) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                float x = event.getRawX();
                float y = event.getRawY();
                moving = false;
                int[] location = new int[2];
                windowView.getLocationOnScreen(location);
                originalXPos = location[0];
                originalYPos = location[1];
                offsetX = originalXPos - x;
                offsetY = originalYPos - y;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float x = event.getRawX();
                float y = event.getRawY();
                System.out.println("x: "+x);
                System.out.println("y: "+y);
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) windowView.getLayoutParams();
                int newX = (int) (offsetX + x);
                int newY = (int) (offsetY + y);
                if (Math.abs(newX - originalXPos) < 1 && Math.abs(newY - originalYPos) < 1 && !moving) {
                    return false;
                }
                params.x = newX;
                params.y = newY;
                wm.updateViewLayout(windowView, params);
                moving = true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                return moving;
            }
        }

        */
        return false;
    }

    @Override
    public void onClick(View v) {
        if(System.currentTimeMillis() - doubleClickLastTime < 300){
            doubleClickLastTime = 0;
            doAction();
        }else{
            doubleClickLastTime = System.currentTimeMillis();
        }
    }

    void doAction(){
        closeWindow(true);
    }

    @Override
    public void onDestroy() {
        //Toast.makeText(this, "service done", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Destroying the overlay window service");
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        assert notificationManager != null;
        notificationManager.cancel(NOTIFICATION_ID);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
