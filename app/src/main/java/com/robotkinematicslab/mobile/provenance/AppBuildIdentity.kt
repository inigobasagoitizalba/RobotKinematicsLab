package com.robotkinematicslab.mobile.provenance

import com.robotkinematicslab.mobile.BuildConfig

data class AppBuildIdentity(
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val buildType: String
) {
    companion object {
        val UNKNOWN = AppBuildIdentity("unknown", "unknown", 0L, "unknown")

        fun current() = AppBuildIdentity(
            applicationId = BuildConfig.APPLICATION_ID,
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE.toLong(),
            buildType = BuildConfig.BUILD_TYPE
        )
    }
}
