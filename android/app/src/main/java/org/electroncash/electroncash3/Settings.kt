package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceGroup
import com.chaquo.python.PyObject


lateinit var settings: LivePreferences


fun initSettings() {
    val sp = PreferenceManager.getDefaultSharedPreferences(app)
    settings = LivePreferences(sp)

    // Appearance
    setDefaultValue(sp, "block_explorer", libWeb.get("DEFAULT_EXPLORER")!!.toString())

    // Fiat
    setDefaultValue(sp, "use_exchange_rate",
                    libExchange.get("DEFAULT_ENABLED")!!.toJava(Boolean::class.java))
    setDefaultValue(sp, "currency", libExchange.get("DEFAULT_CURRENCY")!!.toString())
    setDefaultValue(sp, "use_exchange", libExchange.get("DEFAULT_EXCHANGE")!!.toString())

    // Set any remaining defaults from XML. Despite what some documentation says, this will NOT
    // overwrite existing values.
    PreferenceManager.setDefaultValues(app, R.xml.settings, true)
}


fun setDefaultValue(sp: SharedPreferences, key: String, default: Boolean) {
    if (!sp.contains(key)) sp.edit().putBoolean(key, default).apply()
}

fun setDefaultValue(sp: SharedPreferences, key: String, default: String) {
    if (!sp.contains(key)) sp.edit().putString(key, default).apply()
}


class SettingsFragment : PreferenceFragmentCompat(), MainFragment {
    override val title = MutableLiveData<String>().apply {
        value = app.getString(R.string.settings)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        // Appearance
        setEntries("block_explorer", libWeb.callAttr("BE_sorted_list"))

        // Fiat
        val fx = daemonModel.daemon.get("fx")!!
        val currencies = libExchange.callAttr("get_exchanges_by_ccy", false)
        setEntries("currency", py.builtins.callAttr("sorted", currencies))
        settings.getString("currency").observe(this, Observer {
            val prefExchange = findPreference("use_exchange") as ListPreference
            setEntries("use_exchange",
                       py.builtins.callAttr("sorted", currencies.callAttr("get", it)))
            if (prefExchange.value !in prefExchange.entries) {
                prefExchange.value = prefExchange.entries[0].toString()
            }
            fx.callAttr("set_currency", it)
        })
        settings.getString("use_exchange").observe(this, Observer {
            fx.callAttr("set_exchange", it)
        })

        // Do last, otherwise exchanges entries won't be populated yet and summary won't appear.
        observeGroup(preferenceScreen)
    }


    // TODO improve once Chaquopy provides better syntax.
    fun setEntries(key: String, pyList: PyObject) {
        val arr = Array(pyList.callAttr("__len__").toJava(Int::class.java)) {
            pyList.callAttr("__getitem__", it).toString()
        }
        (findPreference(key) as ListPreference).apply {
            entries = arr
            entryValues = arr
        }
    }

    fun observeGroup(group: PreferenceGroup) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            if (pref is PreferenceGroup) {
                observeGroup(pref)
            } else if (pref is ListPreference) {
                settings.getString(pref.key).observe(this, Observer {
                    if (pref.value != it) {  // Avoid infinite recursion.
                        pref.value = it
                    }
                    pref.summary = pref.entry
                })
            }
        }
    }
}

