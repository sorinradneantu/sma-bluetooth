package ro.upt.sma.blechatclient

import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothGattCharacteristic.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import java.nio.charset.Charset
import java.util.*

class BleGattClientWrapper(private val context: Context) {

    private val bluetoothManager: BluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager

    private var gatt: BluetoothGatt? = null

    interface MessageListener {

        enum class ConnectionStatus {
            CONNECTED, DISCONNECTED
        }

        fun onMessageAdded(message: String)

        fun onMessageSent(message: String)

        fun onConnectionStateChanged(connectionStatus: ConnectionStatus)

        fun onError(message: String)

    }

    fun sendMessage(message: String, messageListener: MessageListener) {
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            messageListener.onError("Bluetooth not enable or available.")
            return
        }

        val bluetoothDevice = gatt!!.device

        if (bluetoothDevice == null) {
            messageListener.onError("Bluetooth device not available")
            return
        }

        if (gatt == null && gatt!!.getConnectionState(bluetoothDevice) != BluetoothAdapter.STATE_CONNECTED) {
            messageListener.onError("Bluetooth device not connected")
            return
        }

        val characteristic = gatt!!.getService(CHAT_SERVICE)
                .getCharacteristic(CHAT_POST_CHARACTERISTIC)

        characteristic.value = message.toByteArray(Charset.defaultCharset())

        val success = gatt!!.writeCharacteristic(characteristic)

        if (success) {
            messageListener.onMessageSent(message)
        } else {
            messageListener.onError("Message was not sent")
        }
    }

    fun registerMessageListener(address: String, messageListener: MessageListener) {
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            messageListener.onError("Bluetooth not enable or available.")
            return
        }

        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(address)

        if (bluetoothDevice == null) {
            messageListener.onError("Bluetooth device not available")
            return
        }

        this.gatt = bluetoothDevice.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)

                if (newState == STATE_CONNECTED) {
                    //  Once connection was established then trigger service discovery.

                    gatt.discoverServices()

                    //  Inform the message listener about CONNECTED value status.

                    messageListener.onConnectionStateChanged(MessageListener.ConnectionStatus.CONNECTED)

                } else {
                    gatt.close()
                    // Inform the message listener about DISCONNECTED value status.

                    messageListener.onConnectionStateChanged(MessageListener.ConnectionStatus.DISCONNECTED)

                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)

                // Acquire a handle for the CHAT_ROOM_CHARACTERISTIC.

                /*

                val characteristic = BluetoothGattCharacteristic(CHAT_ROOM_CHARACTERISTIC,
                    PROPERTY_WRITE or PROPERTY_READ,
                    PERMISSION_WRITE or PERMISSION_READ)

                 */
                val characteristic: BluetoothGattCharacteristic = gatt.getService(CHAT_SERVICE).getCharacteristic(CHAT_ROOM_CHARACTERISTIC)
                gatt.setCharacteristicNotification(characteristic, true)

                // Get and enable the CLIENT_CONFIG descriptor for the same characteristic.

                val clientConfigDescriptor = characteristic.getDescriptor(CLIENT_CONFIG)

                // Write descriptor to GATT handle.

                gatt.writeDescriptor(clientConfigDescriptor)

            }

            override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic) {
                super.onCharacteristicChanged(gatt, characteristic)

                messageListener.onMessageAdded(
                        String(characteristic.value, Charset.defaultCharset()))
            }
        },  BluetoothDevice.TRANSPORT_LE)
    }

    fun unregisterListener() {
        if (gatt != null) {
            gatt!!.disconnect()
        }
    }

    companion object {

        private val TAG = BleGattClientWrapper::class.java.simpleName

        val CHAT_SERVICE: UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        val CHAT_ROOM_CHARACTERISTIC: UUID = UUID.fromString("0000000a-0000-1000-8000-00805f9b34fb")
        val CHAT_POST_CHARACTERISTIC: UUID = UUID.fromString("0000000b-0000-1000-8000-00805f9b34fb")

        /* Mandatory Client Characteristic Config Descriptor */
        var CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

}
