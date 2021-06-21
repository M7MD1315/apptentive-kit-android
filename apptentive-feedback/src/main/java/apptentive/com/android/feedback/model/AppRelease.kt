package apptentive.com.android.feedback.model

import apptentive.com.android.feedback.utils.VersionCode
import apptentive.com.android.feedback.utils.VersionName

/**
 * @param type - always set to "android"
 * @param identifier - host application package name
 * @param versionCode - host application version code
 * @param versionName - host application version name
 * @param targetSdkVersion - host application target sdk version
 */
data class AppRelease(
    val type: String,
    val identifier: String,
    val versionCode: VersionCode,
    val versionName: VersionName,
    val targetSdkVersion: String,
    val debug: Boolean = false,
    val inheritStyle: Boolean = false,
    val overrideStyle: Boolean = false,
    val appStore: String? = null
)
