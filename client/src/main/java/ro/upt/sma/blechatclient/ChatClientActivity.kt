package ro.upt.sma.blechatclient

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_chat.*
import ro.upt.sma.blechatclient.BleGattClientWrapper.MessageListener

class ChatClientActivity : AppCompatActivity(), MessageListener {

    private lateinit var bleWrapper: BleGattClientWrapper

    private lateinit var chatListAdapter: ChatRecyclerViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)


        rv_chat_room_list.apply {
            layoutManager = LinearLayoutManager(this@ChatClientActivity)
            this@ChatClientActivity.chatListAdapter = ChatRecyclerViewAdapter()
            adapter = chatListAdapter
        }

        this.bleWrapper = BleGattClientWrapper(this)
    }

    override fun onResume() {
        super.onResume()

        bt_chat_connectivity_action.setOnClickListener { view ->
            if (!et_chat_address.text.toString().isEmpty()) {
                view.isEnabled = false
                bleWrapper.registerMessageListener(
                        et_chat_address.text.toString().toUpperCase(), this@ChatClientActivity)
            }
        }

        bt_chat_message_send.setOnClickListener {
            if (!et_chat_message.text.toString().isEmpty()) {
                bleWrapper.sendMessage(et_chat_message.text.toString(), this@ChatClientActivity)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        bleWrapper.unregisterListener()
    }

    override fun onMessageAdded(message: String) {
        Log.d(TAG, "onMessageAdded: $message")

        runOnUiThread { chatListAdapter!!.addMessage(message) }
    }

    override fun onMessageSent(message: String) {
        Log.d(TAG, "onMessageSent: $message")
    }

    override fun onConnectionStateChanged(connectionStatus: MessageListener.ConnectionStatus) {
        Log.d(TAG, "onConnectionStateChanged: $connectionStatus")

        runOnUiThread {
            if (connectionStatus === BleGattClientWrapper.MessageListener.ConnectionStatus.CONNECTED) {
                bt_chat_connectivity_action.isEnabled = false
                tv_chat_connection_status.setText(R.string.connected)
                bt_chat_message_send.isEnabled = true
            } else {
                bt_chat_connectivity_action.isEnabled = true
                tv_chat_connection_status.setText(R.string.disconnected)
                bt_chat_message_send.isEnabled = false
            }
        }
    }

    override fun onError(message: String) {
        Log.e(TAG, "onError: $message")

        runOnUiThread { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    companion object {

        private val TAG = ChatClientActivity::class.java.simpleName
    }

}
