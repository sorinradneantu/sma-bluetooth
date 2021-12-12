package ro.upt.sma.blechat

import android.content.Context.BLUETOOTH_SERVICE

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.Charset
import java.util.Arrays
import java.util.HashSet


/**
 * The BLE communication skeleton was provided by an Android Things sample:
 * https://github.com/androidthings/sample-bluetooth-le-gattserver
 */

class BleGattServerWrapper(private val context: Context) {

    private var chatListener: ChatListener? = null

    private val bluetoothManager: BluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    private var bluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothLeAdvertiser: BluetoothLeAdvertiser? = null

    private val registeredDevices = HashSet<BluetoothDevice>()

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private val mBluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)

            when (state) {
                BluetoothAdapter.STATE_ON -> {
                    startAdvertising()
                    startServer()
                }
                BluetoothAdapter.STATE_OFF -> {
                    stopServer()
                    stopAdvertising()
                }
            }// Do nothing

        }
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private val mAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "LE Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "LE Advertise Failed: $errorCode")
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private val mGattServerCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BluetoothDevice CONNECTED: $device")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "BluetoothDevice DISCONNECTED: $device")
                //Remove device from any active subscriptions
                registeredDevices.remove(device)
            }
        }

        override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
        ) {
            if (ChatProfile.CHAT_POST_CHARACTERISTIC == characteristic.uuid) {
                notifyRegisteredDevices(value)
                chatListener!!
                        .onMessageAdded(device.address, String(value, Charset.defaultCharset()))
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic write: " + characteristic.uuid)
                bluetoothGattServer!!.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null)
            }
        }

        override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic
        ) {
            // TODO: Send all messages
            if (ChatProfile.CHAT_ROOM_CHARACTERISTIC == characteristic.uuid) {
                bluetoothGattServer!!.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        byteArrayOf())
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.uuid)
                bluetoothGattServer!!.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0, null)
            }
        }

        override fun onDescriptorReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                descriptor: BluetoothGattDescriptor
        ) {
            if (ChatProfile.CLIENT_CONFIG == descriptor.uuid) {
                Log.d(TAG, "Config descriptor read")
                val returnValue: ByteArray
                if (registeredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
                bluetoothGattServer!!.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue)
            } else {
                Log.w(TAG, "Unknown descriptor read request")
                bluetoothGattServer!!.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0, null)
            }
        }

        override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
        ) {
            if (ChatProfile.CLIENT_CONFIG == descriptor.uuid) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: $device")
                    registeredDevices.add(device)
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: $device")
                    registeredDevices.remove(device)
                }

                if (responseNeeded) {
                    bluetoothGattServer!!.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0, null)
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request")
                if (responseNeeded) {
                    bluetoothGattServer!!.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0, null)
                }
            }
        }
    }

    interface ChatListener {

        fun onMessageAdded(sender: String, message: String)

        fun onError()

    }

    fun registerListener(chatListener: ChatListener) {
        this.chatListener = chatListener

        val bluetoothAdapter = bluetoothManager.adapter

        if (!checkBluetoothSupport(bluetoothAdapter)) {
            chatListener.onError()
        }

        // Register for system Bluetooth events
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        context.registerReceiver(mBluetoothReceiver, filter)

        if (!bluetoothAdapter.isEnabled) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling")
            bluetoothAdapter.enable()
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services")
            startAdvertising()
            startServer()
        }
    }

    fun unregisterListener() {
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter.isEnabled) {
            stopServer()
            stopAdvertising()
        }

        context.unregisterReceiver(mBluetoothReceiver)
    }


    /**
     * Verify the level of Bluetooth support provided by the hardware.
     *
     * @param bluetoothAdapter System [BluetoothAdapter].
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private fun checkBluetoothSupport(bluetoothAdapter: BluetoothAdapter?): Boolean {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported")
            return false
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported")
            return false
        }

        return true
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    private fun startAdvertising() {
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (bluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser")
            return
        }

        val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build()

        val data = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(ParcelUuid(ChatProfile.CHAT_SERVICE))
                .build()

        bluetoothLeAdvertiser!!
                .startAdvertising(settings, data, mAdvertiseCallback)
    }

    /**
     * Stop Bluetooth advertisements.
     */
    private fun stopAdvertising() {
        if (bluetoothLeAdvertiser == null) {
            return
        }

        bluetoothLeAdvertiser!!.stopAdvertising(mAdvertiseCallback)
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    private fun startServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(context, mGattServerCallback)
        if (bluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server")
            return
        }

        bluetoothGattServer!!.addService(ChatProfile.createChatService())
    }

    /**
     * Shut down the GATT server.
     */
    private fun stopServer() {
        if (bluetoothGattServer == null) {
            return
        }

        bluetoothGattServer!!.close()
    }

    /**
     * Send a time service notification to any devices that are subscribed
     * to the characteristic.
     */
    private fun notifyRegisteredDevices(receivedMessage: ByteArray) {
        if (registeredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered")
            return
        }

        Log.i(TAG, "Sending update to " + registeredDevices.size + " subscribers")
        for (device in registeredDevices) {
            val timeCharacteristic = bluetoothGattServer!!
                    .getService(ChatProfile.CHAT_SERVICE)
                    .getCharacteristic(ChatProfile.CHAT_ROOM_CHARACTERISTIC)
            timeCharacteristic.value = receivedMessage
            bluetoothGattServer!!.notifyCharacteristicChanged(device, timeCharacteristic, false)
        }
    }

    companion object {

        private val TAG = BleGattServerWrapper::class.java.simpleName
    }

}
