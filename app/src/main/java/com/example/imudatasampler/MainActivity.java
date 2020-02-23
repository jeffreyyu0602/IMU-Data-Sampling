package com.example.imudatasampler;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_CONFIGURING_CONNECTION_INTERVAL = 3;
    private static final int STATE_STARTING_DATA_SAMPLING = 4;
    private static final int STATE_SUBSCRIBING_TO_BLE_NOTIF = 5;
    private static final int STATE_SAMPLING = 6;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_ID_MULTIPLE_PERMISSIONS = 85;

    private TextView mConnectionState;
    private int connectionState = STATE_DISCONNECTED;

    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mSamplingCharacteristic;

    private SensorManager sensorManager;
    private final float[] accelerometerReading = new float[3];
    private final float[] magnetometerReading = new float[3];
    private final float[] gyroscopeReading = new float[3];

    private ByteArrayOutputStream mByteArrayOutputStream;
    private ByteArrayOutputStream mAccelerometerByteArray;
    private ByteArrayOutputStream mMagnetometerByteArray;
    private ByteArrayOutputStream mGyroscopeByteArray;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
            connectionState = STATE_DISCONNECTED;
            updateConnectionState(R.string.disconnected);
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "BLE device connected.");
                updateConnectionState(R.string.connected);
                connectionState = STATE_CONNECTED;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "BLE device disconnected.");
                updateConnectionState(R.string.disconnected);
                connectionState = STATE_DISCONNECTED;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Parsing BLE characteristics.");
                parseGattServices(mBluetoothLeService.getSupportedGattServices());
                writeCharacteristics();
            } else if (BluetoothLeService.ACTION_GATT_CHARACTERISTIC_CHANGED.equals(action)) {
                Log.d(TAG, "BLE characteristics changed.");
                writeCharacteristics();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                byte[] value = intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA);
                mByteArrayOutputStream.write(value, 0, value.length);
                if (mByteArrayOutputStream.size() >= 1024 * 1024 * 100) {
                    writeToFile();
                }
//                Log.d(TAG, ((value[4] << 8) | (value[5] & 0x00ff)) + " "
//                        + ((value[6] << 8) | (value[7] & 0x00ff)) + " "
//                        + ((value[8] << 8) | (value[9] & 0x00ff)) + " "
//                        + ((value[10] << 8) | (value[11] & 0x00ff)) + " "
//                        + ((value[12] << 8) | (value[13] & 0x00ff)) + " "
//                        + ((value[14] << 8) | (value[15] & 0x00ff)));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button button = findViewById(R.id.sample_button);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (mBluetoothLeService != null) {
                    mBluetoothLeService.close();
                    if (connectionState == STATE_DISCONNECTED) {
                        final boolean result = mBluetoothLeService.connect(
                                ESenseGattAttributes.DEVICE_ADDRESS);
                        Log.d(TAG, "Connect request result=" + result);
                        registerListeners();
                    } else {
                        connectionState = STATE_DISCONNECTED;
                        updateConnectionState(R.string.disconnected);
                        unregisterListeners();
                        writeToFile();
                    }
                }
            }
        });

        // Sets up UI reference.
        mConnectionState = findViewById(R.id.connection_state);

        // Create an output stream in which the data is written into a byte array.
        mByteArrayOutputStream = new ByteArrayOutputStream();
        mAccelerometerByteArray = new ByteArrayOutputStream();
        mGyroscopeByteArray = new ByteArrayOutputStream();
        mMagnetometerByteArray = new ByteArrayOutputStream();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        List<String> listPermissionsNeeded = new ArrayList<>();
        // Check if the app can access precise location. Require permission if not.
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                    REQUEST_ID_MULTIPLE_PERMISSIONS);
        }

        // Bind service.
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        boolean result = bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        // Initialize a SensorManager and get different sensors.
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do something here if sensor accuracy changes.
        // You must implement this callback in your code.
    }

    // Get readings from accelerometer and magnetometer. To simplify calculations,
    // consider storing these readings as unit vectors.
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading,
                    0, accelerometerReading.length);
            for (float f : accelerometerReading) {
                try {
                    mAccelerometerByteArray.write(float2ByteArray(f));
                } catch (IOException e) {
                    Log.e(TAG, e.getStackTrace().toString());
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading,
                    0, magnetometerReading.length);
            for (float f : magnetometerReading) {
                try {
                    mMagnetometerByteArray.write(float2ByteArray(f));
                } catch (IOException e) {
                    Log.e(TAG, e.getStackTrace().toString());
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyroscopeReading,
                    0, gyroscopeReading.length);
            for (float f : gyroscopeReading) {
                try {
                    mGyroscopeByteArray.write(float2ByteArray(f));
                } catch (IOException e) {
                    Log.e(TAG, e.getStackTrace().toString());
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        // Don't receive any more updates from either sensor.
        unregisterListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CHARACTERISTIC_CHANGED);
        return intentFilter;
    }

    private void parseGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                String uuid = gattCharacteristic.getUuid().toString();
                if (ESenseGattAttributes.DATA_RECEIVER_CHARACTERISTIC.equals(uuid)) {
                    mNotifyCharacteristic = gattCharacteristic;
                }
                if (ESenseGattAttributes.DATA_SAMPLING_CHARACTERISTIC.equals(uuid)) {
                    mSamplingCharacteristic = gattCharacteristic;
                }
            }
        }
    }

    private void writeCharacteristics() {
        switch (connectionState) {
            case STATE_CONNECTED:
                mSamplingCharacteristic.setValue(ESenseGattAttributes.CONNECTION_INTERVAL_COMMAND);
                mBluetoothLeService.writeCharacteristic(mSamplingCharacteristic);
                updateConnectionState(R.string.configuring_sampling);
                connectionState = STATE_CONFIGURING_CONNECTION_INTERVAL;
                break;
            case STATE_CONFIGURING_CONNECTION_INTERVAL:
                mSamplingCharacteristic.setValue(ESenseGattAttributes.START_DATA_SAMPLING_COMMAND);
                mBluetoothLeService.writeCharacteristic(mSamplingCharacteristic);
                updateConnectionState(R.string.starting_sampling);
                connectionState = STATE_STARTING_DATA_SAMPLING;
                break;
            case STATE_STARTING_DATA_SAMPLING:
                mBluetoothLeService.setCharacteristicNotification(mNotifyCharacteristic, true);
                updateConnectionState(R.string.subscribing_to_notification);
                connectionState = STATE_SUBSCRIBING_TO_BLE_NOTIF;
                break;
            case STATE_SUBSCRIBING_TO_BLE_NOTIF:
                updateConnectionState(R.string.sampling);
                connectionState = STATE_SAMPLING;
                break;
        }
    }

    private void writeToFile() {
        Log.d(TAG, "Save data to local storage.");
        long time = System.currentTimeMillis();
        File path = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, "eSense_imu_" + time + ".txt");
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(mByteArrayOutputStream.toByteArray());
            mByteArrayOutputStream.reset();
            fos.close();

            file = new File(path, "accelerometer_" + time + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            fos = new FileOutputStream(file);
            fos.write(mAccelerometerByteArray.toByteArray());
            mAccelerometerByteArray.reset();
            fos.close();

            file = new File(path, "gyroscope_" + time + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            fos = new FileOutputStream(file);
            fos.write(mGyroscopeByteArray.toByteArray());
            mGyroscopeByteArray.reset();
            fos.close();

            file = new File(path, "magnetometer_" + time + ".txt");
            if (!file.exists()) {
                file.createNewFile();
            }
            fos = new FileOutputStream(file);
            fos.write(mMagnetometerByteArray.toByteArray());
            mMagnetometerByteArray.reset();
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void registerListeners() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void unregisterListeners() {
        sensorManager.unregisterListener(this);
    }

    public static byte [] float2ByteArray (float value) {
        return ByteBuffer.allocate(4).putFloat(value).array();
    }
}
