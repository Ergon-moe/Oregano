package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager


open class LivePreferences(val context: Context, val resId: Int) {
    val sp = PreferenceManager.getDefaultSharedPreferences(context)
    var haveSetDefaults = false

    val booleans = HashMap<String, LivePreference<Boolean>>()
    val strings = HashMap<String, LivePreference<String>>()

    fun getBoolean(key: String) =
        get(booleans, key, { LiveBooleanPreference(sp, key) })

    fun getString(key: String) =
        get(strings, key, { LiveStringPreference(sp, key) })

    fun <T> get(map: MutableMap<String, T>, key: String, create: () -> T): T {
        if (!haveSetDefaults) {
            setDefaultValues()
            haveSetDefaults = true
        }

        var result = map.get(key)
        if (result != null) {
            return result
        } else {
            result = create()
            map.put(key, result)
            return result
        }
    }

    open fun setDefaultValues() {
        // Despite what some documentation says, this will NOT overwrite existing values.
        PreferenceManager.setDefaultValues(context, resId, true)
    }

    fun setDefaultValue(key: String, value: Boolean) {
        if (!sp.contains(key)) sp.edit().putBoolean(key, value).apply()
    }

    fun setDefaultValue(key: String, value: String) {
        if (!sp.contains(key)) sp.edit().putString(key, value).apply()
    }
}


abstract class LivePreference<T>(val sp: SharedPreferences, val key: String)
    : MutableLiveData<T>(), SharedPreferences.OnSharedPreferenceChangeListener {

    abstract fun spGet(): T
    abstract fun spSet(value: T)

    init {
        update(sp)
        sp.registerOnSharedPreferenceChangeListener(this)
    }

    override fun setValue(value: T) {
        spSet(value)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
        if (key == this.key) {
            update(sp)
        }
    }

    private fun update(sp: SharedPreferences) {
        if (!sp.contains(key)) {
            throw IllegalArgumentException("Preference not found: $key")
        }
        super.setValue(spGet())
    }
}


class LiveBooleanPreference(sp: SharedPreferences, key: String) : LivePreference<Boolean>(sp, key) {
    override fun spGet(): Boolean { return sp.getBoolean(key, false) }
    override fun spSet(value: Boolean) { sp.edit().putBoolean(key, value).apply() }
}

class LiveStringPreference(sp: SharedPreferences, key: String) : LivePreference<String>(sp, key) {
    override fun spGet(): String { return sp.getString(key, null)!! }
    override fun spSet(value: String) { sp.edit().putString(key, value).apply() }
}