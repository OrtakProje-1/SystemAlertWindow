package in.jvapps.system_alert_window.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

public class NotificationHelper {
    private static final String CHANNEL_ID = "bubble_notification_channel";
    private static final String CHANNEL_NAME = "Incoming notification";
    private static final String CHANNEL_DESCRIPTION = "Incoming notification description";
    private static final int BUBBLE_NOTIFICATION_ID = 1237;
    private static final String BUBBLE_SHORTCUT_ID = "bubble_shortcut";
    private static NotificationManager notificationManager;
    private static final String TAG = "NotificationHelper";
    private Context mContext;

    private static NotificationHelper mInstance;



    private NotificationHelper(Context context) {
        this.mContext = context;
        if (isMinAndroidQ())
            initNotificationManager();
    }

    public static NotificationHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotificationHelper(context);
        }
        return mInstance;
    }

    private boolean isMinAndroidQ() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }

    private boolean isMinAndroidR() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initNotificationManager() {
        if (notificationManager == null) {
            if (mContext == null) {
                Log.e(TAG, "Context is null. Can't show the System Alert Window");
                return;
            }
            notificationManager = mContext.getSystemService(NotificationManager.class);
            setUpNotificationChannels();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void setUpNotificationChannels() {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setDescription(CHANNEL_DESCRIPTION);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Notification.BubbleMetadata createBubbleMetadata(Icon icon, PendingIntent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return new Notification.BubbleMetadata.Builder(intent, icon)
                    .setDesiredHeight(250)
                    .setAutoExpandBubble(true)
                    .setSuppressNotification(true)
                    .build();
        } else {
            return new Notification.BubbleMetadata.Builder()
                    .setDesiredHeight(250)
                    .setIcon(icon)
                    .setIntent(intent)
                    .setAutoExpandBubble(true)
                    .setSuppressNotification(true)
                    .build();
        }
    }


    public void dismissNotification(){
        notificationManager.cancel(BUBBLE_NOTIFICATION_ID);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean areBubblesAllowed(){
        if(isMinAndroidR()) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(CHANNEL_ID, BUBBLE_SHORTCUT_ID);
            assert notificationChannel != null;
            return notificationManager.areBubblesAllowed() ||notificationChannel.canBubble();
        }else{
            int devOptions = Settings.Secure.getInt(mContext.getContentResolver(), Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0);
            if (devOptions == 1) {
                Log.d(TAG, "Android bubbles are enabled");
                return true;
            } else {
                Log.e(TAG, "System Alert Window will not work without enabling the android bubbles");
                Toast.makeText(mContext, "Enable android bubbles in the developer options, for System Alert Window to work", Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }

}
