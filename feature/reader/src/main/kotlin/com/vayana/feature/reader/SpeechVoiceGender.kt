package com.vayana.feature.reader

/** The gender a voice is published as. Android's own voice API does not say. */
internal enum class VoiceGender { FEMALE, MALE }

/**
 * Whether a Google Speech Services voice is male or female, or null when unknown.
 *
 * Google names voices by a locale and a three-letter code (`en-us-x-iom-local`); the gender is not part of the
 * Android API. Newer voice data often tags it in the name (`...#male_1-local`), which is trusted first. Otherwise the
 * code is looked up in the published list of Android Google voices from the RT-Voice PRO documentation (Crosstales,
 * 2024, https://crosstales.com/media/data/assets/rtvoice/RTVoice-doc.pdf). Voices released since then, or from
 * another engine (Samsung, RHVoice, ...), are not on it and get no label rather than a guess, except the few
 * measured ones below.
 */
internal fun googleVoiceGender(voiceName: String): VoiceGender? {
    val name = voiceName.lowercase()
    when {
        "#male" in name -> return VoiceGender.MALE
        "#female" in name -> return VoiceGender.FEMALE
    }
    val code = VoiceCodeRegex.find(name)?.value ?: return null
    return when (code) {
        in MaleVoiceCodes, in MeasuredMaleVoiceCodes -> VoiceGender.MALE
        in FemaleVoiceCodes, in MeasuredFemaleVoiceCodes -> VoiceGender.FEMALE
        else -> null
    }
}

/**
 * Voices newer than the published list, classified by measuring the median pitch of a sample rendered on a phone
 * (2026-09-19; the same test agreed with the published label for 17 of 20 listed voices and left the other three
 * inside the 158-162 Hz overlap where pitch alone cannot decide). Male voices measured 104-144 Hz and female
 * voices 183-261 Hz.
 *  - en-us-x-msm00013: 107 Hz.
 *  - ml-in-x-mlc: 224 Hz. ml-in-x-mle: 188 Hz, the less certain of the two, though even its lowest tenth (156 Hz)
 *    is above the male voices' medians.
 */
private val MeasuredMaleVoiceCodes = setOf("en-us-x-msm00013")
private val MeasuredFemaleVoiceCodes = setOf("ml-in-x-mlc", "ml-in-x-mle")

private val VoiceCodeRegex = Regex("^[a-z]{2,3}-[a-z]{2,4}-x-[a-z0-9]+")

private val MaleVoiceCodes = setOf(
    "ar-xa-x-ard", "ar-xa-x-are", "bn-bd-x-ban", "bn-in-x-bin", "bn-in-x-bnm", "cmn-cn-x-ccd", "cmn-cn-x-cce",
    "cmn-tw-x-ctd", "cmn-tw-x-cte", "da-dk-x-nmm", "de-de-x-deb", "de-de-x-deg", "en-au-x-aub", "en-au-x-aud",
    "en-gb-x-gbb", "en-gb-x-gbd", "en-gb-x-rjs", "en-in-x-end", "en-in-x-ene", "en-us-x-iol", "en-us-x-iom",
    "en-us-x-tpd", "es-es-x-eed", "es-es-x-eef", "es-us-x-esd", "es-us-x-esf", "et-ee-x-tms", "fil-ph-x-fid",
    "fil-ph-x-fie", "fr-ca-x-cab", "fr-ca-x-cad", "fr-fr-x-frb", "fr-fr-x-frd", "gu-in-x-gum", "hi-in-x-hid",
    "hi-in-x-hie", "id-id-x-idd", "id-id-x-ide", "it-it-x-itc", "it-it-x-itd", "ja-jp-x-jac", "ja-jp-x-jad",
    "kn-in-x-knm", "ko-kr-x-koc", "ko-kr-x-kod", "ml-in-x-mlm", "ms-my-x-msd", "ms-my-x-msg", "nb-no-x-cmj",
    "nb-no-x-tmg", "nl-nl-x-bmh", "nl-nl-x-dma", "pl-pl-x-bmg", "pl-pl-x-jmk", "pt-pt-x-jmn", "pt-pt-x-pmj",
    "ru-ru-x-rud", "ru-ru-x-ruf", "ta-in-x-tag", "te-in-x-tem", "tr-tr-x-ama", "tr-tr-x-tmc", "ur-pk-x-urm",
    "vi-vn-x-vid", "vi-vn-x-vif", "yue-hk-x-yud", "yue-hk-x-yuf",
)

private val FemaleVoiceCodes = setOf(
    "ar-xa-x-arc", "ar-xa-x-arz", "bn-in-x-bnf", "bn-in-x-bnx", "cmn-cn-x-ccc", "cmn-cn-x-ssa", "cmn-tw-x-ctc",
    "cs-cz-x-jfs", "da-dk-x-kfm", "da-dk-x-sfp", "da-dk-x-vfb", "de-de-x-nfh", "el-gr-x-vfz", "en-au-x-afh",
    "en-au-x-aua", "en-au-x-auc", "en-gb-x-fis", "en-gb-x-gba", "en-gb-x-gbc", "en-gb-x-gbg", "en-in-x-ahp",
    "en-in-x-cxx", "en-in-x-ena", "en-in-x-enc", "en-ng-x-tfn", "en-us-x-iob", "en-us-x-iog", "en-us-x-sfg",
    "en-us-x-tpc", "en-us-x-tpf", "es-es-x-eea", "es-es-x-eec", "es-es-x-eee", "es-us-x-esc", "fi-fi-x-afi",
    "fil-ph-x-cfc", "fil-ph-x-fic", "fr-ca-x-caa", "fr-ca-x-cac", "fr-fr-x-fra", "fr-fr-x-frc", "fr-fr-x-vlf",
    "gu-in-x-guf", "hi-in-x-cfn", "hi-in-x-hia", "hi-in-x-hic", "hu-hu-x-kfl", "id-id-x-dfz", "id-id-x-idc",
    "it-it-x-itb", "it-it-x-kda", "ja-jp-x-htm", "ja-jp-x-jab", "jv-id-x-jvf", "km-kh-x-khm", "kn-in-x-knf",
    "ko-kr-x-ism", "ko-kr-x-kob", "ml-in-x-mlf", "mr-in-x-mrf", "ms-my-x-msc", "ms-my-x-mse", "nb-no-x-cfl",
    "nb-no-x-rfj", "nb-no-x-tfs", "ne-np-x-nep", "nl-nl-x-lfc", "nl-nl-x-tfb", "nl-nl-x-yfr", "pl-pl-x-afb",
    "pl-pl-x-oda", "pl-pl-x-zfg", "pt-br-x-afs", "pt-pt-x-jfb", "pt-pt-x-sfs", "ro-ro-x-vfv", "ru-ru-x-dfc",
    "ru-ru-x-ruc", "ru-ru-x-rue", "si-lk-x-sin", "sk-sk-x-sfk", "su-id-x-suf", "sv-se-x-lfs", "ta-in-x-taf",
    "te-in-x-tef", "th-th-x-mol", "tr-tr-x-cfs", "tr-tr-x-efu", "tr-tr-x-mfm", "uk-ua-x-hfd", "ur-pk-x-cfn",
    "vi-vn-x-gft", "vi-vn-x-vic", "vi-vn-x-vie", "yue-hk-x-jar", "yue-hk-x-yuc", "yue-hk-x-yue",
)
