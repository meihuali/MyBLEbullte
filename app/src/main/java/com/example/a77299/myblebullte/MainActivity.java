package com.example.a77299.myblebullte;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Logger;

import cn.com.heaton.blelibrary.ble.Ble;
import cn.com.heaton.blelibrary.ble.BleDevice;
import cn.com.heaton.blelibrary.ble.L;
import cn.com.heaton.blelibrary.ble.callback.BleConnCallback;
import cn.com.heaton.blelibrary.ble.callback.BleNotiftCallback;
import cn.com.heaton.blelibrary.ble.callback.BleScanCallback;

public class MainActivity extends BaseActivity implements View.OnClickListener {

    StringBuilder sb = new StringBuilder();

    private Ble<BleDevice> mBle;
    private StringBuilder result;
    private TextView tv_current_temperature;
    private TextView tv_max_temperature;
    private TextView tv_min_temperature;
    private TextView tv_connect_state;
    private TextView tv_rcry_connect;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        tv_current_temperature = findViewById(R.id.tv_current_temperature);

        tv_max_temperature = findViewById(R.id.tv_max_temperature);

        tv_min_temperature = findViewById(R.id.tv_min_temperature);
        tv_connect_state = findViewById(R.id.tv_connect_state);
        tv_rcry_connect = findViewById(R.id.tv_rcry_connect);
        tv_rcry_connect.setOnClickListener(this);
    }

    @Override
    protected void onInitView() {
        //1、请求蓝牙相关权限
        requestPermission();
    }

    private void requestPermission() {
        requestPermission(new String[]{Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_COARSE_LOCATION},
                "请求蓝牙相关权限", new GrantedResult() {
                    @Override
                    public void onResult(boolean granted) {
                        if (granted) {
                            //2、初始化蓝牙
                            initBle();
                        } else {
                            finish();
                        }
                    }
                });
    }

    private void initBle() {
        mBle = Ble.options()
                .setLogBleExceptions(true)//设置是否输出打印蓝牙日志
                .setThrowBleException(true)//设置是否抛出蓝牙异常
                .setAutoConnect(true)//设置是否自动连接
                .setConnectFailedRetryCount(3)
                .setConnectTimeout(10 * 1000)//设置连接超时时长
                .setScanPeriod(12 * 1000)//设置扫描时长
                .setUuid_service(UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb"))//设置主服务的uuid
                .setUuid_write_cha(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"))//设置可写特征的uuid
                .create(getApplicationContext());
//        mBle = Ble.create(getApplicationContext());
        //3、检查蓝牙是否支持及打开
        checkBluetoothStatus();
    }
    //检查蓝牙是否支持及打开
    private void checkBluetoothStatus() {
        // 检查设备是否支持BLE4.0
        if (!mBle.isSupportBle(this)) {
            Toast.makeText(getApplicationContext(),"该手机不支持蓝牙",Toast.LENGTH_SHORT).show();
            finish();
        }
        if (!mBle.isBleEnable()) {
            //4、若未打开，则请求打开蓝牙
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, Ble.REQUEST_ENABLE_BT);
        } else {
            //5、若已打开，则进行扫描
            mBle.startScan(scanCallback);
        }
    }

    /**
     * 扫描的回调
     */
    BleScanCallback<BleDevice> scanCallback = new BleScanCallback<BleDevice>() {
        @Override
        public void onLeScan(final BleDevice device, int rssi, byte[] scanRecord) {

            if (device.getBleName().equals("MLT-BT05")) {
                Logger.getLogger(device.getBleName());

                if (device == null) return;
                if (mBle.isScanning()) {
                    mBle.stopScan();
                }
                if (device.isConnected()) {
                    mBle.disconnect(device);
                }
                if (!device.isConnected()) {
                    //mBle.disconnect(device);
                    //扫描到设备时   务必用该方式连接(是上层逻辑问题， 否则点击列表  虽然能够连接上，但设备列表的状态不会发生改变)
                    mBle.connect(device, connectCallback);
                    //此方式只是针对不进行扫描连接（如上，若通过该方式进行扫描列表的连接  列表状态不会发生改变）
//            mBle.connect(device.getBleAddress(), connectCallback);
                }


               // return;
            } else {
                tv_connect_state.setText("连接状态:失败未搜到设备");
                tv_rcry_connect.setVisibility(View.VISIBLE);
                tv_rcry_connect.setText("重试");
                tv_rcry_connect.setEnabled(true);
            }
/*            synchronized (mBle.getLocker()) {
                mLeDeviceListAdapter.addDevice(device);
                mLeDeviceListAdapter.notifyDataSetChanged();
            }*/
        }

        @Override
        public void onStop() {
            super.onStop();
            L.e(TAG, "onStop: ");
            tv_rcry_connect.setEnabled(true);
            tv_rcry_connect.setText("重试");
        }
    };

    /**
     * 连接的回调
     */
    private BleConnCallback<BleDevice> connectCallback = new BleConnCallback<BleDevice>() {
        @Override
        public void onConnectionChanged(final BleDevice device) {
            if (device.isConnected()) {
                tv_connect_state.setText("连接状态:成功");
                tv_connect_state.setTextColor(getResources().getColor(R.color.colorAccent));
                /*连接成功后，设置通知*/
                mBle.startNotify(device, bleNotiftCallback);
                tv_rcry_connect.setVisibility(View.GONE);

            } else {
                tv_rcry_connect.setVisibility(View.VISIBLE);
                tv_connect_state.setText("连接状态:失败");
                tv_rcry_connect.setEnabled(true);
                tv_rcry_connect.setText("重试");
            }
            L.e(TAG, "onConnectionChanged: " + device.isConnected());

        }

        @Override
        public void onConnectException(BleDevice device, int errorCode) {
            super.onConnectException(device, errorCode);
            Toast.makeText(getApplicationContext(),"连接异常，ErrorCode = "+errorCode,Toast.LENGTH_SHORT).show();
        }
    };

    /**
     * 设置通知的回调 (接受数据)
     *
     */
    private BleNotiftCallback<BleDevice> bleNotiftCallback = new BleNotiftCallback<BleDevice>() {
        @Override
        public void onChanged(BleDevice device, BluetoothGattCharacteristic characteristic) {
/*            UUID uuid = characteristic.getUuid();
            L.e(TAG, "onChanged==uuid:" + uuid.toString());
            L.e(TAG, "onChanged==address:" + device.getBleAddress());*/
            L.e(TAG, "onChanged==data:" + Arrays.toString(characteristic.getValue()));

            /**
             * {"tmp":"test","now_tmp":"37.6","max_tmp":"38.1","min_tmp":"36.5"}  模拟数据
             * */

            try {
                if (result == null) {
                    result = sb.append(new String(characteristic.getValue(), "UTF-8"));
                    String over = result.toString();
                    Toast.makeText(getApplicationContext(),over,Toast.LENGTH_SHORT).show();
                } else {
                    result = sb.append(new String(characteristic.getValue(), "UTF-8"));
                    String over = result.toString();
                    Toast.makeText(getApplicationContext(),over,Toast.LENGTH_SHORT).show();
                    if (over.contains("}")) {
                        //解析
                        JSONObject obj = new JSONObject(over);
                        // String test =  obj.getString("tmp"); //test
                        String current =  obj.getString("now_tmp"); //当前体温
                        String max = obj.getString("max_tmp"); //最高体温
                        String min = obj.getString("min_tmp");
                        if (!TextUtils.isEmpty(current)) {
                            tv_current_temperature.setText(current);
                        } else {
                            tv_current_temperature.setText("未知");
                        }
                        if (!TextUtils.isEmpty(max)) {
                            tv_max_temperature.setText(max);
                        } else {
                            tv_max_temperature.setText("未知");
                        }

                        if (!TextUtils.isEmpty(min)) {
                            tv_min_temperature.setText(min);
                        } else {
                            tv_min_temperature.setText("未知");
                        }

                    }


                }

            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),"数据源格式出错",Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }

        }

    };


    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.tv_rcry_connect:
                tv_rcry_connect.setEnabled(false);
                tv_rcry_connect.setText("搜所中");
                checkBluetoothStatus();
                break;
        }
    }
}
