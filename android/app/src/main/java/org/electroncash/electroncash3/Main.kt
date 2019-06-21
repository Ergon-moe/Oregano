package org.electroncash.electroncash3

import android.app.Activity
import android.app.Dialog
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Selection
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.chaquo.python.PyException
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.android.synthetic.main.change_password.*
import kotlinx.android.synthetic.main.main.*
import kotlinx.android.synthetic.main.new_wallet.*
import kotlinx.android.synthetic.main.new_wallet.etConfirmPassword
import kotlinx.android.synthetic.main.new_wallet.etPassword
import kotlinx.android.synthetic.main.text_input.*
import kotlin.properties.Delegates.notNull
import kotlin.reflect.KClass


// Drawer navigation
val ACTIVITIES = HashMap<Int, KClass<out Activity>>().apply {
    put(R.id.navSettings, SettingsActivity::class)
    put(R.id.navNetwork, NetworkActivity::class)
    put(R.id.navConsole, ECConsoleActivity::class)
}

// Bottom navigation
val FRAGMENTS = HashMap<Int, KClass<out Fragment>>().apply {
    put(R.id.navTransactions, TransactionsFragment::class)
    put(R.id.navRequests, RequestsFragment::class)
    put(R.id.navAddresses, AddressesFragment::class)
    put(R.id.navContacts, ContactsFragment::class)
}

interface MainFragment


class MainActivity : AppCompatActivity() {
    var stateValid: Boolean by notNull()
    var cleanStart = true

    override fun onCreate(state: Bundle?) {
        // Remove splash screen: doesn't work if called after super.onCreate.
        setTheme(R.style.AppTheme_NoActionBar)

        // If the wallet name doesn't match, the process has probably been restarted, so
        // ignore the UI state, including all dialogs.
        stateValid = (state != null &&
                      (state.getString("walletName") == daemonModel.walletName))
        super.onCreate(if (stateValid) state else null)

        setContentView(R.layout.main)
        setSupportActionBar(toolbar)
        supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu_24dp)
        }

        navDrawer.setNavigationItemSelectedListener { onDrawerItemSelected(it) }
        navBottom.setOnNavigationItemSelectedListener {
            showFragment(it.itemId)
            true
        }

        daemonUpdate.observe(this, Observer {
            updateToolbar()
            updateDrawer()
        })
        settings.getString("base_unit").observe(this, Observer { updateToolbar() })
        fiatUpdate.observe(this, Observer { updateToolbar() })
    }

    override fun onBackPressed() {
        if (drawer.isDrawerOpen(navDrawer)) {
            closeDrawer()
        } else {
            super.onBackPressed()
        }
    }

    fun updateToolbar() {
        val title = daemonModel.walletName ?: getString(R.string.no_wallet)

        var subtitle: String
        if (! daemonModel.isConnected()) {
            subtitle = getString(R.string.offline)
        } else {
            val wallet = daemonModel.wallet
            val localHeight = daemonModel.network.callAttr("get_local_height").toInt()
            val serverHeight = daemonModel.network.callAttr("get_server_height").toInt()
            if (localHeight < serverHeight) {
                subtitle = "${getString(R.string.synchronizing)} $localHeight / $serverHeight"
            } else if (wallet == null) {
                subtitle = getString(R.string.online)
            } else if (wallet.callAttr("is_up_to_date").toBoolean()) {
                // get_balance returns the tuple (confirmed, unconfirmed, unmatured)
                val balance = wallet.callAttr("get_balance").asList().get(0).toLong()
                subtitle = "${formatSatoshis(balance)} $unitName"
                val fiat = formatFiatAmountAndUnit(balance)
                if (fiat != null) {
                    subtitle += " ($fiat)"
                }
            } else {
                subtitle = getString(R.string.synchronizing)
            }
        }

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            setTitle(title)
            supportActionBar!!.setSubtitle(subtitle)
        } else {
            // Landscape subtitle is too small, so combine it with the title.
            setTitle("$title – $subtitle")
        }
    }

    fun openDrawer() {
        drawer.openDrawer(navDrawer)
    }

    fun closeDrawer() {
        drawer.closeDrawer(navDrawer)
    }

    fun updateDrawer() {
        val loadedWalletName = daemonModel.walletName
        val menu = navDrawer.menu
        menu.clear()

        // New menu items are added at the bottom regardless of their group ID, so we inflate
        // the fixed items in two parts.
        navDrawer.inflateMenu(R.menu.nav_drawer_1)
        for (walletName in daemonModel.listWallets()) {
            val item = menu.add(R.id.navWallets, Menu.NONE, Menu.NONE, walletName)
            item.setIcon(R.drawable.ic_wallet_24dp)
            if (walletName == loadedWalletName) {
                item.setCheckable(true)
                item.setChecked(true)
            }
        }
        navDrawer.inflateMenu(R.menu.nav_drawer_2)
    }

    fun onDrawerItemSelected(item: MenuItem): Boolean {
        val activityCls = ACTIVITIES[item.itemId]
        if (activityCls != null) {
            startActivity(Intent(this, activityCls.java))
        } else if (item.itemId == R.id.navNewWallet) {
            showDialog(this, NewWalletDialog1())
        } else if (item.itemId == Menu.NONE) {
            val walletName = item.title.toString()
            if (walletName != daemonModel.walletName) {
                showDialog(this, OpenWalletDialog().apply { arguments = Bundle().apply {
                    putString("walletName", walletName)
                }})
            }
        } else {
            throw Exception("Unknown item $item")
        }
        closeDrawer()
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (daemonModel.wallet != null) {
            menuInflater.inflate(R.menu.wallet, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> openDrawer()
            R.id.menuChangePassword -> showDialog(this, ChangePasswordDialog())
            R.id.menuShowSeed-> {
                if (daemonModel.wallet!!.containsKey("get_seed")) {
                    showDialog(this, ShowSeedPasswordDialog())
                } else {
                    toast(R.string.this_wallet_has_no_seed)
                }
            }
            R.id.menuDelete -> showDialog(this, DeleteWalletDialog())
            R.id.menuClose -> showDialog(this, CloseWalletDialog())
            else -> throw Exception("Unknown item $item")
        }
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("cleanStart", cleanStart)
        outState.putString("walletName", daemonModel.walletName)
    }

    override fun onRestoreInstanceState(state: Bundle) {
        if (stateValid) {
            super.onRestoreInstanceState(state)
            cleanStart = state.getBoolean("cleanStart", true)
        }
    }

    override fun onPostCreate(state: Bundle?) {
        super.onPostCreate(if (stateValid) state else null)
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        showFragment(navBottom.selectedItemId)
        if (cleanStart) {
            cleanStart = false
            if (daemonModel.wallet == null) {
                openDrawer()
            }
        }
    }

    fun showFragment(id: Int) {
        val ft = supportFragmentManager.beginTransaction()
        val newFrag = getFragment(id)
        for (frag in supportFragmentManager.fragments) {
            if (frag is MainFragment && frag !== newFrag) {
                ft.detach(frag)
            }
        }
        ft.attach(newFrag)

        // BottomNavigationView onClick is sometimes triggered after state has been saved
        // (https://github.com/Electron-Cash/Electron-Cash/issues/1091).
        ft.commitNowAllowingStateLoss()
    }

    private fun getFragment(id: Int): Fragment {
        val tag = "MainFragment:$id"
        var frag = supportFragmentManager.findFragmentByTag(tag)
        if (frag != null) {
            return frag
        } else {
            frag = FRAGMENTS[id]!!.java.newInstance()
            supportFragmentManager.beginTransaction()
                .add(flContent.id, frag, tag).commitNowAllowingStateLoss()
            return frag
        }
    }
}


class NewWalletDialog1 : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.new_wallet)
            .setView(R.layout.new_wallet)
            .setPositiveButton(R.string.next, null)
            .setNegativeButton(R.string.cancel, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        dialog.spnType.adapter = MenuAdapter(context!!, R.menu.wallet_type)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val name = dialog.etName.text.toString()
                if (name.isEmpty()) throw ToastException(R.string.name_is)
                if (name.contains("/")) throw ToastException(R.string.invalid_name)
                if (daemonModel.listWallets().contains(name)) {
                    throw ToastException(R.string.a_wallet_with_that_name_already_exists_please)
                }
                val password = confirmPassword(dialog)

                val nextDialog: DialogFragment
                val arguments = Bundle().apply {
                    putString("name", name)
                    putString("password", password)
                }

                val walletType = dialog.spnType.selectedItemId.toInt()
                if (walletType in listOf(R.id.menuCreateSeed, R.id.menuRestoreSeed)) {
                    nextDialog = NewWalletSeedDialog()
                    val seed = if (walletType == R.id.menuCreateSeed)
                                   daemonModel.commands.callAttr("make_seed").toString()
                               else null
                    arguments.putString("seed", seed)
                } else if (walletType == R.id.menuImport) {
                    nextDialog = NewWalletImportDialog()
                } else {
                    throw Exception("Unknown item: ${dialog.spnType.selectedItem}")
                }
                showDialog(activity!!, nextDialog.apply { setArguments(arguments) })
                dismiss()
            } catch (e: ToastException) { e.show() }
        }
    }
}


fun confirmPassword(dialog: Dialog): String {
    val password = dialog.etPassword.text.toString()
    if (password.isEmpty()) throw ToastException(R.string.enter_password)
    if (password != dialog.etConfirmPassword.text.toString()) {
        throw ToastException(R.string.wallet_passwords)
    }
    return password
}


abstract class NewWalletDialog2 : AlertDialogFragment() {
    class Model : ViewModel() {
        val result = MutableLiveData<Boolean>()
    }
    private val model by lazy { ViewModelProviders.of(this).get(Model::class.java) }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.new_wallet)
            .setView(R.layout.text_input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        super.onShowDialog(dialog)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            model.result.value = null
            showDialog(activity!!, ProgressDialogFragment())
            Thread {
                try {
                    val name = arguments!!.getString("name")!!
                    val password = arguments!!.getString("password")!!
                    onCreateWallet(name, password, dialog.etInput.text.toString())
                    daemonModel.loadWallet(name, password)
                    model.result.postValue(true)
                } catch (e: ToastException) {
                    e.show()
                    model.result.postValue(false)
                }
            }.start()
        }
        model.result.observe(this, Observer { onResult(it) })
    }

    abstract fun onCreateWallet(name: String, password: String, input: String)

    fun onResult(success: Boolean?) {
        if (success == null) return
        dismissDialog(activity!!, ProgressDialogFragment::class)
        if (success) {
            dismiss()
        }
    }
}


class NewWalletSeedDialog : NewWalletDialog2() {
    override fun onShowDialog(dialog: AlertDialog) {
        super.onShowDialog(dialog)
        setupSeedDialog(this)
    }

    override fun onCreateWallet(name: String, password: String, input: String) {
        try {
            daemonModel.createWallet(name, password, "seed", input)
        } catch (e: PyException) {
            if (e.message!!.startsWith("InvalidSeed")) {
                throw ToastException(R.string.the_seed_you_entered_does_not_appear)
            }
            throw e
        }
    }
}


class NewWalletImportDialog : NewWalletDialog2() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        super.onBuildDialog(builder)
        builder.setNeutralButton(R.string.scan_qr, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        super.onShowDialog(dialog)
        dialog.tvPrompt.setText(R.string.enter_a_list_of_bitcoin)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { scanQR(this) }
    }

    override fun onCreateWallet(name: String, password: String, input: String) {
        var foundAddress = false
        var foundPrivkey = false
        for (word in input.split(Regex("\\s+"))) {
            if (word.isEmpty()) {
                // Can happen at start or end of list.
            } else if (clsAddress.callAttr("is_valid", word).toBoolean()) {
                foundAddress = true
            } else if (libBitcoin.callAttr("is_private_key", word).toBoolean()) {
                foundPrivkey = true
            } else {
                throw ToastException(getString(R.string.not_a_valid, word))
            }
        }

        if (foundAddress) {
            if (foundPrivkey) {
                throw ToastException(R.string.cannot_specify_short)
            }
            daemonModel.createWallet(name, password, "addresses", input)
        } else if (foundPrivkey) {
            daemonModel.createWallet(name, password, "privkeys", input)
        } else {
            throw ToastException(R.string.you_appear_to_have_entered_no)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            val text = dialog.etInput.text
            if (!text.isEmpty() && !text.endsWith("\n")) {
                text.append("\n")
            }
            text.append(result.contents)
            Selection.setSelection(text, text.length)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}


class DeleteWalletDialog : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        val walletName = daemonModel.walletName
        val message = getString(R.string.do_you_want_to_delete, walletName) + "\n\n" +
                      getString(R.string.if_your_wallet)
        builder.setTitle(R.string.delete_wallet)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                daemonModel.commands.callAttr("delete_wallet", walletName)
            }
            .setNegativeButton(android.R.string.cancel, null)
    }
}


class OpenWalletDialog : PasswordDialog(runInBackground = true) {
    override fun onPassword(password: String) {
        daemonModel.loadWallet(arguments!!.getString("walletName")!!, password)
    }
}


class CloseWalletDialog : ProgressDialogTask() {
    override fun doInBackground() {
        daemonModel.commands.callAttr("close_wallet")
    }

    override fun onPostExecute() {
        (activity as MainActivity).openDrawer()
    }
}


class ChangePasswordDialog : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.change_password)
            .setView(R.layout.change_password)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            try {
                val currentPassword = dialog.etCurrentPassword.text.toString()
                val newPassword = confirmPassword(dialog)
                try {
                    daemonModel.wallet!!.callAttr("update_password",
                                                  currentPassword, newPassword, true)
                    toast(R.string.password_was)
                    dismiss()
                } catch (e: PyException) {
                    throw if (e.message!!.startsWith("InvalidPassword"))
                        ToastException(R.string.password_incorrect, Toast.LENGTH_SHORT) else e
                }
            } catch (e: ToastException) {
                e.show()
            }
        }
    }
}


class ShowSeedPasswordDialog : PasswordDialog() {
    override fun onPassword(password: String) {
        val seed = daemonModel.wallet!!.callAttr("get_seed", password).toString()
        if (! seed.contains(" ")) {
            // get_seed(None) doesn't throw an exception, but returns the encrypted base64 seed.
            throw PyException("InvalidPassword")
        }
        showDialog(activity!!, SeedDialog().apply { arguments = Bundle().apply {
            putString("seed", seed)
        }})
    }
}

open class SeedDialog : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.wallet_seed)
            .setView(R.layout.text_input)
            .setPositiveButton(android.R.string.ok, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        setupSeedDialog(this)
    }
}


fun setupSeedDialog(fragment: DialogFragment) {
    val tvPrompt = fragment.dialog.tvPrompt
    val etInput = fragment.dialog.etInput
    val seed = fragment.arguments!!.getString("seed")
    if (seed == null) {
        tvPrompt.setText(R.string.please_enter_your_seed_phrase)
    } else {
        tvPrompt.setText(seedAdvice(seed))
        etInput.setText(seed)
        etInput.setFocusable(false)
    }
}


fun seedAdvice(seed: String): String {
    return app.getString(R.string.please_save, seed.split(" ").size) + " " +
           app.getString(R.string.this_seed) + " " +
           app.getString(R.string.never_disclose)
}

