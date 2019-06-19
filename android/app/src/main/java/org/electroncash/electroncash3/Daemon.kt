package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import com.chaquo.python.Kwarg
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform


val py by lazy {
    Python.start(AndroidPlatform(app))
    Python.getInstance()
}
fun libMod(name: String) = py.getModule("electroncash.$name")!!
fun guiMod(name: String) = py.getModule("electroncash_gui.android.$name")!!
val libNetworks by lazy { libMod("networks") }
val guiDaemon by lazy { guiMod("daemon") }

val WATCHDOG_INTERVAL = 1000L

lateinit var daemonModel: DaemonModel
val daemonUpdate = MutableLiveData<Unit>().apply { value = Unit }


fun initDaemon() {
    guiDaemon.callAttr("set_excepthook", mainHandler)
    daemonModel = DaemonModel()
}


class DaemonModel {
    val commands = guiConsole.callAttr("AndroidCommands", app)!!
    val config = commands.get("config")!!
    val daemon = commands.get("daemon")!!
    val network = commands.get("network")!!
    val wallet: PyObject?
        get() = commands.get("wallet")
    val walletName: String?
        get() {
            val wallet = this.wallet
            return if (wallet == null) null else wallet.callAttr("basename").toString()
        }

    lateinit var watchdog: Runnable

    init {
        network.callAttr("register_callback", guiDaemon.callAttr("make_callback", this),
                         guiConsole.get("CALLBACKS"))
        commands.callAttr("start")

        // This is still necessary even with the excepthook, in case a thread exits
        // non-exceptionally.
        watchdog = Runnable {
            for (thread in listOf(daemon, network)) {
                if (! thread.callAttr("is_alive").toBoolean()) {
                    throw RuntimeException("$thread unexpectedly stopped")
                }
            }
            mainHandler.postDelayed(watchdog, WATCHDOG_INTERVAL)
        }
        watchdog.run()
    }

    // This function is called from src/main/python/electroncash_gui/android/daemon.py.
    // It will sometimes be called on the main thread and sometimes on the network thread.
    @Suppress("unused")
    fun onCallback(event: String) {
        if (EXCHANGE_CALLBACKS.contains(event)) {
            fiatUpdate.postValue(Unit)
        } else {
            daemonUpdate.postValue(Unit)
        }
    }

    fun isConnected() = network.callAttr("is_connected").toBoolean()

    fun listWallets(): List<String> {
        return commands.callAttr("list_wallets").asList().map { it.toString() }
    }

    fun createWallet(name: String, password: String, kwargName: String, kwargValue: String) {
        commands.callAttr("create", name, password, Kwarg(kwargName, kwargValue))
    }

    /** If the password is wrong, throws PyException with the type InvalidPassword. */
    fun loadWallet(name: String, password: String) {
        val prevName = walletName
        commands.callAttr("load_wallet", name, password)
        if (prevName != null && prevName != name) {
            commands.callAttr("close_wallet", prevName)
        }
    }

    fun makeTx(address: String, amount: Long?, password: String? = null,
               unsigned: Boolean = false): PyObject {
        makeAddress(address)

        val amountStr: String
        if (amount == null) {
            amountStr = "!"
        } else {
            if (amount <= 0) throw ToastException(R.string.Invalid_amount)
            amountStr = formatSatoshis(amount, UNIT_BCH)
        }

        val outputs = arrayOf(arrayOf(address, amountStr))
        try {
            return commands.callAttr("_mktx", outputs, Kwarg("password", password),
                                     Kwarg("unsigned", unsigned))
        } catch (e: PyException) {
            throw if (e.message!!.startsWith("NotEnoughFunds"))
                ToastException(R.string.insufficient_funds) else e
        }
    }

    fun makeAddress(addrStr: String): PyObject {
        if (addrStr.isEmpty()) {
            throw ToastException(R.string.enter_or)
        }
        try {
            return clsAddress.callAttr("from_string", addrStr)
        } catch (e: PyException) {
            throw if (e.message!!.startsWith("AddressError"))
                ToastException(R.string.invalid_address) else e
        }
    }
}