package com.movtery.zalithlauncher.feature.localauth

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.Toast
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.databinding.DialogSallyAuthBinding
import com.movtery.zalithlauncher.ui.dialog.DraggableDialog
import com.movtery.zalithlauncher.ui.dialog.FullScreenDialog

/**
 * Sally Auth dialog, styled like the other launcher dialogs.
 * - existing == null: create a new account (username + optional skin / cape)
 * - existing != null: change the skin / cape of that account
 * The arm model (slim / classic) is never asked: it is detected from the skin PNG.
 */
class SallyAuthDialog(
    context: Context,
    private val existing: LocalAuthStore.Profile?,
    /** Opens the system file picker for a PNG and hands the bytes back. */
    private val pickPng: (onBytes: (ByteArray) -> Unit) -> Unit,
    /** Called on Save; return true to close the dialog. */
    private val onSubmit: (name: String, model: String, skin: String?, cape: String?) -> Boolean
) : FullScreenDialog(context), View.OnClickListener, DraggableDialog.DialogInitializationListener {
    private val binding = DialogSallyAuthBinding.inflate(layoutInflater)

    private var skinHash: String? = existing?.skin
    private var capeHash: String? = existing?.cape
    private var model: String = existing?.let { LocalAuthStore.effectiveModel(it) } ?: "default"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.apply {
            titleText.text = if (existing == null) context.getString(R.string.sally_auth_new_title)
            else context.getString(R.string.sally_auth_edit_title, existing.name)

            nameEdit.setText(existing?.name ?: "")
            if (existing != null) {
                nameEdit.isEnabled = false
                nameEdit.alpha = 0.6f
            }

            chooseSkinButton.setOnClickListener(this@SallyAuthDialog)
            resetSkinButton.setOnClickListener(this@SallyAuthDialog)
            chooseCapeButton.setOnClickListener(this@SallyAuthDialog)
            removeCapeButton.setOnClickListener(this@SallyAuthDialog)
            cancelButton.setOnClickListener(this@SallyAuthDialog)
            saveButton.setOnClickListener(this@SallyAuthDialog)
        }
        refreshInfo()
        DraggableDialog.initDialog(this)
    }

    private fun refreshInfo() {
        binding.apply {
            skinInfo.setText(
                when {
                    skinHash == null -> R.string.sally_auth_skin_default
                    model == "slim" -> R.string.sally_auth_skin_slim
                    else -> R.string.sally_auth_skin_classic
                }
            )
            capeInfo.setText(if (capeHash != null) R.string.sally_auth_cape_set else R.string.sally_auth_cape_none)
            resetSkinButton.isEnabled = skinHash != null
            removeCapeButton.isEnabled = capeHash != null
        }
    }

    private fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    override fun onInit(): Window? = window

    override fun onClick(v: View) {
        binding.apply {
            when (v) {
                chooseSkinButton -> pickPng { bytes ->
                    try {
                        val skin = LocalAuthStore.importSkin(bytes)
                        skinHash = skin.hash
                        model = skin.model
                        refreshInfo()
                    } catch (e: Exception) {
                        toast(e.message ?: context.getString(R.string.sally_auth_file_error))
                    }
                }
                resetSkinButton -> {
                    skinHash = null
                    model = "default"
                    refreshInfo()
                }
                chooseCapeButton -> pickPng { bytes ->
                    try {
                        capeHash = LocalAuthStore.importTexture(bytes, cape = true)
                        refreshInfo()
                    } catch (e: Exception) {
                        toast(e.message ?: context.getString(R.string.sally_auth_file_error))
                    }
                }
                removeCapeButton -> {
                    capeHash = null
                    refreshInfo()
                }
                cancelButton -> dismiss()
                saveButton -> {
                    val name = nameEdit.text.toString().trim()
                    if (onSubmit(name, model, skinHash, capeHash)) dismiss()
                }
            }
        }
    }
}
