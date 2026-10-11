package app.stripsense.data

import android.content.Context
import app.stripsense.core.domain.Person
import app.stripsense.core.domain.StoreData

/** Alarm receivers and the activity share one writer without loading any models. */
object AppStore {
    @Volatile private var instance: Store? = null
    fun get(context: Context): Store = instance ?: synchronized(this) {
        instance ?: Store(context.applicationContext.filesDir, KeystoreCipher()) {
            StoreData(person = Person(callEn = "", callHi = "", contactEn = "", contactHi = ""), setupVersion = 1)
        }.also { instance = it }
    }
}
