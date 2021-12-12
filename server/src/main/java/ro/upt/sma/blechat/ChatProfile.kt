package ro.upt.sma.blechat


import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import java.util.UUID

object ChatProfile {

    val CHAT_SERVICE : UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    val CHAT_ROOM_CHARACTERISTIC: UUID = UUID.fromString("0000000a-0000-1000-8000-00805f9b34fb")
    val CHAT_POST_CHARACTERISTIC: UUID = UUID.fromString("0000000b-0000-1000-8000-00805f9b34fb")

    /* Mandatory Client Characteristic Config Descriptor */
    var CLIENT_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


    fun createChatService(): BluetoothGattService {
        val service = BluetoothGattService(CHAT_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val roomCharacteristic = BluetoothGattCharacteristic(
                CHAT_ROOM_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ)

        val configDescriptor = BluetoothGattDescriptor(CLIENT_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE)
        roomCharacteristic.addDescriptor(configDescriptor)

        val postCharacteristic = BluetoothGattCharacteristic(
                CHAT_POST_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE)

        service.addCharacteristic(roomCharacteristic)
        service.addCharacteristic(postCharacteristic)

        return service
    }

}
