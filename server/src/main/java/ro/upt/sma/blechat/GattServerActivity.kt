package ro.upt.sma.blechat

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import kotlinx.android.synthetic.main.activity_gatt_server.*
import ro.upt.sma.blechat.BleGattServerWrapper.ChatListener


class GattServerActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatRecyclerViewAdapter

    private lateinit var bleWrapper: BleGattServerWrapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gatt_server)

        rv_chat_list.apply {
            layoutManager = LinearLayoutManager(this@GattServerActivity)
            this@GattServerActivity.chatAdapter = ChatRecyclerViewAdapter()
            adapter = chatAdapter
        }

        this.bleWrapper = BleGattServerWrapper(this)
        bleWrapper.registerListener(object : ChatListener {

            override fun onMessageAdded(sender: String, message: String) {
                Log.d(TAG, "onMessageAdded: $message")
                addToChatList(sender, message)
            }

            override fun onError() {
                finish()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()

        bleWrapper.unregisterListener()
    }

    private fun addToChatList(name: String, message: String) {
        runOnUiThread { chatAdapter.addMessage("$name: $message") }
    }

    companion object {
        private val TAG = GattServerActivity::class.java.simpleName
    }

}
