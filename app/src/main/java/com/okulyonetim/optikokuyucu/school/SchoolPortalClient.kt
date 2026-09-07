package com.okulyonetim.optikokuyucu.school

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Shared Firebase project used by the Okul Yönetim web application. */
object SchoolPortalConfig {
    const val API_KEY = "AIzaSyDJUE-Guw0JD04xXMHPnQURtLXG91H9pCI"
    const val PROJECT_ID = "okul-6e302"
    const val USERNAME_DOMAIN = "korukokuluportal.com"

    const val USERS = "oy_kullanicilar"
    const val ROLES = "oy_roller"
    const val CLASSES = "oy_siniflar"
    const val STUDENTS = "oy_veliler"
    const val TRIAL_EXAMS = "oy_denemeSinavlari"
    const val TRIAL_RESULTS = "oy_denemeSonuclari"
}

object SchoolUsername {
    fun emailFor(username: String): String {
        val normalized = username.trim()
            .lowercase(java.util.Locale("tr", "TR"))
            .replace('ı', 'i')
            .replace('ğ', 'g')
            .replace('ü', 'u')
            .replace('ş', 's')
            .replace('ö', 'o')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9._-]"), "")
        return "$normalized@${SchoolPortalConfig.USERNAME_DOMAIN}"
    }
}

data class SchoolUserProfile(
    val uid: String,
    val username: String,
    val displayName: String,
    val admin: Boolean,
    val active: Boolean,
    val roleId: String,
    val linkedTeacherId: String,
    val permissions: Map<String, String>
) {
    fun canView(permission: String): Boolean {
        if (admin) return true
        return permissions[permission] in setOf("goruntule", "duzenle", "preview", "read", "edit")
    }

    fun canEdit(permission: String): Boolean {
        if (admin) return true
        return permissions[permission] in setOf("duzenle", "edit")
    }
}

data class SchoolAuthTokens(
    val idToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long
)

data class SchoolPortalSession(
    val profile: SchoolUserProfile,
    val tokens: SchoolAuthTokens?
)

data class FirestoreDocument(
    val id: String,
    val fields: Map<String, Any?>
)

class SchoolSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SchoolPortalSession? {
        val uid = prefs.getString(KEY_UID, null)?.takeIf(String::isNotBlank) ?: return null
        val profile = SchoolUserProfile(
            uid = uid,
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "").orEmpty(),
            admin = prefs.getBoolean(KEY_ADMIN, false),
            active = prefs.getBoolean(KEY_ACTIVE, true),
            roleId = prefs.getString(KEY_ROLE_ID, "").orEmpty(),
            linkedTeacherId = prefs.getString(KEY_TEACHER_ID, "").orEmpty(),
            permissions = decodePermissions(prefs.getString(KEY_PERMISSIONS, "").orEmpty())
        )
        val idToken = prefs.getString(KEY_ID_TOKEN, "").orEmpty()
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        val tokens = if (idToken.isBlank() && refreshToken.isBlank()) null else SchoolAuthTokens(
            idToken = idToken,
            refreshToken = refreshToken,
            expiresAtEpochMs = prefs.getLong(KEY_EXPIRES_AT, 0L)
        )
        return SchoolPortalSession(profile, tokens)
    }

    fun save(session: SchoolPortalSession) {
        val p = session.profile
        val t = session.tokens
        check(
            prefs.edit()
                .putString(KEY_UID, p.uid)
                .putString(KEY_USERNAME, p.username)
                .putString(KEY_DISPLAY_NAME, p.displayName)
                .putBoolean(KEY_ADMIN, p.admin)
                .putBoolean(KEY_ACTIVE, p.active)
                .putString(KEY_ROLE_ID, p.roleId)
                .putString(KEY_TEACHER_ID, p.linkedTeacherId)
                .putString(KEY_PERMISSIONS, encodePermissions(p.permissions))
                .putString(KEY_ID_TOKEN, t?.idToken.orEmpty())
                .putString(KEY_REFRESH_TOKEN, t?.refreshToken.orEmpty())
                .putLong(KEY_EXPIRES_AT, t?.expiresAtEpochMs ?: 0L)
                .commit()
        ) { "Okul Yönetim oturumu kaydedilemedi." }
    }

    fun clear() {
        check(prefs.edit().clear().commit()) { "Oturum temizlenemedi." }
    }

    private fun encodePermissions(value: Map<String, String>): String = JSONObject(value).toString()

    private fun decodePermissions(raw: String): Map<String, String> = runCatching {
        if (raw.isBlank()) return@runCatching emptyMap()
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { key -> json.optString(key) }
    }.getOrDefault(emptyMap())

    private companion object {
        const val PREFS_NAME = "school-portal-session"
        const val KEY_UID = "uid"
        const val KEY_USERNAME = "username"
        const val KEY_DISPLAY_NAME = "display-name"
        const val KEY_ADMIN = "admin"
        const val KEY_ACTIVE = "active"
        const val KEY_ROLE_ID = "role-id"
        const val KEY_TEACHER_ID = "teacher-id"
        const val KEY_PERMISSIONS = "permissions"
        const val KEY_ID_TOKEN = "id-token"
        const val KEY_REFRESH_TOKEN = "refresh-token"
        const val KEY_EXPIRES_AT = "expires-at"
    }
}

/**
 * Small REST client that deliberately reuses the existing Okul Yönetim Firebase project without
 * requiring a second Firebase project or a second account system. Firebase Security Rules still
 * authorize every Firestore request through the ID token.
 */
class SchoolPortalClient(
    private val store: SchoolSessionStore
) {
    @Volatile
    private var session: SchoolPortalSession? = store.load()

    fun cachedSession(): SchoolPortalSession? = session

    fun signOut() {
        session = null
        store.clear()
    }

    fun signIn(username: String, password: String): SchoolPortalSession {
        require(username.isNotBlank()) { "Kullanıcı adı zorunludur." }
        require(password.isNotBlank()) { "Şifre zorunludur." }
        val email = SchoolUsername.emailFor(username)
        require(email.substringBefore('@').isNotBlank()) { "Geçerli bir kullanıcı adı girin." }

        val authResponse = request(
            method = "POST",
            url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${SchoolPortalConfig.API_KEY}",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("returnSecureToken", true)
                .toString()
        )
        val auth = JSONObject(authResponse)
        val uid = auth.getString("localId")
        val now = System.currentTimeMillis()
        val tokens = SchoolAuthTokens(
            idToken = auth.getString("idToken"),
            refreshToken = auth.getString("refreshToken"),
            expiresAtEpochMs = now + auth.optLong("expiresIn", 3600L) * 1000L
        )

        val userDoc = getDocumentWithToken(SchoolPortalConfig.USERS, uid, tokens.idToken)
            ?: error("Bu hesap Okul Yönetim kullanıcı listesinde bulunamadı.")
        val active = userDoc.fields["aktif"] as? Boolean ?: true
        if (!active) error("Bu kullanıcı hesabı henüz aktif değil.")

        val roleId = userDoc.string("rolId")
        val roleDoc = roleId.takeIf(String::isNotBlank)
            ?.let { getDocumentWithToken(SchoolPortalConfig.ROLES, it, tokens.idToken) }
        val permissions = (roleDoc?.fields?.get("yetkiler") as? Map<*, *>)
            .orEmpty()
            .entries
            .mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                k to (value as? String).orEmpty()
            }
            .toMap()

        val profile = SchoolUserProfile(
            uid = uid,
            username = userDoc.string("kullaniciAdi").ifBlank { username.trim() },
            displayName = userDoc.string("ad").ifBlank { username.trim() },
            admin = userDoc.fields["admin"] as? Boolean ?: false,
            active = active,
            roleId = roleId,
            linkedTeacherId = userDoc.string("bagliOgretmenId"),
            permissions = permissions
        )
        return SchoolPortalSession(profile, tokens).also {
            session = it
            store.save(it)
        }
    }

    fun refreshProfile(): SchoolPortalSession {
        val token = ensureIdToken()
        val current = requireNotNull(session) { "Okul Yönetim oturumu yok." }
        val userDoc = getDocumentWithToken(SchoolPortalConfig.USERS, current.profile.uid, token)
            ?: error("Kullanıcı profili bulunamadı.")
        val active = userDoc.fields["aktif"] as? Boolean ?: true
        if (!active) error("Bu kullanıcı hesabı artık aktif değil.")
        val roleId = userDoc.string("rolId")
        val roleDoc = roleId.takeIf(String::isNotBlank)
            ?.let { getDocumentWithToken(SchoolPortalConfig.ROLES, it, token) }
        val permissions = (roleDoc?.fields?.get("yetkiler") as? Map<*, *>)
            .orEmpty()
            .entries
            .mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                k to (value as? String).orEmpty()
            }
            .toMap()
        val updated = current.copy(
            profile = current.profile.copy(
                username = userDoc.string("kullaniciAdi").ifBlank { current.profile.username },
                displayName = userDoc.string("ad").ifBlank { current.profile.displayName },
                admin = userDoc.fields["admin"] as? Boolean ?: false,
                active = true,
                roleId = roleId,
                linkedTeacherId = userDoc.string("bagliOgretmenId"),
                permissions = permissions
            )
        )
        session = updated
        store.save(updated)
        return updated
    }

    fun listDocuments(collection: String): List<FirestoreDocument> {
        val token = ensureIdToken()
        val documents = mutableListOf<FirestoreDocument>()
        var pageToken: String? = null
        do {
            val suffix = buildString {
                append("?pageSize=1000")
                if (!pageToken.isNullOrBlank()) {
                    append("&pageToken=")
                    append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8.name()))
                }
            }
            val response = request(
                method = "GET",
                url = firestoreBase(collection) + suffix,
                token = token
            )
            val json = JSONObject(response)
            val array = json.optJSONArray("documents") ?: JSONArray()
            for (index in 0 until array.length()) {
                documents += FirestoreJson.decodeDocument(array.getJSONObject(index))
            }
            pageToken = json.optString("nextPageToken").takeIf(String::isNotBlank)
        } while (pageToken != null)
        return documents
    }

    fun getDocument(collection: String, id: String): FirestoreDocument? =
        getDocumentWithToken(collection, id, ensureIdToken())

    fun upsertDocument(collection: String, id: String, fields: Map<String, Any?>) {
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.name()).replace("+", "%20")
        request(
            method = "PATCH",
            url = firestoreBase(collection) + "/$encodedId",
            token = ensureIdToken(),
            body = JSONObject().put("fields", FirestoreJson.encodeFields(fields)).toString()
        )
    }

    private fun getDocumentWithToken(collection: String, id: String, token: String): FirestoreDocument? {
        val encodedId = URLEncoder.encode(id, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return try {
            FirestoreJson.decodeDocument(
                JSONObject(
                    request(
                        method = "GET",
                        url = firestoreBase(collection) + "/$encodedId",
                        token = token
                    )
                )
            )
        } catch (error: SchoolPortalHttpException) {
            if (error.statusCode == 404) null else throw error
        }
    }

    @Synchronized
    fun ensureIdToken(): String {
        val current = requireNotNull(session) { "Önce Okul Yönetim hesabıyla giriş yapın." }
        val tokens = current.tokens ?: error("Çevrimiçi oturum bilgisi bulunamadı.")
        if (tokens.idToken.isNotBlank() && tokens.expiresAtEpochMs > System.currentTimeMillis() + 60_000L) {
            return tokens.idToken
        }
        if (tokens.refreshToken.isBlank()) error("Oturum yenileme bilgisi bulunamadı.")

        val response = request(
            method = "POST",
            url = "https://securetoken.googleapis.com/v1/token?key=${SchoolPortalConfig.API_KEY}",
            contentType = "application/x-www-form-urlencoded",
            body = "grant_type=refresh_token&refresh_token=" +
                URLEncoder.encode(tokens.refreshToken, StandardCharsets.UTF_8.name())
        )
        val json = JSONObject(response)
        val refreshed = SchoolAuthTokens(
            idToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").ifBlank { tokens.refreshToken },
            expiresAtEpochMs = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        )
        val updated = current.copy(tokens = refreshed)
        session = updated
        store.save(updated)
        return refreshed.idToken
    }

    private fun firestoreBase(collection: String): String =
        "https://firestore.googleapis.com/v1/projects/${SchoolPortalConfig.PROJECT_ID}" +
            "/databases/(default)/documents/$collection"

    private fun request(
        method: String,
        url: String,
        body: String? = null,
        token: String? = null,
        contentType: String = "application/json; charset=utf-8"
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", contentType)
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) {
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    val root = JSONObject(text)
                    root.optJSONObject("error")?.optString("message")
                        ?: root.optString("error_description")
                }.getOrNull().orEmpty()
                throw SchoolPortalHttpException(code, friendlyError(message.ifBlank { text }))
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(raw: String): String = when {
        raw.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) -> "Kullanıcı adı veya şifre hatalı."
        raw.contains("EMAIL_NOT_FOUND", ignoreCase = true) -> "Kullanıcı adı veya şifre hatalı."
        raw.contains("INVALID_PASSWORD", ignoreCase = true) -> "Kullanıcı adı veya şifre hatalı."
        raw.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) -> "Çok fazla hatalı deneme yapıldı. Daha sonra tekrar deneyin."
        raw.contains("USER_DISABLED", ignoreCase = true) -> "Bu hesap devre dışı bırakılmış."
        raw.contains("PERMISSION_DENIED", ignoreCase = true) -> "Bu işlem için Okul Yönetim yetkiniz yok."
        else -> raw.ifBlank { "Okul Yönetim sunucusuna bağlanılamadı." }
    }
}

class SchoolPortalHttpException(
    val statusCode: Int,
    override val message: String
) : IllegalStateException(message)

private fun FirestoreDocument.string(key: String): String = fields[key]?.toString().orEmpty()

object FirestoreJson {
    fun decodeDocument(json: JSONObject): FirestoreDocument {
        val name = json.optString("name")
        return FirestoreDocument(
            id = name.substringAfterLast('/'),
            fields = decodeFields(json.optJSONObject("fields") ?: JSONObject())
        )
    }

    fun encodeFields(fields: Map<String, Any?>): JSONObject = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, encodeValue(value)) }
    }

    private fun decodeFields(json: JSONObject): Map<String, Any?> =
        json.keys().asSequence().associateWith { key -> decodeValue(json.getJSONObject(key)) }

    private fun decodeValue(value: JSONObject): Any? = when {
        value.has("nullValue") -> null
        value.has("stringValue") -> value.optString("stringValue")
        value.has("booleanValue") -> value.optBoolean("booleanValue")
        value.has("integerValue") -> value.optString("integerValue").toLongOrNull() ?: 0L
        value.has("doubleValue") -> value.optDouble("doubleValue")
        value.has("timestampValue") -> value.optString("timestampValue")
        value.has("arrayValue") -> {
            val values = value.optJSONObject("arrayValue")?.optJSONArray("values") ?: JSONArray()
            List(values.length()) { index -> decodeValue(values.getJSONObject(index)) }
        }
        value.has("mapValue") -> decodeFields(
            value.optJSONObject("mapValue")?.optJSONObject("fields") ?: JSONObject()
        )
        else -> null
    }

    private fun encodeValue(value: Any?): JSONObject = when (value) {
        null -> JSONObject().put("nullValue", null)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Byte, is Short, is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Float, is Double -> JSONObject().put("doubleValue", (value as Number).toDouble())
        is Map<*, *> -> {
            val map = value.entries.mapNotNull { (key, nested) ->
                (key as? String)?.let { it to nested }
            }.toMap()
            JSONObject().put("mapValue", JSONObject().put("fields", encodeFields(map)))
        }
        is Iterable<*> -> {
            val array = JSONArray()
            value.forEach { array.put(encodeValue(it)) }
            JSONObject().put("arrayValue", JSONObject().put("values", array))
        }
        is Array<*> -> {
            val array = JSONArray()
            value.forEach { array.put(encodeValue(it)) }
            JSONObject().put("arrayValue", JSONObject().put("values", array))
        }
        else -> JSONObject().put("stringValue", value.toString())
    }
}
