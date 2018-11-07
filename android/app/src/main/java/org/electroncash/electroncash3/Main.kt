package org.electroncash.electroncash3

import android.app.Dialog
import android.app.ProgressDialog
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.main.*
import kotlin.reflect.KClass


class MainActivity : AppCompatActivity() {
    companion object {
        // TODO: integrate console into MainActivity and remove this.
        var instance: MainActivity? = null

        val FRAGMENTS = HashMap<Int, KClass<out Fragment>>().apply {
            put(R.id.navWallets, WalletsFragment::class)
            put(R.id.navAddresses, AddressesFragment::class)
            put(R.id.navSettings, SettingsFragment::class)
        }
    }

    val daemonModel by lazy { ViewModelProviders.of(this).get(DaemonModel::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        instance = this
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)
        navigation.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.navConsole -> {
                    startActivity(Intent(this, ECConsoleActivity::class.java))
                    false
                }
                else -> {
                    showFragment(it.itemId)
                    true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        showFragment(navigation.selectedItemId)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun showFragment(id: Int) {
        val ft = supportFragmentManager.beginTransaction()
        for (frag in supportFragmentManager.fragments) {
            if (frag is MainFragment) {
                ft.detach(frag)
                frag.title.removeObservers(this)
                frag.subtitle.removeObservers(this)
            }
        }
        val newFrag = getFragment(id)
        ft.attach(newFrag)
        if (newFrag is MainFragment) {
            newFrag.title.observe(this, Observer { setTitle(it ?: "") })
            newFrag.subtitle.observe(this, Observer { supportActionBar!!.setSubtitle(it) })
        }
        ft.commit()
    }

    private fun getFragment(id: Int): Fragment {
        val tag = "MainFragment:$id"
        var frag = supportFragmentManager.findFragmentByTag(tag)
        if (frag != null) {
            return frag
        } else {
            frag = FRAGMENTS[id]!!.java.newInstance()
            supportFragmentManager.beginTransaction()
                .add(flContent.id, frag, tag).commit()
            return frag
        }
    }
}

val Fragment.mainActivity
    get() = activity as MainActivity

val Fragment.daemonModel
    get() = mainActivity.daemonModel


interface MainFragment {
    // To control the title or subtitle, override these with a MutableLiveData.
    val title: LiveData<String>
        get() = MutableLiveData<String>().apply { value = "" }
    val subtitle: LiveData<String>
        get() = MutableLiveData<String>().apply { value = null }
}

open class AlertDialogFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(context!!)
        onBuildDialog(builder)
        val dialog = builder.create()
        dialog.setOnShowListener { onShowDialog(dialog) }
        return dialog
    }

    open fun onBuildDialog(builder: AlertDialog.Builder) {}

    /** Can be used to do things like configure custom views, or attach listeners to buttons so
     *  they don't always close the dialog. */
    open fun onShowDialog(dialog: AlertDialog) {}
}

open class MessageDialog() : AlertDialogFragment() {
    constructor(title: String, message: String) : this() {
        arguments = Bundle().apply {
            putString("title", title)
            putString("message", message)
        }
    }
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(arguments!!.getString("title"))
            .setMessage(arguments!!.getString("message"))
            .setPositiveButton(android.R.string.ok, null)
    }
}

class ProgressDialogFragment : AlertDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return ProgressDialog(context).apply {
            setMessage(getString(R.string.please_wait))
        }
    }
}