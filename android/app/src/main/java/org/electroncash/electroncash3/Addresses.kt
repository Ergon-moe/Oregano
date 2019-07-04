package org.electroncash.electroncash3

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MutableLiveData
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
import android.widget.Button
import com.chaquo.python.PyObject
import kotlinx.android.synthetic.main.address_detail.*
import kotlinx.android.synthetic.main.addresses.*


val libAddress by lazy { libMod("address") }
val clsAddress by lazy { libAddress["Address"]!! }


val addressFilterType = MutableLiveData<Int>().apply { value = R.id.filterAll }
val addressFilterStatus = MutableLiveData<Int>().apply { value = R.id.filterAll }
val addressLabelUpdate = MutableLiveData<Unit>().apply { value = Unit }


class AddressesFragment : Fragment(), MainFragment {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.addresses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        btnType.setOnClickListener { showDialog(activity!!, FilterTypeDialog()) }
        btnStatus.setOnClickListener { showDialog(activity!!, FilterStatusDialog()) }

        setupVerticalList(rvAddresses)
        daemonUpdate.observe(viewLifecycleOwner, Observer { refresh() })
        for (filter in listOf(addressFilterType, addressFilterStatus)) {
            filter.observe(viewLifecycleOwner, Observer { refresh() })
        }

        addressLabelUpdate.observe(viewLifecycleOwner, Observer { rebind() })
        settings.getBoolean("cashaddr_format").observe(viewLifecycleOwner,
                                                       Observer { rebind() })
    }

    fun refresh() {
        setFilterLabel(btnType, R.string.type, R.menu.filter_type, addressFilterType)
        setFilterLabel(btnStatus, R.string.status, R.menu.filter_status, addressFilterStatus)

        val wallet = daemonModel.wallet
        for (btn in listOf(btnType, btnStatus)) {
            btn.isEnabled = (wallet != null)
        }
        rvAddresses.adapter =
            if (wallet == null) null
            else AddressesAdapter(activity!!, wallet)
    }

    fun setFilterLabel(btn: Button, prefix: Int, menuId: Int, liveData: LiveData<Int>) {
        val menu = inflateMenu(menuId)
        btn.setText("${getString(prefix)}: ${menu.findItem(liveData.value!!).title}")
    }

    fun rebind() {
        rvAddresses.adapter?.notifyDataSetChanged()
    }
}


class AddressesAdapter(val activity: FragmentActivity, val wallet: PyObject)
    : BoundAdapter<AddressModel>(R.layout.address_list) {

    val addresses = ArrayList<AddressModel>()

    init {
        for (addr in wallet.callAttr("get_addresses").asList()) {
            val am = AddressModel(wallet, addr)
            if (passesFilter(am)) {
                addresses.add(am)
            }
        }
    }

    fun passesFilter(am: AddressModel): Boolean {
        when (addressFilterType.value) {
            R.id.filterReceiving -> { if (am.isChange) return false }
            R.id.filterChange -> { if (!am.isChange) return false }
        }
        when (addressFilterStatus.value) {
            R.id.filterUnused -> { if (!am.history.isEmpty()) return false }
            R.id.filterFunded -> { if (am.balance == 0L) return false }
            R.id.filterUsed -> {
                if (am.history.isEmpty() || am.balance != 0L) return false
            }
        }
        return true
    }

    override fun getItem(position: Int): AddressModel {
        return addresses.get(position)
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
                              else if (balance != 0L) R.string.balance
                              else R.string.used)

    // get_addr_balance returns the tuple (confirmed, unconfirmed, unmatured)
    val balance
        get() = wallet.callAttr("get_addr_balance", addr).asList().get(0).toLong()

    val history by lazy {
        wallet.callAttr("get_address_history", addr).asList()
    }

    val type
        get() = app.getString(if (isChange) R.string.change else R.string.receiving)

    val isChange
        get() = wallet.callAttr("is_change", addr).toBoolean()

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


abstract class FilterDialog : MenuDialog() {
    lateinit var liveData: MutableLiveData<Int>

    fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu, titleId: Int, menuId: Int,
                      liveData: MutableLiveData<Int>) {
        this.liveData = liveData
        builder.setTitle(titleId)
        MenuInflater(app).inflate(menuId, menu)
        menu.findItem(liveData.value!!).isChecked = true
    }

    override fun onMenuItemSelected(item: MenuItem) {
        liveData.value = item.itemId
        dismiss()
    }
}

class FilterTypeDialog : FilterDialog() {
    override fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu) {
        onBuildDialog(builder, menu, R.string.type, R.menu.filter_type, addressFilterType)
    }
}

class FilterStatusDialog : FilterDialog() {
    override fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu) {
        onBuildDialog(builder, menu, R.string.status, R.menu.filter_status,
                      addressFilterStatus)
    }
}
