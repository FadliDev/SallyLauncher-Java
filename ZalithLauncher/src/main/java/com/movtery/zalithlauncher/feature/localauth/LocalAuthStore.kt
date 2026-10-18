package com.movtery.zalithlauncher.feature.localauth

import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import com.movtery.zalithlauncher.utils.path.PathManager
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device storage for SallyAuth: profiles, textures and the RSA signing key.
 * Everything lives in <filesDir>/local_auth and never leaves the device.
 * Shared between the :launcher process (UI writes) and the :game process (server reads) through plain files.
 */
object LocalAuthStore {
    class Profile(
        val id: String,
        val name: String,
        var model: String = "default", // "default" (Steve) or "slim" (Alex)
        var skin: String? = null,      // sha256 hex of the PNG
        var cape: String? = null
    )

    private val lock = Any()
    private const val MAX_TEXTURE_BYTES = 1_000_000

    private fun root(): File = File(PathManager.DIR_FILE, "local_auth").also { it.mkdirs() }
    private fun profilesDir(): File = File(root(), "profiles").also { it.mkdirs() }
    private fun texturesDir(): File = File(root(), "textures").also { it.mkdirs() }

    fun normalizeId(id: String): String = id.replace("-", "").lowercase()

    fun newProfileId(): String = UUID.randomUUID().toString().replace("-", "")

    // ---------------------------------------------------------------- profiles

    fun saveProfile(p: Profile) {
      synchronized(lock) {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("name", p.name)
        o.put("model", p.model)
        o.put("skin", p.skin ?: JSONObject.NULL)
        o.put("cape", p.cape ?: JSONObject.NULL)
        val target = File(profilesDir(), p.id + ".json")
        val tmp = File(profilesDir(), p.id + ".json.tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(target)) { target.writeText(o.toString()); tmp.delete() }
        Unit
      }
    }

    private fun readProfile(f: File): Profile? = try {
        val o = JSONObject(f.readText())
        Profile(
            o.getString("id"),
            o.getString("name"),
            o.optString("model", "default"),
            if (o.isNull("skin")) null else o.optString("skin"),
            if (o.isNull("cape")) null else o.optString("cape")
        )
    } catch (e: Exception) { null }

    fun listProfiles(): List<Profile> = synchronized(lock) {
        profilesDir().listFiles { f -> f.name.endsWith(".json") }?.mapNotNull { readProfile(it) } ?: emptyList()
    }

    fun getProfile(id: String): Profile? {
        val nid = normalizeId(id)
        if (!Regex("^[0-9a-f]{32}$").matches(nid)) return null
        synchronized(lock) {
            val f = File(profilesDir(), "$nid.json")
            return if (f.exists()) readProfile(f) else null
        }
    }

    fun findByName(name: String): Profile? = listProfiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    // ---------------------------------------------------------------- textures

    fun textureFile(hash: String): File? {
        if (!Regex("^[0-9a-f]{64}$").matches(hash)) return null
        val f = File(texturesDir(), "$hash.png")
        return if (f.exists()) f else null
    }

    /** Validates a PNG skin/cape, stores it by content hash and returns the hash. */
    fun importTexture(bytes: ByteArray, cape: Boolean): String {
        require(bytes.size in 1..MAX_TEXTURE_BYTES) { "File is empty or larger than 1 MB" }
        val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        require(bytes.size > 8 && bytes.copyOfRange(0, 8).contentEquals(sig)) { "Not a PNG file" }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = opts.outWidth
        val h = opts.outHeight
        if (cape) {
            require((w % 64 == 0 && h == w / 2) || (w == 22 && h == 17)) { "Cape must be 64x32 (or a multiple), got ${w}x$h" }
        } else {
            require(w % 64 == 0 && (h == w || h == w / 2) && w <= 1024) { "Skin must be 64x64 or 64x32 (or a multiple), got ${w}x$h" }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        synchronized(lock) {
            val f = File(texturesDir(), "$hash.png")
            if (!f.exists()) f.writeBytes(bytes)
            Unit
        }
        return hash
    }

    // ---------------------------------------------------------------- skin model detection

    private val modelCache = ConcurrentHashMap<String, String>() // texture hash -> "slim" | "default"

    /**
     * Detects the arm model of a skin PNG: "slim" (Alex, 3px arms) or "default" (Steve/classic, 4px arms).
     * A slim skin leaves the unused strip of the right arm texture fully transparent:
     * x 50..51 / y 16..19 and x 54..55 / y 20..31 on a 64x64 skin (scaled for HD skins).
     * Legacy 64x32 skins only exist in the classic model.
     */
    fun detectModel(bytes: ByteArray): String {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return "default"
        try {
            val w = bmp.width
            val h = bmp.height
            if (w < 64 || w % 64 != 0 || h != w) return "default"
            val s = w / 64
            fun transparent(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
                for (y in y0 * s until (y1 + 1) * s) {
                    for (x in x0 * s until (x1 + 1) * s) {
                        if (Color.alpha(bmp.getPixel(x, y)) != 0) return false
                    }
                }
                return true
            }
            val right = transparent(50, 16, 51, 19) && transparent(54, 20, 55, 31)
            return if (right) "slim" else "default"
        } finally {
            bmp.recycle()
        }
    }

    /** The model to advertise for a profile: detected from its skin file, or the stored one if there is no skin. */
    fun effectiveModel(p: Profile): String {
        val hash = p.skin ?: return p.model
        modelCache[hash]?.let { return it }
        val file = textureFile(hash) ?: return p.model
        val model = try { detectModel(file.readBytes()) } catch (e: Exception) { return p.model }
        modelCache[hash] = model
        return model
    }

    class ImportedSkin(val hash: String, val model: String)

    /** Imports a skin PNG and auto-detects whether it is slim or classic. */
    fun importSkin(bytes: ByteArray): ImportedSkin {
        val hash = importTexture(bytes, cape = false)
        val model = detectModel(bytes)
        modelCache[hash] = model
        return ImportedSkin(hash, model)
    }

    // ---------------------------------------------------------------- signing key

    private fun keyFile(): File = File(root(), "signing_key.pkcs8")
    private fun pubFile(): File = File(root(), "signing_key.x509")

    private fun ensureKey() {
        synchronized(lock) {
            if (keyFile().exists() && pubFile().exists()) return
            val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            keyFile().writeBytes(kp.private.encoded)
            pubFile().writeBytes(kp.public.encoded)
        }
    }

    fun publicKeyPem(): String {
        ensureKey()
        val b64 = Base64.encodeToString(pubFile().readBytes(), Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n" + b64.chunked(64).joinToString("\n") + "\n-----END PUBLIC KEY-----\n"
    }

    /** SHA1withRSA signature, base64, as required for the "textures" profile property. */
    fun sign(data: String): String {
        ensureKey()
        val priv = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile().readBytes()))
        val s = Signature.getInstance("SHA1withRSA")
        s.initSign(priv)
        s.update(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(s.sign(), Base64.NO_WRAP)
    }
}
