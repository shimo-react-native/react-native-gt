package im.shimo.geetest2;

import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
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

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bell on 2017/9/27.
 */

public class GeetestModule extends ReactContextBaseJavaModule {

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
        getCurrentActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                JSONObject params = new JSONObject();
                try {
                    params.put("challenge", mChallenge);
                    params.put("success", mSuccess);
                    params.put("gt", mCaptchaId);

                    openGtTest(getCurrentActivity(), params);
                } catch (JSONException e) {
                    e.printStackTrace();
                    mPromise.reject("400", "open GTView failed");
                }
            }
        });
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
                Log.e("geetest", "read content time out");
            }

            @Override
            public void submitPostDataTimeout() {
                //TODO 提交二次验证超时
                mGtAppValidateTask.cancel(true);
                Log.e("geetest", "submit error");
            }

            @Override
            public void receiveInvalidParameters() {
                Log.e("geetest", "ecieve invalid parameters");
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
            Log.i("geetest", "geetest checking server");
            return captcha.checkServer();
        }

        @Override
        protected void onPostExecute(JSONObject params) {
            if (params != null) {
                // 根据captcha.getSuccess()的返回值 自动推送正常或者离线验证
                if (captcha.getSuccess()) {
                    Log.i("geetest", "captcha get success");
                    openGtTest(getCurrentActivity(), params);
                } else {
                    // TODO 从API_1获得极验服务宕机或不可用通知, 使用备用验证或静态验证
                    // 静态验证依旧调用上面的openGtTest(_, _, _), 服务器会根据getSuccess()的返回值, 自动切换
                    // openGtTest(getCurrentActivity(), params);
                    Log.e("geetest", "Geetest Server is Down.");

                    // 执行此处网站主的备用验证码方案
                }
            } else {
                Log.e("geetest", "Can't Get Data from API_1");
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
                mPromise.resolve(null);
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

    public void openGtTest(Context ctx, JSONObject params) {
        Log.i("geetest", "open geetest");
        GtDialog dialog = new GtDialog(ctx, params);
        // 启用debug可以在webview上看到验证过程的一些数据
        dialog.setDebug(this.debug);

        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // 取消验证
                Log.i("geetest", "user close the geetest.");
                mPromise.reject("400", "cancel");
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

                        mPromise.resolve(map);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        mPromise.reject("400", "failed");
                    }

                } else {
                    // TODO 验证失败
                    mPromise.reject("400", "failed");
                }
            }

            @Override
            public void gtCallClose() {
                mPromise.reject("400", "验证取消");
            }

            @Override
            public void gtCallReady(Boolean status) {
                if (status) {
                    //TODO 验证加载完成
                    Log.i("geetest", "geetest finish load");
                } else {
                    //TODO 验证加载超时,未准备完成
                    Log.e("geetest", "there's a network jam");
                }
            }

            @Override
            public void gtError() {
                Log.e("geetest", "Fatal Error Did Occur.");
                mPromise.reject("400", "failed");
            }
        });
    }
}
