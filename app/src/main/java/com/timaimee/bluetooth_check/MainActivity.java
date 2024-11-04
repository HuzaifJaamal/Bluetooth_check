package com.timaimee.bluetooth_check;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

//import android.support.v7.app.AppCompatActivity;
//import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.inuker.bluetooth.library.Code;
import com.inuker.bluetooth.library.Constants;
import com.inuker.bluetooth.library.model.BleGattProfile;
import com.inuker.bluetooth.library.search.SearchResult;
import com.inuker.bluetooth.library.search.response.SearchResponse;
import com.inuker.bluetooth.library.utils.BluetoothUtils;
import com.orhanobut.logger.LogLevel;
import com.orhanobut.logger.Logger;
import com.veepoo.protocol.VPOperateManager;
import com.veepoo.protocol.listener.base.IABleConnectStatusListener;
import com.veepoo.protocol.listener.base.IABluetoothStateListener;
import com.veepoo.protocol.listener.base.IBleWriteResponse;
import com.veepoo.protocol.listener.base.IConnectResponse;
import com.veepoo.protocol.listener.base.INotifyResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import com.veepoo.protocol.listener.data.IBPDetectDataListener;

import com.veepoo.protocol.listener.data.ISportDataListener;
import com.veepoo.protocol.model.datas.BpData;

import com.veepoo.protocol.model.datas.SportData;
import com.veepoo.protocol.model.enums.EBPDetectModel;


public class MainActivity extends AppCompatActivity {

    VPOperateManager mVpoperateManager;         //for veepoo protocol

    private final static String TAG = MainActivity.class.getSimpleName();
    private final static String YOUR_APPLICATION = "timaimee";

    List<SearchResult> mListData = new ArrayList<>();
    List<String> mListAddress = new ArrayList<>();

    TextView infoText;
    String deviceMac = "";

    WriteResponse writeResponse = new WriteResponse();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        initLog();
        Logger.t(TAG).i("onSearchStarted");
        mVpoperateManager = mVpoperateManager.getMangerInstance(getApplicationContext());

        infoText = (TextView)findViewById(R.id.InfoView);
        registerBluetoothStateListener();
    }

    private void initLog() {
        Logger.init(YOUR_APPLICATION)
                .methodCount(0)
                .methodOffset(0)
                .hideThreadInfo()
                .logLevel(LogLevel.FULL)
                .logAdapter(new CustomLogAdapter());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean scanDevice() {

        if (!mListAddress.isEmpty()) {
            mListAddress.clear();
        }
        if (!mListData.isEmpty()) {
            mListData.clear();
            Log.e("is empty", "ScanDevice");
        }

        if (!BluetoothUtils.isBluetoothEnabled()) {
            Toast.makeText(getApplicationContext(), "蓝牙没有开启", Toast.LENGTH_SHORT).show();
            return true;
        }
        mVpoperateManager.startScanDevice(mSearchResponse);
        return false;
    }

    /**
     * 蓝牙打开or关闭状态
     */
    private void registerBluetoothStateListener() {
        mVpoperateManager.registerBluetoothStateListener(mBluetoothStateListener);
    }


    /**
     * 监听系统蓝牙的打开和关闭的回调状态
     */
    private final IABleConnectStatusListener mBleConnectStatusListener = new IABleConnectStatusListener() {

        @Override
        public void onConnectStatusChanged(String mac, int status) {
            if (status == Constants.STATUS_CONNECTED) {
                Logger.t(TAG).i("STATUS_CONNECTED");
            } else if (status == Constants.STATUS_DISCONNECTED) {
                Logger.t(TAG).i("STATUS_DISCONNECTED");
            }
        }
    };

    /**
     * 监听蓝牙与设备间的回调状态
     */
    private final IABluetoothStateListener mBluetoothStateListener = new IABluetoothStateListener() {
        @Override
        public void onBluetoothStateChanged(boolean openOrClosed) {
            Logger.t(TAG).i("open=" + openOrClosed);
        }
    };

    private final SearchResponse mSearchResponse = new SearchResponse() {
        @Override
        public void onSearchStarted() {
            Logger.t(TAG).i("onSearchStarted");
        }

        @Override
        public void onDeviceFounded(final SearchResult device) {
            Logger.t(TAG).i(String.format("**device for %s-%s-%d", device.getName(), device.getAddress(), device.rssi));
            deviceMac = device.getAddress();   //"BL-P1-FE:BA:1A:D1:CC:93"; ->有可能會有多個Device 有需要可以建List讓User選
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!mListAddress.contains(device.getAddress())) {
                        mListData.add(device);
                        mListAddress.add(device.getAddress());
                    }
                    Collections.sort(mListData, new DeviceCompare());

                }
            });
        }

        @Override
        public void onSearchStopped() {
            Logger.t(TAG).i("onSearchStopped");
        }

        @Override
        public void onSearchCanceled() {
            Logger.t(TAG).i("onSearchCanceled");
        }
    };

    private void connectDevice(final String mac) {

        mVpoperateManager.registerConnectStatusListener(mac, mBleConnectStatusListener);

        mVpoperateManager.connectDevice(mac, new IConnectResponse() {

            @Override
            public void connectState(int code, BleGattProfile profile, boolean isoadModel) {
                if (code == Code.REQUEST_SUCCESS) {
                    //蓝牙与设备的连接状态
                    Logger.t(TAG).i("连接成功");

                } else {
                    Logger.t(TAG).i("连接失败");
                }
            }
        }, new INotifyResponse() {
            @Override
            public void notifyState(int state) {
                if (state == Code.REQUEST_SUCCESS) {
                    //蓝牙与设备的连接状态
                    Logger.t(TAG).i("监听成功-可进行其他操作");
                    infoText.setText("监听成功-可进行其他操作");
                    //startActivity(new Intent(mContext, OperaterActivity.class));
                } else {
                    Logger.t(TAG).i("监听失败，重新连接");
                    infoText.setText("监听失败-請重試");
                }

            }
        });
    }

    class WriteResponse implements IBleWriteResponse {

        @Override
        public void onResponse(int code) {
            Logger.t(TAG).i("write cmd status:" + code);

        }
    }

    public void onScanClick(View view) {
        Log.e("onScan Click", "ScanDevice");
        scanDevice();
    }

    public void onConnectDeviceClick(View view) {
        Log.e("onConnectDeviceClick", "conn Device");
        connectDevice(deviceMac);
    }

    public void onSetTimeClick(View view) {
        Log.e("onSetTimeClick", "set time ");

    }

    public void onWalkClick(View view) {
        VPOperateManager.getMangerInstance(getApplicationContext()).readSportStep(writeResponse, new ISportDataListener() {
            @Override
            public void onSportDataChange(SportData sportData) {
                String message = "当前计步:\n" + sportData.getStep();
                Logger.t(TAG).i(message);
                infoText.setText(message);
            }
        });
    }

    public void onDetectClick(View view) {
        infoText.setText("Monitoring please wait....");
        VPOperateManager.getMangerInstance(getApplicationContext()).startDetectBP(writeResponse, new IBPDetectDataListener() {
            @Override
            public void onDataChange(BpData bpData) {
                String message = "BpData date statues:\n" + bpData.toString();
                Logger.t(TAG).i(message);
                infoText.setText(message);
            }
        }, EBPDetectModel.DETECT_MODEL_PUBLIC);

    }

    public void onDetectStopClick(View view) {
        VPOperateManager.getMangerInstance(getApplicationContext()).stopDetectBP(writeResponse, EBPDetectModel.DETECT_MODEL_PUBLIC);
        infoText.setText("detect stop!!");
    }

}




/*
package com.timaimee.bluetooth_check;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 3;

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> arrayAdapter;
    private ArrayList<String> devicesList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ListView listView = findViewById(R.id.deviceListView);
        devicesList = new ArrayList<>();
        arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devicesList);
        listView.setAdapter(arrayAdapter);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        } else {
            startBluetooth();
        }
    }

    private void startBluetooth() {
        // Request Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions();
            } else {
                enableBluetooth();
            }
        } else {
            // For Android 11 and below
            enableBluetooth();
        }
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.BLUETOOTH_SCAN,
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void enableBluetooth() {
        try {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                startDeviceDiscovery();
            }
        } catch (SecurityException e) {
            Log.e("MainActivity", "Permission denied while enabling Bluetooth: " + e.getMessage());
            Toast.makeText(this, "Permission denied for enabling Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private void startDeviceDiscovery() {
        // Ensure permissions are granted before starting device discovery
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            scanBluetoothDevices();
        } else {
            requestBluetoothPermissions();
        }
    }

    private void scanBluetoothDevices() {
        try {
            // Register BroadcastReceiver for Bluetooth device discovery
            IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
            registerReceiver(receiver, filter);

            // Start discovery
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                bluetoothAdapter.startDiscovery();
                Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show();
            }
        } catch (SecurityException e) {
            Log.e("MainActivity", "Permission denied during Bluetooth scan: " + e.getMessage());
            Toast.makeText(this, "Permission denied for Bluetooth scan", Toast.LENGTH_SHORT).show();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                try {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null) {
                        String deviceName = device.getName();
                        String deviceAddress = device.getAddress(); // MAC address
                        devicesList.add(deviceName + " - " + deviceAddress);
                        arrayAdapter.notifyDataSetChanged();
                    }
                } catch (SecurityException e) {
                    Log.e("MainActivity", "Permission denied when receiving device info: " + e.getMessage());
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.e("MainActivity", "Receiver was not registered: " + e.getMessage());
        }
        if (bluetoothAdapter != null) {
            try {
                bluetoothAdapter.cancelDiscovery();
            } catch (SecurityException e) {
                Log.e("MainActivity", "Permission denied when canceling discovery: " + e.getMessage());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetooth();
            } else {
                Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                startDeviceDiscovery();
            } else {
                Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}


*/
