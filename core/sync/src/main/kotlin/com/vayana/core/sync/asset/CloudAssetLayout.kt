package com.vayana.core.sync.asset

object CloudAssetLayout {
    private val AssetIdRegex = Regex("^[A-Za-z0-9_-]{16,128}$")

    fun isValidAssetId(assetId: String): Boolean = AssetIdRegex.matches(assetId)

    fun pathFor(assetId: String): String {
        require(isValidAssetId(assetId)) { "Invalid cloud asset id" }
        val normalized = assetId.replace('_', '-')
        val first = normalized.take(2).lowercase()
        val second = normalized.drop(2).take(2).lowercase().padEnd(2, 'x')
        return "vayana/assets/$first/$second/$assetId.bin"
    }
}
