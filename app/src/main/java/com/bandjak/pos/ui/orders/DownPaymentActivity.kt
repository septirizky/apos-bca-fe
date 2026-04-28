package com.bandjak.pos.ui.orders

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bandjak.pos.api.ApiClient
import com.bandjak.pos.databinding.ActivityDownPaymentBinding
import com.bandjak.pos.model.DownPayment
import com.bandjak.pos.realtime.PosRealtimeSocket
import com.bandjak.pos.util.AutoRefreshTimer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class DownPaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownPaymentBinding
    private lateinit var adapter: DownPaymentAdapter

    private var allDownPayments: List<DownPayment> = listOf()
    private val autoRefreshTimer = AutoRefreshTimer {
        loadDownPayments()
    }
    private val realtimeListener: (String) -> Unit = { eventType ->
        if (eventType == "connected" || eventType == "down_payments_changed") {
            loadDownPayments()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)

        binding = ActivityDownPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = DownPaymentAdapter(listOf())

        binding.dpList.layoutManager =
            LinearLayoutManager(this)

        binding.dpList.adapter = adapter

        loadDownPayments()

        binding.searchInput.addTextChangedListener(object : TextWatcher {

            override fun afterTextChanged(s: Editable?) {

                val keyword = s.toString().lowercase()

                renderDownPayments(keyword)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    override fun onStart() {
        super.onStart()
        PosRealtimeSocket.addListener(realtimeListener)
        autoRefreshTimer.start()
    }

    override fun onStop() {
        autoRefreshTimer.stop()
        PosRealtimeSocket.removeListener(realtimeListener)
        super.onStop()
    }

    private fun loadDownPayments(){

        ApiClient.api.getDownPayments()
            .enqueue(object : Callback<List<DownPayment>> {

                override fun onResponse(
                    call: Call<List<DownPayment>>,
                    response: Response<List<DownPayment>>
                ) {

                    allDownPayments = response.body() ?: listOf()

                    renderDownPayments()
                }

                override fun onFailure(call: Call<List<DownPayment>>, t: Throwable) {
                    t.printStackTrace()
                }

            })
    }

    private fun renderDownPayments(keyword: String = binding.searchInput.text.toString().lowercase()) {
        if (keyword.isBlank()) {
            adapter.update(allDownPayments)
            return
        }

        val filtered = allDownPayments.filter {
            val name = it.name.lowercase()
            val contact = it.contact.orEmpty().lowercase()

            name.contains(keyword) || contact.contains(keyword)
        }

        adapter.update(filtered)
    }
}
