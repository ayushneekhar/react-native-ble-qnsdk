package com.coachcare;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.IllegalViewOperationException;

import com.coachcare.User;

import com.qingniu.qnble.utils.QNLogUtils;
import com.yolanda.health.qnblesdk.constant.QNUnit;
import com.yolanda.health.qnblesdk.listener.QNBleConnectionChangeListener;
import com.yolanda.health.qnblesdk.listener.QNScaleDataListener;
import com.yolanda.health.qnblesdk.listener.QNResultCallback;
import com.yolanda.health.qnblesdk.out.QNBleApi;
import com.yolanda.health.qnblesdk.constant.CheckStatus;
import com.yolanda.health.qnblesdk.constant.QNIndicator;
import com.yolanda.health.qnblesdk.constant.UserGoal;
import com.yolanda.health.qnblesdk.constant.UserShape;
import com.yolanda.health.qnblesdk.listener.QNBleDeviceDiscoveryListener;
import com.yolanda.health.qnblesdk.listener.QNResultCallback;
import com.yolanda.health.qnblesdk.out.QNBleApi;
import com.yolanda.health.qnblesdk.out.QNBleBroadcastDevice;
import com.yolanda.health.qnblesdk.out.QNBleDevice;
import com.yolanda.health.qnblesdk.out.QNBleKitchenDevice;
import com.yolanda.health.qnblesdk.out.QNConfig;
import com.yolanda.health.qnblesdk.out.QNScaleData;
import com.yolanda.health.qnblesdk.out.QNScaleItemData;
import com.yolanda.health.qnblesdk.out.QNScaleStoreData;
import com.yolanda.health.qnblesdk.out.QNShareData;
import com.yolanda.health.qnblesdk.out.QNUser;
import com.yolanda.health.qnblesdk.out.QNUtils;
import com.yolanda.health.qnblesdk.out.QNWiFiConfig;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;


public class BleQnsdkModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

  private final String TAG=BleQnsdkModule.class.getSimpleName();

  private final Context mContext;
  private final ReactApplicationContext reactContext;
  private QNBleApi mQNBleApi;
  public User mUser = new User();
  private boolean loaded = false;
  public static final String FORMAT_SHORT = "yyyy-MM-dd";

  public void EmitData(double weight) {
    if (eventContext != null) {

      eventContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
              .emit("onStop", weight);
    }
  }


  void emitResult(QNBleDevice qnBleDevice, QNScaleData qnScaleData) {
    try {
      BleScaleData scaleData = qnScaleData.getBleScaleData();
      // resultObj = new JSONObject();
      WritableMap resultObj = Arguments.createMap();


      resultObj.putDouble("body_bmi", scaleData.getBmi());
      resultObj.putDouble("body_bmr", scaleData.getBmr());
      resultObj.putDouble("body_fat_ratio", scaleData.getBodyfat());
      resultObj.putDouble("body_muscle", scaleData.getMuscle());
      resultObj.putDouble("body_fat_sub", scaleData.getSubfat());
      resultObj.putDouble("muscle_weight", scaleData.getMuscleMass());
      resultObj.putDouble("bone_mass", scaleData.getBone());
      resultObj.putDouble("water_content", scaleData.getWater());
      resultObj.putDouble("body_weight", scaleData.getWeight());
      resultObj.putDouble("protein", scaleData.getProtein());
      resultObj.putDouble("metabolic_age", scaleData.getBodyAge());
      Log.d("scale_data", scaleData.toString());


      sendEventToJS("onHealthData", resultObj);
    } catch (Exception error) {
      error.printStackTrace();
      Log.d(TAG, "Final Result Object ERROR : > " + error.getMessage());

    }
  }

  public BleQnsdkModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.mContext = reactContext.getApplicationContext();
    this.eventContext = reactContext;
    mQNBleApi = QNBleApi.getInstance(this.mContext);
    initBleConnectStatus();
    mQNBleApi.setDataListener(new QNScaleDataListener() {
      @Override
      public void onGetUnsteadyWeight(QNBleDevice device, double weight) {
        Log.d(TAG, initWeight(weight));
        EmitData(weight);
      }

      @Override
      public void onGetScaleData(QNBleDevice device, QNScaleData data) {
        emitResult(device, data);
        Log.d(TAG, "收到测量数据");
        Log.d(TAG, initScaleDate(data));
        Log.d(TAG, data.toString());
        data.getItemValue(QNIndicator.TYPE_WEIGHT);
        Log.d(TAG, "enter onGetScaleData>>>" + data.getBleScaleData().toString());
        Log.d(TAG, "enter onGetScaleData BMI >>>" + data.getBleScaleData().getBmi());
        Log.d(TAG, "enter onGetScaleData Body FAT >>>" + data.getBleScaleData().getBodyfat());
        Log.d(TAG, "enter onGetScaleData Body getBmr >>>" + data.getBleScaleData().getBmr());


        // Log.d("scale_dataaaaaa", data.getItemValue(QNIndicator.TYPE_WEIGHT).toString());
        // emit
        // EmitHealthData(data);

      }

      @Override
      public void onGetStoredScale(QNBleDevice device, List<QNScaleStoreData> storedDataList) {
        Log.d("ConnectActivity", "收到存储数据");
        // EmitStoredData(storedDataList);

      }

      @Override
      public void onGetElectric(QNBleDevice device, int electric) {
        String text = "收到电池电量百分比:" + electric;
        Log.d("ConnectActivity", text);

      }

      // 测量过程中的连接状态
      @Override
      public void onScaleStateChange(QNBleDevice device, int status) {
        Log.d("ConnectActivity", "秤的连接状态是:" + status);
      }

      @Override
      public void onScaleEventChange(QNBleDevice device, int scaleEvent) {
        Log.d("ConnectActivity", "秤返回的事件是:" + scaleEvent);

      }

      @Override
      public void readSnComplete(QNBleDevice device, String sn) {

      }

      // get other health data
      // @Override
      // public void onGetHealthData(QNBleDevice device, QNScaleData healthData) {
      //     Log.d("ConnectActivity", "收到健康数据");
      //     healthData.getItemValue(QNIndicator.TYPE_BMI);
      //     healthData.getItemValue(QNIndicator.TYPE_BMR);
      //     healthData.getItemValue(QNIndicator.TYPE_BODY_AGE);
      //     healthData.getItemValue(QNIndicator.TYPE_BODY_FAT);
      //     healthData.getItemValue(QNIndicator.TYPE_BODY_WATER);
      //     healthData.getItemValue(QNIndicator.TYPE_BONE_SALT);
      //     healthData.getItemValue(QNIndicator.TYPE_FAT_FREE_WEIGHT);
      //     healthData.getItemValue(QNIndicator.TYPE_FAT_WEIGHT);
      //     healthData.getItemValue(QNIndicator.TYPE_MUSCLE_MASS);
      //     healthData.getItemValue(QNIndicator.TYPE_MUSCLE_RATE);
      //     healthData.getItemValue(QNIndicator.TYPE_PHYSICAGE);
      //     healthData.getItemValue(QNIndicator.TYPE_PROTEIN);
      //     healthData.getItemValue(QNIndicator.TYPE_SKELETAL_MUSCLE_RATE);
      //     healthData.getItemValue(QNIndicator.TYPE_SUBFAT);
      //     healthData.getItemValue(QNIndicator.TYPE_VISCERAL_FAT);
      //     healthData.getItemValue(QNIndicator.TYPE_WEIGHT);

      //     // emit
      //     EmitHealthData(healthData);
      // }


    });

  }

  public void initialize() {

    if (this.loaded == false) {
      final ReactApplicationContext context = getReactApplicationContext();
      mQNBleApi = QNBleApi.getInstance(context);
      this.setConfig();

      this.initSDK();
    }

    this.loaded = true;
  }

  public void setConfig() {
    QNConfig mQnConfig = mQNBleApi.getConfig();
    mQnConfig.setNotCheckGPS(true);
    mQnConfig.setAllowDuplicates(false);
    mQnConfig.setDuration(0);
    mQnConfig.setOnlyScreenOn(false);

    mQnConfig.save(new QNResultCallback() {
      @Override
      public void onResult(int i, String s) {
        Log.d("ScanActivity", "initData:" + s);
      }
    });
  }

  public void initSDK() {
    String encryptPath = "file:///android_asset/123456789.qn";
    mQNBleApi.initSdk("123456789", encryptPath, new QNResultCallback() {
      @Override
      public void onResult(int code, String msg) {
        Log.d(TAG, "Initialization file\n" + msg);
      }
    });
  }

  @Override
  public void onHostResume() {
    this.initialize();
  }

  @Override
  public void onHostPause() {
    Log.w("Scale", "on onHostPause");
  }

  @Override
  public void onHostDestroy() {
    Log.w("S", "on onHostDestroy");
  }

  private QNUser createQNUser() {
    UserShape userShape = UserShape.SHAPE_NORMAL;
    ;
    String dateString = "04/01/2001";
    String dateFormatPattern = "MM/dd/yyyy";
    SimpleDateFormat dateFormat = new SimpleDateFormat(dateFormatPattern);

    UserGoal userGoal = UserGoal.GOAL_STAY_HEALTH;
    Date date = new Date(0);

    try {

      date = dateFormat.parse(dateString);
    } catch (Exception e) {

    }

    return mQNBleApi.buildUser("2345",
            180, "male", date, QNInfoConst.CALC_ATHLETE,
            userShape, userGoal, 20.0, new QNResultCallback() {
              @Override
              public void onResult(int code, String msg) {
                Log.d(TAG, ":onResult" + msg);
              }
            });
  }

  @Override
  public String getName() {
    return "QNSDKManager";
  }

  @ReactMethod
  public void buildUser(String name, String birthday, int height, String gender, String id, int unit, int athleteType, Promise promise) {
    try {
      SimpleDateFormat sdf = new SimpleDateFormat(FORMAT_SHORT); // here set the pattern as you date in string was containing like date/month/year
      Date formattedBirthday = sdf.parse(birthday);

      this.mUser.setAthleteType(athleteType);
      this.mUser.setBirthDay(formattedBirthday);
      this.mUser.setGender(gender);
      this.mUser.setHeight(height);
      this.mUser.setUserId(id);


      mQNBleApi.buildUser(id,
              height, gender, formattedBirthday, athleteType, new QNResultCallback() {
                @Override
                public void onResult(int code, String msg) {
                  Log.w(TAG, "Build User message:" + msg);
                }
              });

      QNConfig mQnConfig = mQNBleApi.getConfig();
      mQnConfig.setUnit(unit);

      promise.resolve(true);
    } catch (IllegalViewOperationException | ParseException e) {
      promise.reject("build user reject", e);
    }
  }

  public static void verifyPermissions(Activity activity) {
    if (ContextCompat.checkSelfPermission(activity,
            Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
    }
    if (ActivityCompat.shouldShowRequestPermissionRationale(activity,
            Manifest.permission.ACCESS_COARSE_LOCATION)) {
    }


    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
      return;
    } else {
    }

    if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
      return;
    } else {
    }
  }

  public void sendEventToJS(String eventName, @Nullable WritableMap params) {
    this.reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
  }

  private void connectQnDevice(QNBleDevice device) {
    mQNBleApi.connectDevice(device, createQNUser(), new QNResultCallback() {
      @Override
      public void onResult(int code, String msg) {
        Log.d("Device Status", code + msg);
      }
    });

  }

  private String initWeight(double weight) {
    int unit = mQNBleApi.getConfig().getUnit();
    return mQNBleApi.convertWeightWithTargetUnit(weight, unit);
  }

  private String initScaleDate(QNScaleData data) {
    StringBuilder stringBuilder = new StringBuilder();
    // stringBuilder.append("测量时间:" + data.getMeasureTime() + "\n");
    // stringBuilder.append("用户ID:" + data.getUserId() + "\n");
    // stringBuilder.append("测量结果:" + data.getScaleType() + "\n");
    // stringBuilder.append("电量:" + data.getElectric() + "\n");
    // stringBuilder.append("体重:" + data.getWeight() + "\n");
    // stringBuilder.append("阻抗:" + data.getImpedance() + "\n");
    // stringBuilder.append("年龄:" + data.getAge() + "\n");
    // stringBuilder.append("身高:" + data.getHeight() + "\n");
    // stringBuilder.append("性别:" + data.getSex() + "\n");
    // stringBuilder.append("单位:" + data.getUnit() + "\n");
    // stringBuilder.append("体重:" + data.getItemValue(QNIndicator.TYPE_WEIGHT) + "\n");
    // stringBuilder.append("BMI:" + data.getItemValue(QNIndicator.TYPE_BMI) + "\n");
    // stringBuilder.append("体脂率:" + data.getItemValue(QNIndicator.TYPE_BODY_FAT) + "\n");
    // stringBuilder.append("去脂体重:" + data.getItemValue(QNIndicator.TYPE_FAT_FREE_WEIGHT) + "\n");
    // stringBuilder.append("水分含量:" + data.getItemValue(QNIndicator.TYPE_BODY_WATER) + "\n");
    // stringBuilder.append("肌肉量:" + data.getItemValue(QNIndicator.TYPE_MUSCLE_MASS) + "\n");
    // stringBuilder.append("骨量:" + data.getItemValue(QNIndicator.TYPE_BONE_MASS) + "\n");
    // stringBuilder.append("内脏脂肪等级:" + data.getItemValue(QNIndicator.TYPE_VISCERAL_FAT) + "\n");
    // stringBuilder.append("基础代谢:" + data.getItemValue(QNIndicator.TYPE_BMR) + "\n");
    // stringBuilder.append("身体年龄:" + data.getItemValue(QNIndicator.TYPE_BODY_AGE) + "\n");
    // stringBuilder.append("蛋白质:" + data.getItemValue(QNIndicator.TYPE_PROTEIN) + "\n");
    // stringBuilder.append("骨骼肌率:" + data.getItemValue(QNIndicator.TYPE_SKELETAL_MUSCLE_RATE) + "\n");
    // stringBuilder.append("脂肪率:" + data.getItemValue(QNIndicator.TYPE_BODY_FAT_RATE) + "\n");
    // stringBuilder.append("皮下脂肪率:" + data.getItemValue(QNIndicator.TYPE_SUBCUTANEOUS_FAT) + "\n");
    // stringBuilder.append("体型:" + data.getItemValue(QNIndicator.TYPE_BODY_SHAPE) + "\n");
    // stringBuilder.append("健康得分:" + data.getItemValue(QNIndicator.TYPE_HEALTH_SCORE) + "\n");
    // stringBuilder.append("肌肉得分:" + data.getItemValue(QNIndicator.TYPE_MUSCLE_SCORE) + "\n");
    // stringBuilder.append("体重得分:" + data.getItemValue(QNIndicator.TYPE_WEIGHT_SCORE) + "\n");
    // stringBuilder.append("脂肪得分:" + data.getItemValue(QNIndicator.TYPE_FAT_SCORE) + "\n");
    // stringBuilder.append("水分得分:" + data.getItemValue(QNIndicator.TYPE_WATER_SCORE) + "\n");
    // stringBuilder.append("骨量得分:" + data.getItemValue(QNIndicator.TYPE_BONE_SCORE) + "\n");
    // stringBuilder.append("蛋白质得分:" + data.getItemValue(QNIndicator.TYPE_PROTEIN_SCORE) + "\n");
    // stringBuilder.append("身体年龄得分:" + data.getItemValue(QNIndicator.TYPE_BODY_AGE_SCORE) + "\n");
    // stringBuilder.append("内脏脂肪得分:" + data.getItemValue(QNIndicator.TYPE_VISCERAL_FAT_SCORE) + "\n");
    // stringBuilder.append("蛋白质率:" + data.getItemValue(QNIndicator.TYPE_PROTEIN_RATE) + "\n");
    // stringBuilder.append("骨骼肌率:" + data.getItemValue(QNIndicator.TYPE_SKELETAL_MUSCLE_RATE) + "\n");
    // stringBuilder.append("脂肪率:" + data.getItemValue(QNIndicator.TYPE_BODY_FAT_RATE) + "\n");
    // stringBuilder.append("皮下脂肪率:" + data.getItemValue(QNIndicator.TYPE_SUBCUTANEOUS_FAT) + "\n");
    // stringBuilder.append("体型:" + data.getItemValue(QNIndicator.TYPE_BODY_SHAPE) + "\n");
    // stringBuilder.append("健康得分:" + data.getItemValue(QNIndicator.TYPE_HEALTH_SCORE) + "\n");
    // stringBuilder.append("肌肉得分:" + data.getItemValue(QNIndicator.TYPE_MUSCLE_SCORE) + "\n");
    // stringBuilder.append("体重得分:" + data.getItemValue(QNIndicator.TYPE_WEIGHT_SCORE) + "\n");

    return stringBuilder.toString();
  }



  @ReactMethod
  public void initBleConnectStatus() {

    Log.d(TAG, "Intialised from React Native");
    this.mQNBleApi.setBleConnectionChangeListener(new QNBleConnectionChangeListener() {
      @Override
      public void onConnecting(QNBleDevice device) {
        Log.d(TAG, "Connecting");
      }

      @Override
      public void onConnected(QNBleDevice device) {
        Log.d(TAG, "Connected");

      }

      @Override
      public void onServiceSearchComplete(QNBleDevice device) {
        Log.d(TAG, "Search COmplete");
      }

      @Override
      public void onDisconnecting(QNBleDevice device) {
        Log.d(TAG, "Disconnecting");
      }

      @Override
      public void onDisconnected(QNBleDevice device) {
        Log.d(TAG, "Disconnected");
      }

      @Override
      public void onConnectError(QNBleDevice device, int errorCode) {
        Log.d(TAG, "onConnectError:" + errorCode);
      }

      @Override
      public void onStartInteracting(QNBleDevice device) {

      }

    });
  }


  @ReactMethod
  public void onStartDiscovery(String name, final Promise promise) {
    Activity activity = getCurrentActivity();
    verifyPermissions(activity);

    Handler mHandler = new Handler();
    mHandler.post(new Runnable() {

      @Override
      public void run() {
        mQNBleApi.startBleDeviceDiscovery(new QNResultCallback() {
          @Override
          public void onResult(int code, String msg) {
            if (code != CheckStatus.OK.getCode()) {
              promise.resolve("Success scan scan: ");
            }

          }
        });
      }
    });
  }

  @ReactMethod
  public void stopScan(Callback callback) {
    mQNBleApi.stopBleDeviceDiscovery(new QNResultCallback() {
      @Override
      public void onResult(int code, String msg) {
        if (code == CheckStatus.OK.getCode()) {
          callback.invoke("stopScan: ");
        }
      }
    });
    // TODO: Implement some actually useful functionality

  }

  @ReactMethod
  public void startScan() {
    mQNBleApi.setBleDeviceDiscoveryListener(new QNBleDeviceDiscoveryListener() {
      @Override
      public void onDeviceDiscover(QNBleDevice device) {
        // devices.add(device);
        Log.d(TAG, device.getDeviceType() + "----" + QNDeviceType.USER_SCALE);
        mBleDevice = device;
        connectQnDevice(device);

        stopScan();
      }

      @Override
      public void onStartScan() {
        Log.e(TAG, "onStartScan");
      }

      @Override
      public void onStopScan() {
        Log.e(TAG, "onStopScan");

      }

      @Override
      public void onScanFail(int code) {
        Log.e(TAG, "onScanFail:" + code);

      }

      @Override
      public void onBroadcastDeviceDiscover(QNBleBroadcastDevice device) {
      }

      @Override
      public void onKitchenDeviceDiscover(QNBleKitchenDevice device) {
        if (device.isBluetooth()) {

        }
      }
    });
    mQNBleApi.startBleDeviceDiscovery(new QNResultCallback() {
      @Override
      public void onResult(int code, String msg) {
        Log.d(TAG, "code:" + code + ";msg:" + msg);
      }
    });
  }

}
