package org.electroncash.electroncash3

import android.arch.lifecycle.MediatorLiveData


val libExchange by lazy { libMod("exchange_rate") }
val fiatUpdate = MediatorLiveData<Unit>()


fun initExchange() {
    val fx = daemonModel.daemon.get("fx")!!
    settings.getString("currency").observeForever {
        fx.callAttr("set_currency", it)
    }
    settings.getString("use_exchange").observeForever {
        fx.callAttr("set_exchange", it)
    }

    with (fiatUpdate) {
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