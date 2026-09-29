package com.example.fixup.shared

import android.content.Context
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fixup.R
import com.example.fixup.databinding.ActivityChatbotBinding
import com.example.fixup.databinding.ItemChatbotMessageBinding
import com.example.fixup.utils.Constants
import com.example.fixup.utils.LocaleHelper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class ChatbotActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatbotBinding
    private val client = OkHttpClient()

    private val messages = mutableListOf<ChatMessage>()
    private val history  = mutableListOf<Pair<String, String>>()
    private lateinit var adapter: ChatbotAdapter

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val isTyping: Boolean = false
    )

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.contentLayout) { view, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(0, 0, 0, maxOf(sys.bottom, ime.bottom))
            insets
        }

        val lm = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recyclerMessages.layoutManager = lm
        adapter = ChatbotAdapter(messages)
        binding.recyclerMessages.adapter = adapter

        binding.btnBotBack.setOnClickListener { finish() }
        binding.btnBotInfo.setOnClickListener {
            android.widget.Toast.makeText(this, "MistriBot helps with home repair in Pakistan 🏠", android.widget.Toast.LENGTH_SHORT).show()
        }
        binding.btnSend.setOnClickListener { sendMessage() }

        setupChips()
        setupWelcomeCards()
    }

    private var firstMessageSent = false

    private fun showConversation() {
        if (!firstMessageSent) {
            firstMessageSent = true
            binding.layoutWelcome.visibility = View.GONE
            binding.recyclerMessages.visibility = View.VISIBLE
        }
    }

    // ── Welcome & chips ────────────────────────────────────────────────────────

    private fun setupWelcomeCards() {
        binding.cardSuggestEstimate.setOnClickListener  { sendChip(getString(R.string.chip_estimate)) }
        binding.cardSuggestFairPrice.setOnClickListener { sendChip(getString(R.string.chip_fair_price)) }
        binding.cardSuggestService.setOnClickListener   { sendChip(getString(R.string.chip_service)) }
    }

    private fun setupChips() {
        binding.chipEstimate.setOnClickListener  { sendChip(getString(R.string.chip_estimate)) }
        binding.chipFairPrice.setOnClickListener { sendChip(getString(R.string.chip_fair_price)) }
        binding.chipService.setOnClickListener   { sendChip(getString(R.string.chip_service)) }
    }

    private fun sendChip(text: String) {
        hideChips()
        processUserMessage(text)
    }

    private fun hideChips() {
        binding.scrollChips.visibility = View.GONE
    }

    // ── Send ───────────────────────────────────────────────────────────────────

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return
        binding.etMessage.setText("")
        hideChips()
        processUserMessage(text)
    }

    private fun processUserMessage(text: String) {
        showConversation()
        addUserMessage(text)
        history.add(Pair("user", text))
        showTyping()
        callFlask()
    }

    // ── Flask call ─────────────────────────────────────────────────────────────

    private fun callFlask() {
        val messagesJson = JSONArray().apply {
            for ((role, content) in history) {
                put(JSONObject().apply {
                    put("role", role)
                    put("content", content)
                })
            }
        }
        val body = JSONObject().apply {
            put("messages", messagesJson)
        }.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(Constants.FLASK_BASE_URL + Constants.FLASK_CHAT)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    removeTyping()
                    val err = "Sorry, I'm having trouble connecting. Please try again."
                    addBotMessage(err)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val reply = try {
                    JSONObject(response.body!!.string()).getString("reply")
                } catch (e: Exception) {
                    "Sorry, I'm having trouble connecting. Please try again."
                }
                runOnUiThread {
                    removeTyping()
                    addBotMessage(reply)
                    history.add(Pair("assistant", reply))
                }
            }
        })
    }

    // ── Message helpers ────────────────────────────────────────────────────────

    private fun addUserMessage(text: String) {
        messages.add(ChatMessage(text = text, isUser = true))
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun addBotMessage(text: String) {
        messages.add(ChatMessage(text = text, isUser = false))
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun showTyping() {
        binding.tvBotStatus.text = "Typing…"
        messages.add(ChatMessage(text = "", isUser = false, isTyping = true))
        adapter.notifyItemInserted(messages.size - 1)
        scrollToBottom()
    }

    private fun removeTyping() {
        binding.tvBotStatus.text = "Ready to help"
        val idx = messages.indexOfLast { it.isTyping }
        if (idx >= 0) {
            messages.removeAt(idx)
            adapter.notifyItemRemoved(idx)
        }
    }

    private fun scrollToBottom() {
        binding.recyclerMessages.post {
            if (messages.isNotEmpty())
                binding.recyclerMessages.scrollToPosition(messages.size - 1)
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private inner class ChatbotAdapter(
        private val items: List<ChatMessage>
    ) : RecyclerView.Adapter<ChatbotAdapter.VH>() {

        private val dotColor = Color.parseColor("#94A3B8")

        inner class VH(val b: ItemChatbotMessageBinding) : RecyclerView.ViewHolder(b.root) {
            var dotAnimSet: AnimatorSet? = null
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemChatbotMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = items[position]
            holder.dotAnimSet?.cancel()
            holder.dotAnimSet = null

            if (msg.isUser) {
                holder.b.layoutBot.visibility  = View.GONE
                holder.b.layoutUser.visibility = View.VISIBLE
                holder.b.tvUserMessage.text    = msg.text
            } else {
                holder.b.layoutUser.visibility = View.GONE
                holder.b.layoutBot.visibility  = View.VISIBLE

                if (msg.isTyping) {
                    holder.b.tvBotMessage.visibility  = View.GONE
                    holder.b.layoutTyping.visibility  = View.VISIBLE
                    styleDots(holder.b)
                    holder.dotAnimSet = startDotAnimation(holder.b)
                } else {
                    holder.b.layoutTyping.visibility  = View.GONE
                    holder.b.tvBotMessage.visibility  = View.VISIBLE
                    holder.b.tvBotMessage.text        = msg.text
                }
            }
        }

        override fun onViewRecycled(holder: VH) {
            super.onViewRecycled(holder)
            holder.dotAnimSet?.cancel()
            holder.dotAnimSet = null
        }

        private fun styleDots(b: ItemChatbotMessageBinding) {
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
            b.dot1.background = dotBg
            b.dot2.background = dotBg.constantState!!.newDrawable().mutate()
            b.dot3.background = dotBg.constantState!!.newDrawable().mutate()
        }

        private fun startDotAnimation(b: ItemChatbotMessageBinding): AnimatorSet {
            fun bounceAnim(v: View, startDelay: Long): ObjectAnimator =
                ObjectAnimator.ofFloat(v, "translationY", 0f, -14f, 0f).apply {
                    duration         = 600
                    this.startDelay  = startDelay
                    repeatCount      = ObjectAnimator.INFINITE
                    interpolator     = AccelerateDecelerateInterpolator()
                }

            return AnimatorSet().also { set ->
                set.playTogether(
                    bounceAnim(b.dot1, 0L),
                    bounceAnim(b.dot2, 150L),
                    bounceAnim(b.dot3, 300L)
                )
                set.start()
            }
        }
    }
}
