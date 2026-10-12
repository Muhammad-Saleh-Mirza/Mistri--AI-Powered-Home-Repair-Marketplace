package com.example.fixup.customer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.fixup.R
import com.example.fixup.databinding.ActivityCompletionReviewBinding
import com.example.fixup.utils.Constants
import com.example.fixup.utils.LocaleHelper
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Shown to the customer when a job is completed: displays the vendor's completion photo,
 * then continues to payment (if unpaid) or straight to rating.
 */
class CompletionReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompletionReviewBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var requestId: String

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletionReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestId = intent.getStringExtra("requestId") ?: run { finish(); return }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onStart() {
        super.onStart()
        loadRequest()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // Reloaded in onStart so the button reflects payment made on the next screen.
    private fun loadRequest() {
        binding.progressBar.visibility   = View.VISIBLE
        binding.layoutContent.visibility = View.GONE

        db.collection(Constants.COLLECTION_REQUESTS).document(requestId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) { finish(); return@addOnSuccessListener }
                binding.progressBar.visibility   = View.GONE
                binding.layoutContent.visibility = View.VISIBLE

                val vendorId   = doc.getString("acceptedVendorId").orEmpty()
                val vendorName = doc.getString("acceptedVendorName").orEmpty().ifEmpty { "Vendor" }
                val amount     = doc.getLong("acceptedBidAmount")?.toInt() ?: 0
                val isPaid     = doc.getBoolean("isPaid") ?: false
                val photo      = doc.getString("completionPhotoUrl").orEmpty()

                binding.tvJobTitle.text   = doc.getString("title").orEmpty()
                binding.tvVendorDone.text = getString(R.string.review_vendor_done, vendorName)

                if (photo.isNotEmpty()) {
                    binding.tvNoPhoto.visibility = View.GONE
                    Glide.with(this).load(photo).fitCenter().into(binding.ivCompletionPhoto)
                } else {
                    binding.ivCompletionPhoto.setImageDrawable(null)
                    binding.tvNoPhoto.visibility = View.VISIBLE
                }

                binding.btnContinue.text = getString(
                    if (isPaid) R.string.review_btn_rate else R.string.review_btn_pay
                )
                binding.btnContinue.setOnClickListener {
                    if (isPaid) {
                        startActivity(Intent(this, RateVendorActivity::class.java).apply {
                            putExtra("requestId", requestId)
                            putExtra("vendorId",  vendorId)
                        })
                    } else {
                        startActivity(Intent(this, PaymentActivity::class.java).apply {
                            putExtra("requestId",  requestId)
                            putExtra("vendorId",   vendorId)
                            putExtra("vendorName", vendorName)
                            putExtra("amount",     amount)
                        })
                    }
                }
            }
            .addOnFailureListener { e ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
