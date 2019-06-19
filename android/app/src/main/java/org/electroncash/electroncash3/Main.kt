package org.electroncash.electroncash3

import android.app.Activity
import android.arch.lifecycle.Observer
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import kotlinx.android.synthetic.main.main.*
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
    put(R.id.navWallets, WalletsFragment::class)
    put(R.id.navRequests, RequestsFragment::class)
    put(R.id.navAddresses, AddressesFragment::class)
    put(R.id.navContacts, ContactsFragment::class)
}


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
        navDrawer.inflateMenu(R.menu.nav_drawer)
        for (walletName in daemonModel.listWallets()) {
            val item = menu.add(R.id.navWallets, Menu.NONE, Menu.NONE, walletName)
            item.setIcon(R.drawable.ic_wallet_24dp)
            if (walletName == loadedWalletName) {
                item.setCheckable(true)
                item.setChecked(true)
            }
        }
    }

    fun onDrawerItemSelected(item: MenuItem): Boolean {
        val activityCls = ACTIVITIES[item.itemId]
        if (activityCls != null) {
            startActivity(Intent(this, activityCls.java))
        } else if (item.itemId == R.id.navNewWallet) {
            showDialog(this, NewWalletDialog1())
        } else if (item.itemId == Menu.NONE) {
            showDialog(this, OpenWalletDialog().apply { arguments = Bundle().apply {
                putString("walletName", item.title.toString())
            }})
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


interface MainFragment