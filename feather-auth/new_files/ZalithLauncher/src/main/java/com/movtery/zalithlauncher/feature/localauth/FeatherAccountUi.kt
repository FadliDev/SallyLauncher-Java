package com.movtery.zalithlauncher.feature.localauth

import android.content.DialogInterface
import android.net.Uri
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.event.single.AccountUpdateEvent
import com.movtery.zalithlauncher.event.value.OtherLoginEvent
import com.movtery.zalithlauncher.feature.accounts.AccountType
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.UUID

/**
 * UI for FeatherAuth accounts: create a local account with skin/cape, or edit the current one.
 * Create an instance as a property of the Fragment (the file picker must be registered early).
 */
class FeatherAccountUi(private val fragment: Fragment) {
    private var pending: ((Uri) -> Unit)? = null
    private val picker = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pending
        pending = null
        if (uri != null && cb != null) cb(uri)
    }

    private val nameRegex = Regex("^[A-Za-z0-9_]{3,16}$")

    fun showMenu() {
        val ctx = fragment.requireContext()
        AlertDialog.Builder(ctx)
            .setTitle("Local skin account")
            .setItems(arrayOf("Create new account", "Edit current account (skin / cape)")) { _, which ->
                if (which == 0) showEditor(null) else {
                    val acc = AccountsManager.currentAccount
                    val prof = if (acc != null && acc.accountType == AccountType.FEATHER.type) LocalAuthStore.getProfile(acc.profileId) else null
                    if (prof == null) toast("Select a FeatherAuth account first") else showEditor(prof)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(fragment.requireContext(), s, Toast.LENGTH_LONG).show()

    private fun pickPng(onBytes: (ByteArray) -> Unit) {
        val ctx = fragment.requireContext()
        pending = { uri ->
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Cannot read file")
                onBytes(bytes)
            } catch (e: Exception) {
                toast(e.message ?: "Failed to read file")
            }
        }
        picker.launch("image/png")
    }

    private fun showEditor(existing: LocalAuthStore.Profile?) {
        val ctx = fragment.requireContext()
        val pad = (16 * ctx.resources.displayMetrics.density).toInt()
        val layout = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val nameEdit = EditText(ctx).apply {
            hint = "Username (3-16: A-Z a-z 0-9 _)"
            setSingleLine()
            setText(existing?.name ?: "")
            isEnabled = existing == null
        }
        val slim = CheckBox(ctx).apply {
            text = "Slim arms (Alex model)"
            isChecked = existing?.model == "slim"
        }
        var skinHash: String? = existing?.skin
        var capeHash: String? = existing?.cape
        val skinInfo = TextView(ctx)
        val capeInfo = TextView(ctx)
        fun refreshInfo() {
            skinInfo.text = if (skinHash != null) "Skin: set" else "Skin: none (default)"
            capeInfo.text = if (capeHash != null) "Cape: set" else "Cape: none"
        }
        refreshInfo()

        val skinBtn = Button(ctx).apply {
            text = "Choose skin PNG"
            setOnClickListener {
                pickPng { bytes ->
                    try { skinHash = LocalAuthStore.importTexture(bytes, cape = false); refreshInfo() }
                    catch (e: Exception) { toast(e.message ?: "Invalid skin") }
                }
            }
        }
        val capeBtn = Button(ctx).apply {
            text = "Choose cape PNG"
            setOnClickListener {
                pickPng { bytes ->
                    try { capeHash = LocalAuthStore.importTexture(bytes, cape = true); refreshInfo() }
                    catch (e: Exception) { toast(e.message ?: "Invalid cape") }
                }
            }
        }
        val noCapeBtn = Button(ctx).apply {
            text = "Remove cape"
            setOnClickListener { capeHash = null; refreshInfo() }
        }

        listOf(nameEdit, slim, skinInfo, skinBtn, capeInfo, capeBtn, noCapeBtn).forEach {
            layout.addView(it, LinearLayout.LayoutParams(match, wrap))
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle(if (existing == null) "New local account" else "Edit ${existing.name}")
            .setView(layout)
            .setPositiveButton("Save", null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val model = if (slim.isChecked) "slim" else "default"
            if (existing == null) {
                val name = nameEdit.text.toString().trim()
                if (!nameRegex.matches(name)) { toast("Invalid username"); return@setOnClickListener }
                if (LocalAuthStore.findByName(name) != null) { toast("That username already exists"); return@setOnClickListener }
                createAccount(LocalAuthStore.Profile(LocalAuthStore.newProfileId(), name, model, skinHash, capeHash))
            } else {
                existing.model = model
                existing.skin = skinHash
                existing.cape = capeHash
                LocalAuthStore.saveProfile(existing)
                AccountsManager.currentAccount?.let { syncLauncherSkin(it, existing) }
                EventBus.getDefault().post(AccountUpdateEvent())
                toast("Saved")
            }
            dialog.dismiss()
        }
    }

    private fun createAccount(profile: LocalAuthStore.Profile) {
        LocalAuthStore.saveProfile(profile)
        val account = MinecraftAccount().apply {
            username = profile.name
            profileId = profile.id
            accessToken = UUID.randomUUID().toString().replace("-", "")
            clientToken = UUID.randomUUID().toString().replace("-", "")
            otherBaseUrl = LocalAuthServer.BASE_URL
            otherAccount = profile.name
            otherPassword = "0"
            accountType = AccountType.FEATHER.type
        }
        syncLauncherSkin(account, profile)
        EventBus.getDefault().post(OtherLoginEvent(account))
    }

    /** The launcher draws avatars from DIR_USER_SKIN/<uniqueUUID>.png, so mirror the chosen skin there. */
    private fun syncLauncherSkin(account: MinecraftAccount, profile: LocalAuthStore.Profile) {
        try {
            val dst = File(PathManager.DIR_USER_SKIN, account.uniqueUUID + ".png")
            val src = profile.skin?.let { LocalAuthStore.textureFile(it) }
            if (src != null) src.copyTo(dst, overwrite = true) else if (dst.exists()) dst.delete()
        } catch (e: Exception) {
            Logging.w("FeatherAuth", "Could not sync launcher skin: ${e.message}")
        }
    }
}
