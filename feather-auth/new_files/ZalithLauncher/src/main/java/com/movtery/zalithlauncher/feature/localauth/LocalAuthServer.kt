package com.movtery.zalithlauncher.feature.localauth

import android.util.Base64
import com.movtery.zalithlauncher.feature.log.Logging
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Minimal Yggdrasil / authlib-injector compatible server, bound to 127.0.0.1 only.
 * Started inside the :game process right before the JVM, so nothing is reachable from outside the device
 * and no credentials or skins are sent anywhere.
 */
object LocalAuthServer {
    const val PORT = 25585
    const val BASE_URL = "http://127.0.0.1:$PORT"
    private const val TAG = "FeatherAuth"
    private const val MAX_BODY = 1_000_000

    @Volatile private var server: ServerSocket? = null
    private val joined = ConcurrentHashMap<String, String>() // serverId -> profileId
    private val pool: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "FeatherAuth-worker").apply { isDaemon = true }
    }

    private class Req(val method: String, val path: String, val query: Map<String, String>, val body: ByteArray)
    private class Resp(val code: Int, val type: String, val body: ByteArray)

    @JvmStatic
    @Synchronized
    fun ensureStarted() {
        if (server?.isClosed == false) return
        try {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 50)
            server = s
            Thread({ acceptLoop(s) }, "FeatherAuth-accept").apply { isDaemon = true; start() }
            Logging.i(TAG, "Local auth server listening on $BASE_URL")
        } catch (e: Exception) {
            Logging.e(TAG, "Could not start local auth server", e)
        }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            try {
                val c = s.accept()
                pool.execute { handle(c) }
            } catch (e: Exception) {
                if (!s.isClosed) Logging.w(TAG, "accept failed: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ HTTP plumbing

    private fun handle(sock: Socket) {
        try {
            sock.soTimeout = 10_000
            val input = BufferedInputStream(sock.getInputStream())
            val req = readRequest(input)
            val resp = if (req == null) text(400, "bad request") else {
                try { route(req) } catch (e: Exception) {
                    Logging.w(TAG, "handler error on ${req.path}: ${e.message}")
                    error(500, "InternalError", "Internal error")
                }
            }
            writeResponse(sock.getOutputStream(), resp)
        } catch (e: Exception) {
            Logging.w(TAG, "connection error: ${e.message}")
        } finally {
            try { sock.close() } catch (_: Exception) {}
        }
    }

    private fun readLine(i: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = i.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) break
            if (c != '\r'.code) b.write(c)
            if (b.size() > 8192) return null
        }
        return b.toString("ISO-8859-1")
    }

    private fun readRequest(i: InputStream): Req? {
        val line = readLine(i) ?: return null
        val parts = line.split(" ")
        if (parts.size < 2) return null
        var len = 0
        while (true) {
            val h = readLine(i) ?: return null
            if (h.isEmpty()) break
            val idx = h.indexOf(':')
            if (idx > 0 && h.substring(0, idx).trim().equals("content-length", true)) {
                len = h.substring(idx + 1).trim().toIntOrNull() ?: return null
            }
        }
        if (len < 0 || len > MAX_BODY) return null
        val body = ByteArray(len)
        var off = 0
        while (off < len) {
            val n = i.read(body, off, len - off)
            if (n < 0) return null
            off += n
        }
        val target = parts[1]
        val q = target.indexOf('?')
        val path = if (q >= 0) target.substring(0, q) else target
        val query = HashMap<String, String>()
        if (q >= 0) {
            target.substring(q + 1).split("&").forEach { kv ->
                if (kv.isEmpty()) return@forEach
                val e = kv.indexOf('=')
                val k = URLDecoder.decode(if (e >= 0) kv.substring(0, e) else kv, "UTF-8")
                val v = if (e >= 0) URLDecoder.decode(kv.substring(e + 1), "UTF-8") else ""
                query[k] = v
            }
        }
        return Req(parts[0].uppercase(), path, query, body)
    }

    private fun writeResponse(o: OutputStream, r: Resp) {
        val reason = when (r.code) { 200 -> "OK"; 204 -> "No Content"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; else -> "Error" }
        val head = "HTTP/1.1 ${r.code} $reason\r\nContent-Type: ${r.type}\r\nContent-Length: ${r.body.size}\r\nConnection: close\r\n\r\n"
        o.write(head.toByteArray(Charsets.ISO_8859_1))
        if (r.code != 204) o.write(r.body)
        o.flush()
    }

    private fun json(code: Int, o: Any): Resp = Resp(code, "application/json; charset=utf-8", o.toString().toByteArray(Charsets.UTF_8))
    private fun text(code: Int, s: String): Resp = Resp(code, "text/plain; charset=utf-8", s.toByteArray(Charsets.UTF_8))
    private fun noContent(): Resp = Resp(204, "text/plain", ByteArray(0))
    private fun error(code: Int, err: String, msg: String): Resp =
        json(code, JSONObject().put("error", err).put("errorMessage", msg))

    // ------------------------------------------------------------------ routing

    private fun route(r: Req): Resp {
        val p = r.path
        val post = r.method == "POST"
        val get = r.method == "GET"
        return when {
            get && (p == "/" || p.isEmpty()) -> metadata()
            post && p == "/authserver/authenticate" -> authenticate(r)
            post && p == "/authserver/refresh" -> refresh(r)
            post && (p == "/authserver/validate" || p == "/authserver/invalidate" || p == "/authserver/signout") -> noContent()
            post && p == "/sessionserver/session/minecraft/join" -> join(r)
            get && p == "/sessionserver/session/minecraft/hasJoined" -> hasJoined(r)
            get && p.startsWith("/sessionserver/session/minecraft/profile/") ->
                profile(p.substringAfterLast('/'), r.query["unsigned"] == "false")
            post && p == "/api/profiles/minecraft" -> profilesByName(r)
            get && p.startsWith("/textures/") && p.endsWith(".png") -> texture(p.removePrefix("/textures/").removeSuffix(".png"))
            // Minecraft 1.19+ "services" endpoints: permissive answers so chat/multiplayer are not blocked.
            get && p.endsWith("/privileges") -> json(200, JSONObject().put("privileges", privileges()))
            get && p.endsWith("/player/attributes") -> json(200, JSONObject()
                .put("privileges", privileges())
                .put("profanityFilterPreferences", JSONObject().put("profanityFilterOn", false))
                .put("banStatus", JSONObject().put("bannedScopes", JSONObject())))
            else -> error(404, "NotFound", "Not found")
        }
    }

    private fun privileges(): JSONObject = JSONObject()
        .put("onlineChat", JSONObject().put("enabled", true))
        .put("multiplayerServer", JSONObject().put("enabled", true))
        .put("multiplayerRealms", JSONObject().put("enabled", false))
        .put("telemetry", JSONObject().put("enabled", false))

    private fun metadata(): Resp = json(200, JSONObject()
        .put("meta", JSONObject()
            .put("serverName", "FeatherAuth (local)")
            .put("implementationName", "feather-local-auth")
            .put("implementationVersion", "0.1.0")
            .put("feature.non_email_login", true))
        .put("skinDomains", JSONArray().put("127.0.0.1").put("localhost"))
        .put("signaturePublickey", LocalAuthStore.publicKeyPem()))

    private fun body(r: Req): JSONObject = try { JSONObject(String(r.body, Charsets.UTF_8)) } catch (e: Exception) { JSONObject() }

    private fun token(): String = UUID.randomUUID().toString().replace("-", "")

    private fun brief(p: LocalAuthStore.Profile) = JSONObject().put("id", p.id).put("name", p.name)

    private fun authenticate(r: Req): Resp {
        val b = body(r)
        val p = LocalAuthStore.findByName(b.optString("username"))
            ?: return error(403, "ForbiddenOperationException", "Unknown local profile")
        val client = b.optString("clientToken").ifEmpty { token() }
        val out = JSONObject()
            .put("accessToken", token())
            .put("clientToken", client)
            .put("availableProfiles", JSONArray().put(brief(p)))
            .put("selectedProfile", brief(p))
        if (b.optBoolean("requestUser", false)) out.put("user", JSONObject().put("id", p.id))
        return json(200, out)
    }

    private fun refresh(r: Req): Resp {
        val b = body(r)
        val sel = b.optJSONObject("selectedProfile")
        val first = LocalAuthStore.listProfiles().firstOrNull()
        val p = (sel?.optString("id")?.let { LocalAuthStore.getProfile(it) }) ?: first
            ?: return error(403, "ForbiddenOperationException", "No local profiles")
        return json(200, JSONObject()
            .put("accessToken", token())
            .put("clientToken", b.optString("clientToken").ifEmpty { token() })
            .put("selectedProfile", brief(p)))
    }

    private fun join(r: Req): Resp {
        val b = body(r)
        val p = LocalAuthStore.getProfile(b.optString("selectedProfile"))
            ?: return error(403, "ForbiddenOperationException", "Invalid profile")
        val serverId = b.optString("serverId")
        if (serverId.isEmpty()) return error(403, "ForbiddenOperationException", "Missing serverId")
        if (joined.size > 1000) joined.clear()
        joined[serverId] = p.id
        return noContent()
    }

    private fun hasJoined(r: Req): Resp {
        val p = LocalAuthStore.findByName(r.query["username"] ?: "") ?: return noContent()
        val serverId = r.query["serverId"] ?: return noContent()
        if (joined[serverId] != p.id) return noContent()
        return json(200, profileJson(p, signed = true))
    }

    private fun profile(id: String, signed: Boolean): Resp {
        val p = LocalAuthStore.getProfile(id) ?: return noContent()
        return json(200, profileJson(p, signed))
    }

    private fun profilesByName(r: Req): Resp {
        val arr = try { JSONArray(String(r.body, Charsets.UTF_8)) } catch (e: Exception) { JSONArray() }
        val out = JSONArray()
        for (i in 0 until minOf(arr.length(), 100)) {
            LocalAuthStore.findByName(arr.optString(i))?.let { out.put(brief(it)) }
        }
        return json(200, out)
    }

    private fun texture(hash: String): Resp {
        val f = LocalAuthStore.textureFile(hash) ?: return error(404, "NotFound", "No such texture")
        return Resp(200, "image/png", f.readBytes())
    }

    private fun profileJson(p: LocalAuthStore.Profile, signed: Boolean): JSONObject {
        val textures = JSONObject()
        p.skin?.let { h ->
            val skin = JSONObject().put("url", "$BASE_URL/textures/$h.png")
            if (p.model == "slim") skin.put("metadata", JSONObject().put("model", "slim"))
            textures.put("SKIN", skin)
        }
        p.cape?.let { h -> textures.put("CAPE", JSONObject().put("url", "$BASE_URL/textures/$h.png")) }

        val payload = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("profileId", p.id)
            .put("profileName", p.name)
            .put("textures", textures)
        val value = Base64.encodeToString(payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val prop = JSONObject().put("name", "textures").put("value", value)
        if (signed) prop.put("signature", LocalAuthStore.sign(value))
        return JSONObject().put("id", p.id).put("name", p.name).put("properties", JSONArray().put(prop))
    }
}
