package im.shimo.geetest2;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import androidx.core.app.ActivityCompat;
import android.util.Log;

import com.example.sdk.Geetest;
import com.example.sdk.GtDialog;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

/**
 * Created by bell on 2017/9/27.
 */

public class GeetestModule extends ReactContextBaseJavaModule {
    private static final String E_CALLBACK_ERROR = "E_CALLBACK_ERROR";
    private static final String E_PERMISSIONS_MISSING = "E_PERMISSIONS_MISSING";

    private Boolean debug = false;
    private GtAppDlgTask mGtAppDlgTask;
    private GtAppValidateTask mGtAppValidateTask;
    private Geetest captcha;
    private Promise mPromise;

    private String mChallenge;
    private String mCaptchaId;
    private int mSuccess;

    private Promise mOpenGTViewpromise;

    public GeetestModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "GeetestModule";
    }

    @ReactMethod
    public void setDebugMode(Boolean debug) {
        this.debug = debug;
    }

    @ReactMethod
    public void configure(String captchaId, String challenge, int success, Promise promise) {
        mChallenge = challenge;
        mCaptchaId = captchaId;
        mSuccess = success;

        WritableMap map = Arguments.createMap();
        promise.resolve(map);
    }

    @ReactMethod
    public void openGTView(Boolean animated, final Promise promise) {
        mPromise = promise;

        JSONObject params = new JSONObject();
        try {
            params.put("challenge", mChallenge);
            params.put("success", mSuccess);
            params.put("gt", mCaptchaId);
            openGtTest(getCurrentActivity(), params);
        } catch (JSONException e) {
            e.printStackTrace();
            reject("E_OPEN_FAILED", "open geetest failed");
        }
    }

    @ReactMethod
    public void request(String challengeURL, String validateURL, Promise promise) {
        mPromise = promise;
        Geetest _captcha = new Geetest(challengeURL, validateURL);
        captcha = _captcha;
        captcha.setTimeout(30000);

        captcha.setGeetestListener(new Geetest.GeetestListener() {
            @Override
            public void readContentTimeout() {
                mGtAppDlgTask.cancel(true);
                Log.e("GeetestModule", "read content time out");
            }

            @Override
            public void submitPostDataTimeout() {
                // 提交二次验证超时
                mGtAppValidateTask.cancel(true);
                Log.e("GeetestModule", "submit error");
            }

            @Override
            public void receiveInvalidParameters() {
                Log.e("GeetestModule", "ecieve invalid parameters");
            }
        });

        GtAppDlgTask gtAppDlgTask = new GtAppDlgTask();
        mGtAppDlgTask = gtAppDlgTask;
        mGtAppDlgTask.execute();
    }

    private void sendValidationEvent(Boolean success) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit("GeetestValidationFinished", success);
    }

    class GtAppDlgTask extends AsyncTask<Void, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Void... params) {
            Log.i("GeetestModule", "geetest checking server");
            return captcha.checkServer();
        }

        @Override
        protected void onPostExecute(JSONObject params) {
            if (params != null) {
                // 根据captcha.getSuccess()的返回值 自动推送正常或者离线验证
                if (captcha.getSuccess()) {
                    Log.i("GeetestModule", "captcha get success");
                    openGtTest(getCurrentActivity(), params);
                } else {
                    // 从API_1获得极验服务宕机或不可用通知, 使用备用验证或静态验证
                    // 静态验证依旧调用上面的openGtTest(_, _, _), 服务器会根据getSuccess()的返回值, 自动切换
                    // openGtTest(getCurrentActivity(), params);
                    Log.e("GeetestModule", "Geetest Server is Down.");

                    // 执行此处网站主的备用验证码方案
                }
            } else {
                Log.e("GeetestModule", "Can't Get Data from API_1");
            }
        }
    }

    class GtAppValidateTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            try {
                JSONObject resJson = new JSONObject(params[0]);
                Map<String, String> validateParams = new HashMap<String, String>();
                validateParams.put("geetest_challenge", resJson.getString("geetest_challenge"));
                validateParams.put("geetest_validate", resJson.getString("geetest_validate"));
                validateParams.put("geetest_seccode", resJson.getString("geetest_seccode"));
                String response = captcha.submitPostData(validateParams, "utf-8");
                // 验证通过, 获取二次验证响应, 根据响应判断验证是否通过完整验证
                resolve(null);
                sendValidationEvent(true);
                return response;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "invalid result";
        }

        @Override
        protected void onPostExecute(String params) {
        }
    }

    private void openGtTest(final Context ctx, final JSONObject params) {
        final Activity activity = getCurrentActivity();
        if (activity == null) {
            reject("E_OPEN_FAILED", "open geetest failed");
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                permissionsCheck(activity, Arrays.asList(Manifest.permission.READ_PHONE_STATE), new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        doOpenGtTest(ctx, params);
                        return null;
                    }
                });
            }
        });
    }

    private void doOpenGtTest(Context ctx, JSONObject params) {
        Log.i("GeetestModule", "open geetest");
        GtDialog dialog = new GtDialog(ctx, params);
        // 启用debug可以在webview上看到验证过程的一些数据
        dialog.setDebug(this.debug);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // 取消验证
                Log.i("GeetestModule", "user close the geetest.");
                reject("E_CANCEL", "geetest verify canceled");
                sendValidationEvent(false);
            }
        });

        dialog.setGtListener(new GtDialog.GtListener() {
            @Override
            public void gtResult(boolean success, String result) {
                if (success) {
                    try {
                        JSONObject resJson = new JSONObject(result);
                        WritableMap resultMap = Arguments.createMap();
                        resultMap.putString("geetest_challenge", resJson.getString("geetest_challenge"));
                        resultMap.putString("geetest_validate", resJson.getString("geetest_validate"));
                        resultMap.putString("geetest_seccode", resJson.getString("geetest_seccode"));

                        WritableMap map = Arguments.createMap();
                        map.putString("code", "1");
                        map.putMap("result", resultMap);
                        map.putString("message", "验证成功");

                        resolve(map);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    // 验证失败
                    Log.i("GeetestModule", "geetest verify failed");
                }
            }

            @Override
            public void gtCallClose() {
                reject("E_CANCEL", "geetest verify canceled");
            }

            @Override
            public void gtCallReady(Boolean status) {
                if (status) {
                    // 验证加载完成
                    Log.i("GeetestModule", "geetest finish load");
                } else {
                    // 验证加载超时,未准备完成
                    Log.e("GeetestModule", "there's a network jam");
                    reject("E_LOAD_FAILED", "geetest load failed");
                }
            }

            @Override
            public void gtError() {
                Log.e("GeetestModule", "Fatal Error Did Occur.");
            }
        });
    }

    private void resolve(@Nullable Object value) {
        if (mPromise != null) {
            mPromise.resolve(value);
            mPromise = null;
        }
    }

    private void reject(String code, String message) {
        if (mPromise != null) {
            mPromise.reject(code, message);
            mPromise = null;
        }
    }

    private void reject(String code, String message, Throwable e) {
        if (mPromise != null) {
            mPromise.reject(code, message, e);
            mPromise = null;
        }
    }

    private void permissionsCheck(final Activity activity, final List<String> requiredPermissions, final Callable<Void> callback) {

        List<String> missingPermissions = new ArrayList<>();

        for (String permission : requiredPermissions) {
            int status = ActivityCompat.checkSelfPermission(activity, permission);
            if (status != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            ((PermissionAwareActivity) activity).requestPermissions(missingPermissions.toArray(new String[missingPermissions.size()]), 1, new PermissionListener() {

                @Override
                public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
                    if (requestCode == 1) {
                        for (int grantResult : grantResults) {
                            if (grantResult == PackageManager.PERMISSION_DENIED) {
                                reject(E_PERMISSIONS_MISSING, "Required permission missing");
                                return true;
                            }
                        }
                        try {
                            callback.call();
                        } catch (Exception e) {
                            reject(E_CALLBACK_ERROR, "Unknown error", e);
                        }
                    }
                    return true;
                }
            });
            return;
        }
        // all permissions granted
        try {
            callback.call();
        } catch (Exception e) {
            reject(E_CALLBACK_ERROR, "Unknown error", e);
        }
    }
}
