package in.jvapps.system_alert_window;

import static in.jvapps.system_alert_window.services.WindowServiceNew.INTENT_EXTRA_IS_CLOSE_WINDOW;
import static in.jvapps.system_alert_window.services.WindowServiceNew.INTENT_EXTRA_IS_UPDATE_WINDOW;
import static in.jvapps.system_alert_window.utils.Constants.CHANNEL;
import static in.jvapps.system_alert_window.utils.Constants.INTENT_EXTRA_PARAMS_MAP;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;

import in.jvapps.system_alert_window.services.WindowServiceNew;
import in.jvapps.system_alert_window.utils.Commons;
import in.jvapps.system_alert_window.utils.NotificationHelper;
import io.flutter.FlutterInjector;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.FlutterEngineCache;
import io.flutter.embedding.engine.dart.DartExecutor;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.JSONMethodCodec;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.view.FlutterCallbackInformation;

public class SystemAlertWindowPlugin extends Activity implements FlutterPlugin, ActivityAware, MethodCallHandler {

    private final String flutterEngineId = "system_alert_window_engine";
    private Context mContext;
    private Activity mActivity;

    private MethodChannel methodChannel;
    public int ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE = 1237;
    private final String TAG = "SystemAlertWindowPlugin";

    public SystemAlertWindowPlugin() {
    }

    private SystemAlertWindowPlugin(Context context, Activity activity, MethodChannel newMethodChannel) {
        this.mContext = context;
        mActivity = activity;
        methodChannel = newMethodChannel;
        methodChannel.setMethodCallHandler(this);
    }

    public synchronized FlutterEngine getFlutterEngine(Context context) {
        FlutterEngine flutterEngine = FlutterEngineCache.getInstance().get(flutterEngineId);
        if (flutterEngine == null) {
            // XXX: The constructor triggers onAttachedToEngine so this variable doesn't help us.
            // Maybe need a boolean flag to tell us we're currently loading the main flutter engine.
            flutterEngine = new FlutterEngine(context.getApplicationContext());
            flutterEngine.getDartExecutor().executeDartEntrypoint(DartExecutor.DartEntrypoint.createDefault());
            FlutterEngineCache.getInstance().put(flutterEngineId, flutterEngine);
        }
        return flutterEngine;
    }

    public void disposeFlutterEngine() {
        FlutterEngine flutterEngine = FlutterEngineCache.getInstance().get(flutterEngineId);
        if (flutterEngine != null) {
            flutterEngine.destroy();
            FlutterEngineCache.getInstance().remove(flutterEngineId);
        }
    }

    @SuppressWarnings("unused")
    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL, JSONMethodCodec.INSTANCE);
        channel.setMethodCallHandler(new SystemAlertWindowPlugin(registrar.context(), registrar.activity(), channel));
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.mContext = flutterPluginBinding.getApplicationContext();
        this.methodChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), CHANNEL, JSONMethodCodec.INSTANCE);
        this.methodChannel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        this.mContext = null;
        this.methodChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding activityPluginBinding) {
        this.mActivity = activityPluginBinding.getActivity();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        this.mActivity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding activityPluginBinding) {
        this.mActivity = activityPluginBinding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        this.mActivity = null;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        try {
            String prefMode;
            JSONArray arguments;
            switch (call.method) {
                case "getPlatformVersionInt":
                    result.success(Build.VERSION.SDK_INT);
                    break;
                case "getPlatformVersion":
                    result.success("Android " + Build.VERSION.RELEASE);
                    break;
                case "requestPermissions":
                    assert (call.arguments != null);
                    arguments = (JSONArray) call.arguments;
                    prefMode = (String) arguments.get(0);
                    if (prefMode == null) {
                        prefMode = "default";
                    }
                    if (askPermission(!isBubbleMode(prefMode))) {
                        result.success(true);
                    } else {
                        result.success(false);
                    }
                    break;
                case "checkPermissions":
                    arguments = (JSONArray) call.arguments;
                    prefMode = (String) arguments.get(0);
                    if (prefMode == null) {
                        prefMode = "default";
                    }
                    if (checkPermission(!isBubbleMode(prefMode))) {
                        result.success(true);
                    } else {
                        result.success(false);
                    }
                    break;
                case "showSystemWindow":
                    assert (call.arguments != null);
                    arguments = (JSONArray) call.arguments;
                    String title = (String) arguments.get(0);
                    String body = (String) arguments.get(1);
                    JSONObject paramObj = (JSONObject) arguments.get(2);
                    HashMap params = new Gson().fromJson(paramObj.toString(), HashMap.class);
                    prefMode = (String) arguments.get(3);
                    if (prefMode == null) {
                        prefMode = "default";
                    }
                        if (checkPermission(true)) {
                            Log.d(TAG, "Going to show System Alert Window");
                            final Intent i = new Intent(mContext, WindowServiceNew.class);
                            i.putExtra(INTENT_EXTRA_PARAMS_MAP, params);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            i.putExtra(INTENT_EXTRA_IS_UPDATE_WINDOW, false);
                            i.putExtra("title",title);
                            i.putExtra("body",body);
                            //WindowService.enqueueWork(mContext, i);
                            mContext.startService(i);
                        } else {
                            Toast.makeText(mContext, "Please give draw over other apps permission", Toast.LENGTH_LONG).show();
                            result.success(false);
                        }
                    result.success(true);
                    break;
                case "updateSystemWindow":
                    assert (call.arguments != null);
                    JSONArray updateArguments = (JSONArray) call.arguments;
                    HashMap<String, Object> updateParams = new Gson().fromJson(((JSONObject) updateArguments.get(2)).toString(), HashMap.class);


                        if (checkPermission(true)) {
                            Log.d(TAG, "Going to update System Alert Window");
                            final Intent i = new Intent(mContext, WindowServiceNew.class);
                            i.putExtra(INTENT_EXTRA_PARAMS_MAP, updateParams);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            i.putExtra(INTENT_EXTRA_IS_UPDATE_WINDOW, true);
                            //WindowService.enqueueWork(mContext, i);
                            mContext.startService(i);
                        } else {
                            Toast.makeText(mContext, "Please give draw over other apps permission", Toast.LENGTH_LONG).show();
                            result.success(false);
                        }
                    result.success(true);
                    break;
                case "closeSystemWindow":
                    arguments = (JSONArray) call.arguments;
                    prefMode = (String) arguments.get(0);
                    if (prefMode == null) {
                        prefMode = "default";
                    }
                    if (checkPermission(!isBubbleMode(prefMode))) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isBubbleMode(prefMode)) {
                            NotificationHelper.getInstance(mContext).dismissNotification();
                        } else {
                            final Intent i = new Intent(mContext, WindowServiceNew.class);
                            i.putExtra(INTENT_EXTRA_IS_CLOSE_WINDOW, true);
                            //WindowService.dequeueWork(mContext, i);
                            mContext.startService(i);
                        }
                        result.success(true);
                    }
                    break;

                default:
                    result.notImplemented();
            }
        } catch (Exception ex) {
            Log.e(TAG, ex.toString());
        }

    }

    private boolean isBubbleMode(String prefMode) {
        boolean isPreferOverlay = "overlay".equalsIgnoreCase(prefMode);
        return Commons.isForceAndroidBubble(mContext) ||
                (!isPreferOverlay && ("bubble".equalsIgnoreCase(prefMode) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R));
    }

    /*public static void setPluginRegistrant(PluginRegistry.PluginRegistrantCallback callback) {
        sPluginRegistrantCallback = callback;
    }*/


    @TargetApi(Build.VERSION_CODES.M)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE) {
            if (!Settings.canDrawOverlays(mContext)) {
                Log.e(TAG, "System Alert Window will not work without 'Can Draw Over Other Apps' permission");
                Toast.makeText(mContext, "System Alert Window will not work without 'Can Draw Over Other Apps' permission", Toast.LENGTH_LONG).show();
            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean askPermission(boolean isOverlay) {
        if (!isOverlay && (Commons.isForceAndroidBubble(mContext) || Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)) {
            return NotificationHelper.getInstance(mContext).areBubblesAllowed();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(mContext)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + mContext.getPackageName()));
                if (mActivity == null) {
                    if (mContext != null) {
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                        Toast.makeText(mContext, "Please grant, Can Draw Over Other Apps permission.", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Can't detect the permission change, as the mActivity is null");
                    } else {
                        Log.e(TAG, "'Can Draw Over Other Apps' permission is not granted");
                        Toast.makeText(mContext, "Can Draw Over Other Apps permission is required. Please grant it from the app settings", Toast.LENGTH_LONG).show();
                    }
                } else {
                    mActivity.startActivityForResult(intent, ACTION_MANAGE_OVERLAY_PERMISSION_REQUEST_CODE);
                }
            } else {
                return true;
            }
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public boolean checkPermission(boolean isOverlay) {
        if (!isOverlay && (Commons.isForceAndroidBubble(mContext) || Build.VERSION.SDK_INT > Build.VERSION_CODES.Q)) {
            //return NotificationHelper.getInstance(mContext).areBubblesAllowed();
            return true;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(mContext);
        }
        return false;
    }

}
