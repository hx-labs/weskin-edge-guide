package net.fitken.example

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import net.fitken.example.databinding.ActivityMainBinding
import net.fitken.mlselfiecamera.selfie.SelfieActivity


class MainActivity : AppCompatActivity() {

    companion object {
        const val SELFIE_REQUEST_CODE = 1
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var startForResult: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imagePath = result.data?.getStringExtra(SelfieActivity.KEY_IMAGE_PATH)
                Toast.makeText(this, imagePath, Toast.LENGTH_SHORT).show()
                val bmImg = BitmapFactory.decodeFile(imagePath)
                binding.profileImage.setImageBitmap(bmImg)
            }
        }

        binding.btnOpenCamera.setOnClickListener {
            val intent = Intent(this, SelfieActivity::class.java)
            startForResult.launch(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SELFIE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val imagePath = data?.getStringExtra(SelfieActivity.KEY_IMAGE_PATH)
            Toast.makeText(
                this,
                imagePath,
                Toast.LENGTH_SHORT
            ).show()
            val bmImg = BitmapFactory.decodeFile(imagePath)
            binding.profileImage.setImageBitmap(bmImg)
        }
    }
}
