package org.electroncash.electroncash3

import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.chaquo.python.PyObject
import kotlinx.android.synthetic.main.addresses.*


val guiAddresses by lazy { guiMod("addresses") }
val libAddress by lazy { libMod("address") }
val clsAddress by lazy { libAddress["Address"]!! }
val clsNetworks by lazy { libNetworks["net"]!! }


class AddressesFragment : Fragment(), MainFragment {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.addresses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupVerticalList(rvAddresses)
        daemonUpdate.observe(viewLifecycleOwner, Observer {
            val wallet = daemonModel.wallet
            rvAddresses.adapter =
                if (wallet == null) null
                else AddressesAdapter(activity!!, wallet)
        })
        settings.getBoolean("cashaddr_format").observe(viewLifecycleOwner, Observer {
            rvAddresses.adapter?.notifyDataSetChanged()
        })
    }
}


class AddressesAdapter(val activity: FragmentActivity, val wallet: PyObject)
    : BoundAdapter<AddressModel>(R.layout.address_list) {

    val addresses = wallet.callAttr("get_addresses").asList()

    override fun getItem(position: Int): AddressModel {
        return AddressModel(wallet, addresses.get(position))
    }

    override fun getItemCount(): Int {
        return addresses.size
    }

    override fun onBindViewHolder(holder: BoundViewHolder<AddressModel>, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.itemView.setOnClickListener {
            showDialog(activity, AddressDialog(holder.item.addrString))
        }
    }
}

class AddressModel(val wallet: PyObject, val addr: PyObject) {
    val addrString
        get() = addr.callAttr("to_ui_string").toString()

    // get_addr_balance returns the tuple (confirmed, unconfirmed, unmatured)
    val balance
        get() = formatSatoshis(wallet.callAttr("get_addr_balance", addr)
                                   .asList().get(0).toLong())

    val type
        get() = app.getString(if (wallet.callAttr("is_change", addr).toBoolean())
                                  R.string.change else R.string.receiving)
}


class AddressDialog() : MenuDialog() {
    constructor(address: String) : this() {
        arguments = Bundle().apply { putString("address", address) }
    }
    val address by lazy { arguments!!.getString("address")!! }

    override fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu,
                               inflater: MenuInflater) {
        builder.setTitle(address)
        inflater.inflate(R.menu.address, menu)
    }

    override fun onMenuItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menuCopy -> {
                copyToClipboard(
                    if (clsAddress["FMT_UI"] == clsAddress["FMT_LEGACY"]) address
                    else clsNetworks["CASHADDR_PREFIX"].toString() + ":" + address,
                    R.string.address_copied)
            }
            R.id.menuExplorer -> exploreAddress(activity!!, address)
            else -> throw Exception("Unknown item $item")
        }
    }
}