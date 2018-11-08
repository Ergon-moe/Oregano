package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.os.Bundle
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceFragmentCompat
import android.support.v7.preference.PreferenceGroup
import com.chaquo.python.PyObject


class Settings(context: Context, resId: Int) : LivePreferences(context, resId) {
    override fun setDefaultValues() {
        // Appearance
        setDefaultValue("block_explorer", libWeb.get("DEFAULT_EXPLORER")!!.toString())

        // Fiat
        setDefaultValue("use_exchange_rate",
                        libExchange.get("DEFAULT_ENABLED")!!.toJava(Boolean::class.java))
        setDefaultValue("currency", libExchange.get("DEFAULT_CURRENCY")!!.toString())
        setDefaultValue("use_exchange", libExchange.get("DEFAULT_EXCHANGE")!!.toString())

        // Set any remaining defaults from XML.
        super.setDefaultValues()
    }
}


class SettingsFragment : PreferenceFragmentCompat(), MainFragment {
    override val title = MutableLiveData<String>().apply {
        value = app.getString(R.string.settings)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings, rootKey)
        observeGroup(preferenceScreen)

        // Appearance
        setEntries("block_explorer", libWeb.callAttr("BE_sorted_list"))

        // Fiat
        // TODO
    }

    // TODO improve once Chaquopy provides better syntax.
    fun setEntries(key: String, pyList: PyObject) {
        val arr =  Array(pyList.callAttr("__len__").toJava(Int::class.java)) {
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
                    pref.summary = pref.entry
                })
            }
        }
    }

}

