package com.vayana.format.ocr

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * A Tesseract model the app downloads when it is first used (none ships in the APK), pinned to one commit of
 * tessdata_fast and its SHA-256. [textLanguage] is the BCP 47 language of the text it reads.
 */
enum class OcrModel(
    val id: String,
    /** Tesseract's language name: `<language>.traineddata` in `tessdata/`. */
    val language: String,
    private val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val textLanguage: String,
) {
    /** Malayalam; fastest, garbles Latin text. The default for Malayalam books. */
    MalayalamFast("mal-fast", "mal", "mal.traineddata", 5_275_996, "bd05cbf1b197e7810d2903419aedb06f9ef77bfedf50b358673c1d18d707cdb4", "ml"),

    /** Malayalam script, Latin letters too: for books that mix English in. */
    MalayalamScript("mal-script-fast", "Malayalam", "script/Malayalam.traineddata", 9_007_302, "822ad7ed9fb2eba0b0da5c788ed8710eba651d91777fc659ce0d700cfb99be06", "ml"),
    EnglishFast("eng-fast", "eng", "eng.traineddata", 4_113_088, "7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2", "en"),
    HindiFast("hin-fast", "hin", "hin.traineddata", 1_122_751, "4c73ffc59d497c186b19d1e90f5d721d678ea6b2e277b719bee4e2af12271825", "hi"),
    TamilFast("tam-fast", "tam", "tam.traineddata", 3_237_963, "d02fbec24be4b07e32e80d0ccfc3b6b67a3c5d61c9d0a7c8532677990912c6ec", "ta"),
    KannadaFast("kan-fast", "kan", "kan.traineddata", 3_608_331, "bd31e6b6ae93271e3bcf5383d306d8eefbb91542937cd6d735a5930c970e61d8", "kn"),
    TeluguFast("tel-fast", "tel", "tel.traineddata", 2_769_654, "d10691fddd5b67802e1c12800ebb321d3b8bcd8d24a2ac3ff206f93188c04ab5", "te"),
    BengaliFast("ben-fast", "ben", "ben.traineddata", 855_841, "31163084c279aaebd376216f0c3d5c17ad4b5fee8db49dae79c20000b5de5964", "bn"),
    MarathiFast("mar-fast", "mar", "mar.traineddata", 2_118_233, "0ba3f2d116972e72fe9e176bc84c38e81dfb6670f4ed1f7f6c8e16a27da7cb61", "mr"),
    GujaratiFast("guj-fast", "guj", "guj.traineddata", 1_418_394, "fa69658614b4946a9afae8853d67e0689838803dfa3d12c2e35ec53ee6f8df34", "gu"),
    PunjabiFast("pan-fast", "pan", "pan.traineddata", 497_721, "1ec0907fc3534065ea9ae190c6bb7ec9e5c74fd9d2fa996aaec7407f11ad8131", "pa"),
    OdiaFast("ori-fast", "ori", "ori.traineddata", 1_480_066, "36f3135e61d501a3acfad41f5fe60b8e791274fff4c5375c969fdcca980cdbac", "or"),
    SanskritFast("san-fast", "san", "san.traineddata", 12_404_677, "a399958921c64e7d2319369ade1e74b4db2577744ac5bfeabd16663cf743b91c", "sa"),
    FrenchFast("fra-fast", "fra", "fra.traineddata", 1_130_365, "ced037562e8c80c13122dece28dd477d399af80911a28791a66a63ac1e3445ca", "fr"),
    GermanFast("deu-fast", "deu", "deu.traineddata", 1_525_436, "19d219bbb6672c869d20a9636c6816a81eb9a71796cb93ebe0cb1530e2cdb22d", "de"),
    SpanishFast("spa-fast", "spa", "spa.traineddata", 2_294_433, "6f2e04d02774a18f01bed44b1111f2cd7f3ba7ac9dc4373cd3f898a40ea6b464", "es"),
    PortugueseFast("por-fast", "por", "por.traineddata", 1_982_756, "c4932b937207a9514b7514d518b931a99938c02a28a5a5a553f8599ed58b7deb", "pt"),
    ItalianFast("ita-fast", "ita", "ita.traineddata", 2_701_314, "b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827", "it"),
    RussianFast("rus-fast", "rus", "rus.traineddata", 3_861_738, "e16e5e036cce1d9ec2b00063cf8b54472625b9e14d893a169e2b0dedeb4df225", "ru"),
    ;

    val url: String get() = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$TessdataCommit/$path"

    companion object {
        fun byId(id: String?): OcrModel? = entries.firstOrNull { it.id == id }

        /** The model for books in [language] (a BCP 47 tag, `ta` or `ta-IN`), or null. */
        fun forLanguage(language: String?): OcrModel? {
            val primary = language?.substringBefore('-')?.lowercase() ?: return null
            return entries.firstOrNull { it.textLanguage == primary }
        }

        // tessdata_fast at the time the hashes above were taken (2026-10-20).
        private const val TessdataCommit = "87416418657359cb625c412a48b6e1d6d41c29bd"
    }
}

/**
 * Downloaded models under `<dataPath>/tessdata/`, the layout Tesseract expects. A model is only ever installed whole
 * and with the expected SHA-256; anything else is deleted.
 */
class OcrModelFiles(val dataPath: File) {
    fun isInstalled(model: OcrModel): Boolean = file(model).length() == model.sizeBytes

    /** Downloads [model] unless it is already installed. Throws on a network error or a file that doesn't match. */
    fun download(model: OcrModel) {
        if (isInstalled(model)) return
        val connection = URL(model.url).openConnection() as HttpURLConnection
        connection.connectTimeout = TimeoutMillis
        connection.readTimeout = TimeoutMillis
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} for ${model.id}")
            connection.inputStream.use { install(model, it) }
        } finally {
            connection.disconnect()
        }
    }

    /** Writes [source] as [model]'s file if its size and SHA-256 match; otherwise throws and leaves nothing behind. */
    fun install(model: OcrModel, source: InputStream) = installVerified(source, model.id, model.sizeBytes, model.sha256, file(model))

    private fun file(model: OcrModel) = File(File(dataPath, "tessdata"), "${model.language}.traineddata")

    private companion object {
        const val TimeoutMillis = 30_000
    }
}

/** Writes [source] to [target] only if it is exactly [sizeBytes] long with this [sha256]; nothing is left otherwise. */
internal fun installVerified(source: InputStream, name: String, sizeBytes: Long, sha256: String, target: File) {
    target.parentFile?.mkdirs()
    val partial = File(target.parentFile, "${target.name}.part")
    val digest = MessageDigest.getInstance("SHA-256")
    var length = 0L
    try {
        partial.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                length += read
                if (length > sizeBytes) throw IOException("$name is larger than expected")
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (length != sizeBytes || actual != sha256) throw IOException("$name does not match its pinned SHA-256")
        if (!partial.renameTo(target)) throw IOException("Could not install $name")
    } finally {
        partial.delete()
    }
}
