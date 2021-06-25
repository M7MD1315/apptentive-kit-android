package apptentive.com.android.feedback.model

import apptentive.com.android.feedback.utils.SensitiveDataUtils
import apptentive.com.android.serialization.json.JsonConverter.toJsonObject

data class Person(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
    val facebookId: String? = null,
    val phoneNumber: String? = null,
    val street: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val country: String? = null,
    val birthday: String? = null, // FIXME: make it Date
    @SensitiveDataKey val mParticleId: String? = null,
    @SensitiveDataKey val customData: CustomData = CustomData()
) {

    override fun toString(): String {
        return SensitiveDataUtils.logWithSanitizeCheck(javaClass, toJsonObject())
    }
}
