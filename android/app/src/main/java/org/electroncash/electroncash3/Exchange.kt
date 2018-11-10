package org.electroncash.electroncash3

import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData


val libExchange by lazy { libMod("exchange_rate") }
lateinit var fiatUpdate: MutableLiveData<Unit>


fun initExchange() {
    fiatUpdate = MediatorLiveData<Unit>().apply {
        addSource(settings.getBoolean("use_exchange_rate"), { value = Unit })
        addSource(settings.getString("currency"), { value = Unit })
        addSource(settings.getString("use_exchange"), { value = Unit })
    }
}


fun formatFiat(daemonModel: DaemonModel, amount: Long): String? {
    val fx = daemonModel.daemon.get("fx")!!
    if (!fx.callAttr("is_enabled").toJava(Boolean::class.java)) {
        return null
    }
    val result = fx.callAttr("format_amount_and_units", amount).toString()
    return if (result.isEmpty()) null else result
}