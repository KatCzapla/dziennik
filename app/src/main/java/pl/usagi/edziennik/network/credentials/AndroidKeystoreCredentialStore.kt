package pl.usagi.edziennik.network.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) : CredentialStore {
    private val storageFile = File(context.noBackupFilesDir, STORAGE_FILE_NAME)
    private val mutex = Mutex()

    override suspend fun save(
        username: String,
        password: String,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val payload = json.encodeToString(
                CredentialsPayload(
                    username = username,
                    password = password,
                ),
            )
            val encrypted = encrypt(payload.encodeToByteArray())
            storageFile.parentFile?.mkdirs()
            storageFile.writeText(json.encodeToString(encrypted))
        }
    }

    override suspend fun load(): StoredCredentials? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!storageFile.exists()) return@withLock null

            val payload = try {
                val encrypted = json.decodeFromString<EncryptedValue>(storageFile.readText())
                decrypt(encrypted).decodeToString()
            } catch (exception: GeneralSecurityException) {
                clearInvalidCredentials()
                return@withLock null
            } catch (exception: IOException) {
                clearInvalidCredentials()
                return@withLock null
            } catch (exception: SerializationException) {
                clearInvalidCredentials()
                return@withLock null
            } catch (exception: IllegalArgumentException) {
                clearInvalidCredentials()
                return@withLock null
            }

            try {
                val credentials = json.decodeFromString<CredentialsPayload>(payload)
                StoredCredentials(
                    username = credentials.username,
                    password = credentials.password,
                )
            } catch (exception: SerializationException) {
                clearInvalidCredentials()
                null
            } catch (exception: IllegalArgumentException) {
                clearInvalidCredentials()
                null
            }
        }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            clearInvalidCredentials()
        }
    }

    override suspend fun hasCredentials(): Boolean = load() != null

    private fun encrypt(plaintext: ByteArray): EncryptedValue {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())

        return EncryptedValue(
            ciphertext = cipher.doFinal(plaintext).toBase64(),
            iv = cipher.iv.toBase64(),
        )
    }

    private fun decrypt(encryptedValue: EncryptedValue): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, encryptedValue.iv.fromBase64()),
        )
        return cipher.doFinal(encryptedValue.ciphertext.fromBase64())
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { entry ->
            return entry.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE_BITS)
            .build()

        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    private fun clearInvalidCredentials() {
        if (storageFile.exists()) {
            storageFile.delete()
        }
    }

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray =
        Base64.decode(this, Base64.NO_WRAP)

    @Serializable
    private data class CredentialsPayload(
        val username: String,
        val password: String,
    )

    @Serializable
    private data class EncryptedValue(
        val ciphertext: String,
        val iv: String,
    )

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "edziennik_credentials_key"
        const val STORAGE_FILE_NAME = "credentials.enc"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
