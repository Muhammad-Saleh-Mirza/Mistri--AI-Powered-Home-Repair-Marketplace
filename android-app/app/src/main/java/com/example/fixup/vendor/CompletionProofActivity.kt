package com.example.fixup.vendor

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.example.fixup.R
import com.example.fixup.databinding.ActivityCompletionProofBinding
import com.example.fixup.utils.Constants
import com.example.fixup.utils.ImageUtils
import com.example.fixup.utils.LocaleHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException

/**
 * Vendor marks a job complete. A photo of the finished work is mandatory; it is stored
 * on the request as `completionPhotoUrl` so the customer can review it before paying/rating.
 * Finishes with RESULT_OK once the request has been marked completed.
 */
class CompletionProofActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompletionProofBinding
    private val db = FirebaseFirestore.getInstance()

    private lateinit var requestId: String
    private var photoDataUri: String? = null
    private var cameraImageUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraImageUri?.let { handlePicked(it) }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handlePicked(it) } }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(this, getString(R.string.proof_toast_camera_permission), Toast.LENGTH_LONG).show()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletionProofBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestId = intent.getStringExtra("requestId") ?: run { finish(); return }
        binding.tvJobTitle.text = intent.getStringExtra("requestTitle").orEmpty()

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.btnCamera.setOnClickListener { handleCameraClick() }
        binding.btnGallery.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSubmit.setOnClickListener { submit() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── Photo selection ────────────────────────────────────────────────────────

    private fun handleCameraClick() {
        val perm = Manifest.permission.CAMERA
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(perm)
        }
    }

    private fun launchCamera() {
        try {
            val dir = getExternalFilesDir("Pictures") ?: filesDir
            val imageFile = File.createTempFile("mistri_done_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", imageFile)
            cameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.proof_toast_photo_failed), Toast.LENGTH_SHORT).show()
        }
    }

    /** Downsizes and encodes the picked photo off the main thread, then shows the preview. */
    private fun handlePicked(uri: Uri) {
        setLoading(true)
        Thread {
            val encoded = ImageUtils.uriToDataUri(this, uri, 800, 60)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setLoading(false)
                if (encoded.isEmpty()) {
                    Toast.makeText(this, getString(R.string.proof_toast_photo_failed), Toast.LENGTH_SHORT).show()
                } else {
                    photoDataUri = encoded
                    binding.tvNoPhoto.visibility = View.GONE
                    Glide.with(this).load(encoded).centerCrop().into(binding.ivPreview)
                }
            }
        }.start()
    }

    // ── Submit ─────────────────────────────────────────────────────────────────

    private fun submit() {
        val photo = photoDataUri
        if (photo == null) {
            Toast.makeText(this, getString(R.string.proof_toast_need_photo), Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)
        db.collection(Constants.COLLECTION_REQUESTS).document(requestId)
            .update(
                mapOf(
                    "status"             to "completed",
                    "isVendorEnRoute"    to false,
                    "completedAt"        to FieldValue.serverTimestamp(),
                    "completionPhotoUrl" to photo
                )
            )
            .addOnSuccessListener {
                setLoading(false)
                setResult(Activity.RESULT_OK)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_job_complete_title))
                    .setMessage(getString(R.string.dialog_job_complete_msg))
                    .setPositiveButton(getString(R.string.dialog_ok)) { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
            .addOnFailureListener { e ->
                setLoading(false)
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled  = !loading
        binding.btnCamera.isEnabled  = !loading
        binding.btnGallery.isEnabled = !loading
    }
}
