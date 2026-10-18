package com.movtery.zalithlauncher.feature.localauth

import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.movtery.zalithlauncher.event.single.AccountUpdateEvent
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.event.value.OtherLoginEvent
import com.movtery.zalithlauncher.feature.accounts.AccountType
import com.movtery.zalithlauncher.feature.accounts.AccountUtils
import com.movtery.zalithlauncher.feature.accounts.AccountsManager
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.util.UUID

/**
 * Entry points for Sally Auth accounts (on-device auth server with skin / cape):
 * [showCreate] adds a new account, [showChangeSkin] edits the skin / cape of the selected one.
 * Create an instance as a property of the Fragment (the file picker must be registered early).
 */
class SallyAuthUi(private val fragment: Fragment) {
    private var pending: ((Uri) -> Unit)? = null
    private val picker = fragment.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pending
        pending = null
        if (uri != null && cb != null) cb(uri)
    }

    private val nameRegex = Regex("^[A-Za-z0-9_]{3,16}$")

    private fun str(id: Int) = fragment.requireContext().getString(id)

    private fun toast(s: String) = Toast.makeText(fragment.requireContext(), s, Toast.LENGTH_LONG).show()

    private fun pickPng(onBytes: (ByteArray) -> Unit) {
        val ctx = fragment.requireContext()
        pending = { uri ->
            try {
                val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException(str(R.string.sally_auth_file_error))
                onBytes(bytes)
            } catch (e: Exception) {
                toast(e.message ?: str(R.string.sally_auth_file_error))
            }
        }
        picker.launch("image/png")
    }

    /** "Sally Auth" button: create a new account. */
    fun showCreate() {
        SallyAuthDialog(fragment.requireContext(), null, ::pickPng, ::submitNew).show()
    }

    private fun submitNew(name: String, model: String, skin: String?, cape: String?): Boolean {
        if (!nameRegex.matches(name)) { toast(str(R.string.sally_auth_invalid_name)); return false }
        if (LocalAuthStore.findByName(name) != null) { toast(str(R.string.sally_auth_name_exists)); return false }
        createAccount(LocalAuthStore.Profile(LocalAuthStore.newProfileId(), name, model, skin, cape))
        return true
    }

    /** "Change Skin" button: edit the skin / cape of the selected Sally Auth account. */
    fun showChangeSkin() {
        val acc = AccountsManager.currentAccount
        val prof = if (AccountUtils.isSallyAuthAccount(acc)) LocalAuthStore.getProfile(acc!!.profileId) else null
        if (prof == null) { toast(str(R.string.sally_auth_select_first)); return }
        SallyAuthDialog(fragment.requireContext(), prof, ::pickPng) { _, model, skin, cape ->
            prof.model = model
            prof.skin = skin
            prof.cape = cape
            LocalAuthStore.saveProfile(prof)
            syncLauncherSkin(acc!!, prof)
            EventBus.getDefault().post(AccountUpdateEvent())
            toast(str(R.string.generic_saved))
            true
        }.show()
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
            accountType = AccountType.SALLY_AUTH.type
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
            Logging.w("SallyAuth", "Could not sync launcher skin: ${e.message}")
        }
    }
}
