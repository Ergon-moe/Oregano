package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaquo.python.PyObject
import kotlinx.android.synthetic.main.address_detail.*
import kotlinx.android.synthetic.main.addresses.*


val libAddress by lazy { libMod("address") }
val clsAddress by lazy { libAddress["Address"]!! }


val addressLabelUpdate = MutableLiveData<Unit>().apply { value = Unit }


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

        addressLabelUpdate.observe(viewLifecycleOwner, Observer {
            rvAddresses.adapter?.notifyDataSetChanged()
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
            showDialog(activity, AddressDialog(holder.item.uiString))
        }
    }
}


class AddressModel(val wallet: PyObject, val addr: PyObject) {
    val uiString
        get() = addr.callAttr("to_ui_string").toString()

    val storageString
        get() = addr.callAttr("to_storage_string").toString()

    val status
        get() = app.getString(if (history.isEmpty()) R.string.unused
                              else if (balance > 0) R.string.balance
                              else R.string.used)

    // get_addr_balance returns the tuple (confirmed, unconfirmed, unmatured)
    val balance
        get() = wallet.callAttr("get_addr_balance", addr).asList().get(0).toLong()

    val history
        get() =  wallet.callAttr("get_address_history", addr).asList()

    val type
        get() = app.getString(if (wallet.callAttr("is_change", addr).toBoolean())
                                  R.string.change else R.string.receiving)

    val description
        get() = wallet.callAttr("get_label", storageString).toString()
}


class AddressDialog() : AlertDialogFragment() {
    constructor(address: String) : this() {
        arguments = Bundle().apply { putString("address", address) }
    }
    val addrModel by lazy {
        AddressModel(daemonModel.wallet!!,
                     clsAddress.callAttr("from_string",
                                         arguments!!.getString("address")!!))
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        with (builder) {
            setView(R.layout.address_detail)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok, { _, _  ->
                setDescription(addrModel.storageString, dialog.etDescription.text.toString())
                addressLabelUpdate.setValue(Unit)
            })
        }
    }

    override fun onShowDialog(dialog: AlertDialog) {
        val fullString = addrModel.addr.callAttr("to_full_ui_string").toString()
        dialog.btnExplore.setOnClickListener {
            exploreAddress(activity!!, addrModel.addr)
        }
        dialog.btnCopy.setOnClickListener {
            copyToClipboard(fullString, R.string.address)
        }
        showQR(dialog.imgQR, fullString)
        dialog.tvAddress.text = addrModel.uiString
        dialog.tvType.text = addrModel.type
        dialog.etDescription.setText(addrModel.description)
    }
}