package com.vayana.core.sync.asset

data class CloudAssetReference(
    val id: String,
    val sha256: String,
    val sizeBytes: Long,
    val uploadedAt: Long,
) {
    init {
        require(CloudAssetLayout.isValidAssetId(id)) { "Invalid cloud asset id" }
        require(sha256.matches(Sha256Regex)) { "Invalid cloud asset SHA-256" }
        require(sizeBytes >= 0L) { "Invalid cloud asset size" }
        require(uploadedAt > 0L) { "Invalid cloud asset upload timestamp" }
    }
}

data class StagedCloudAsset(
    val reference: CloudAssetReference,
    val relativePath: String,
    val plaintextSha256: String,
)

private val Sha256Regex = Regex("^[a-f0-9]{64}$")
