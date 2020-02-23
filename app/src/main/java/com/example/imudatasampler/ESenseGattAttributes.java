/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.imudatasampler;

import java.util.HashMap;

/**
 * This class includes standard GATT attributes for eSense BLE device.
 */
public class ESenseGattAttributes {
    public static String DEVICE_ADDRESS = "00:04:79:00:0C:9B";
    private static HashMap<String, String> attributes = new HashMap();
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static String DATA_SAMPLING_CHARACTERISTIC = "0000ff07-0000-1000-8000-00805f9b34fb";
    public static String DATA_RECEIVER_CHARACTERISTIC = "0000ff08-0000-1000-8000-00805f9b34fb";

    public static byte[] START_DATA_SAMPLING_COMMAND = {0x53, 0x67, 0x02, 0x01, 0x64};
    public static byte[] STOP_DATA_SAMPLING_COMMAND = {0x53, 0x02, 0x02, 0x00, 0x00};
    public static byte[] CONNECTION_INTERVAL_COMMAND =
            {0x57, 0x78, 0x08, 0x00, (byte) 0xa0, 0x00, (byte) 0xa0, 0x00, 0x10, 0x00, 0x20};

    static {
        // Services.
        attributes.put("0000ff06-0000-1000-8000-00805f9b34fb", "BLE Service");
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "Device Information Service");
        // Characteristics.
        attributes.put(DATA_SAMPLING_CHARACTERISTIC, "Start/stop IMU sampling and configure advertisement and connection intervals");
        attributes.put(DATA_RECEIVER_CHARACTERISTIC, "Get sensor data when IMU sampling is enabled");
        attributes.put("0000ff09-0000-1000-8000-00805f9b34fb", "Get push button status");
        attributes.put("0000ff0a-0000-1000-8000-00805f9b34fb", "Get battery voltage");
        attributes.put("0000ff0b-0000-1000-8000-00805f9b34fb", "Get advertisement and connection intervals");
        attributes.put("0000ff0c-0000-1000-8000-00805f9b34fb", "Change device name");
        attributes.put("0000ff0d-0000-1000-8000-00805f9b34fb", "Get accelerometer factory offset");
        attributes.put("0000ff0e-0000-1000-8000-00805f9b34fb", "Get/set accelerometer and gyroscope configuration");
        attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "Get device name");
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
