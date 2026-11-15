package mod.hilal.saif.activities.tools

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowInsetsCompat
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.chrisbanes.insetter.Insetter
import org.sketchware.daygreen.DownloadUtility
import org.sketchware.daygreen.FileCheckUtils
import pro.sketchware.R
import pro.sketchware.databinding.ActivityGenericListBinding
import java.io.File

class NdkManagerActivity : BaseAppCompatActivity() {
    private lateinit var binding: ActivityGenericListBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)
        binding = ActivityGenericListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Insetter.builder()
            .padding(WindowInsetsCompat.Type.navigationBars())
            .applyToView(binding.listContainer)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.collapsingToolbar.title = "NDK Manager"
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupItems()
    }

    private fun setupItems() {
        binding.listContainer.removeAllViews()

        // Same ABI mapping as BuildToolsActivity
        val abi = DownloadUtility.getDeviceAbi()
        val ndkAbi = when (abi) {
            "arm64-v8a" -> "aarch64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x86_64"
            else -> "aarch64"
        }
        val cmakeAbi = when (abi) {
            "arm64-v8a" -> "arm64"
            "armeabi-v7a" -> "arm"
            "x86_64" -> "x64"
            else -> "arm64"
        }

        addToolCard(
            title = "Android NDK (r29)",
            description = "Required for compiling native C/C++ code",
            isInstalled = FileCheckUtils.isNdkDownloaded(this),
            url = "https://github.com/lzhiyong/termux-ndk/releases/download/android-ndk/android-ndk-r29-$ndkAbi.tar.xz",
            destination = File(filesDir, "native/ndk.tar.xz"),
            removeDir = File(filesDir, "native/ndk")
        )

        addToolCard(
            title = "CMake (3.25.3)",
            description = "Build tool to configure and compile native code",
            isInstalled = FileCheckUtils.isCmakeDownloaded(this),
            url = "https://github.com/gus23-okta/sketchware-daygreen-build-tools/releases/download/3.25.3/cmake-3.25.3-1-linux-$cmakeAbi.tar.gz",
            destination = File(filesDir, "native/cmake.tar.gz"),
            removeDir = File(filesDir, "native/cmake")
        )
    }

    private fun addToolCard(
        title: String,
        description: String,
        isInstalled: Boolean,
        url: String,
        destination: File,
        removeDir: File
    ) {
        val cardView = layoutInflater.inflate(R.layout.item_download_card, binding.listContainer, false)

        val tvTitle = cardView.findViewById<TextView>(R.id.title)
        val tvDesc = cardView.findViewById<TextView>(R.id.description)
        val tvStatusChip = cardView.findViewById<TextView>(R.id.status_chip)
        val tvStatusText = cardView.findViewById<TextView>(R.id.status_text)
        val btnDownload = cardView.findViewById<View>(R.id.btn_download)
        val imgIcon = cardView.findViewById<ImageView>(R.id.icon)

        tvTitle.text = title
        tvDesc.text = description
        tvStatusChip.text = if (isInstalled) "Installed" else "Not Installed"
        tvStatusChip.alpha = if (isInstalled) 1.0f else 0.6f
        tvStatusText.text = if (isInstalled) "Status: Installed" else "Status: Not installed"

        val downloadBtn = btnDownload as MaterialButton
        if (isInstalled) {
            downloadBtn.text = "Remove"
            downloadBtn.setIconResource(R.drawable.ic_mtrl_delete)
            downloadBtn.setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove NDK tool")
                    .setMessage("Are you sure you want to remove $title?")
                    .setPositiveButton("Remove") { _, _ ->
                        removeDir.deleteRecursively()
                        setupItems()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            downloadBtn.text = "Download"
            downloadBtn.setIconResource(R.drawable.ic_mtrl_download)
            downloadBtn.setOnClickListener {
                DownloadUtility.downloadFile(this, url, destination) {
                    setupItems()
                }
            }
        }

        imgIcon.setImageResource(R.drawable.ic_mtrl_android)
        imgIcon.setColorFilter(MaterialColors.getColor(this, R.attr.colorPrimary, Color.BLACK))

        binding.listContainer.addView(cardView)
    }
}
