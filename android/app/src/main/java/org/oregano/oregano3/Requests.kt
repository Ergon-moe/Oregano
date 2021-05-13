package org.oregano.oregano3

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.chaquo.python.PyObject
import kotlinx.android.synthetic.main.main.*
import kotlinx.android.synthetic.main.request_detail.*
import kotlinx.android.synthetic.main.requests.*


class RequestsFragment : ListFragment(R.layout.requests, R.id.rvRequests) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addSource(daemonUpdate)
        addSource(settings.getString("base_unit"))

        btnAdd.setOnClickListener { newRequest(activity!!) }
    }

    override fun onCreateAdapter() =
        ListAdapter(this, R.layout.request_list, ::RequestModel, ::RequestDialog)

    override fun onRefresh(wallet: PyObject) =
        wallet.callAttr("get_sorted_requests", daemonModel.config)!!
}


fun newRequest(activity: FragmentActivity) {
    try {
        val address = daemonModel.wallet!!.callAttr("get_unused_address")
                      ?: throw ToastException(R.string.no_more)
        showDialog(activity, RequestDialog(address.callAttr("to_storage_string").toString()))
    } catch (e: ToastException) { e.show() }
}


class RequestModel(val request: PyObject) : ListModel {
    val address by lazy { getField("address").toString() }
    val amount = getField("amount").toLong()
    val timestamp = formatTime(getField("time").toLong())
    val description = getField("memo").toString()
    val status = (app.resources.getStringArray(R.array.payment_status)
                  [getField("status").toInt()])

    private fun getField(key: String): PyObject {
        return request.callAttr("get", key)!!
    }

    override val dialogArguments by lazy {
        Bundle().apply { putString("address", address) }
    }
}


class RequestDialog() : AlertDialogFragment() {
    val wallet by lazy { daemonModel.wallet!! }

    init {
        if (wallet.callAttr("is_watching_only").toBoolean()) {
            throw ToastException(R.string.this_wallet_is)
        }
    }

    val address by lazy {
        clsAddress.callAttr("from_string", arguments!!.getString("address"))
    }
    val existingRequest by lazy {
        wallet.callAttr("get_payment_request", address, daemonModel.config)
    }
    lateinit var amountBox: AmountBox

    constructor(address: String): this() {
        arguments = Bundle().apply { putString("address", address) }
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        with (builder) {
            setView(R.layout.request_detail)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok, null)
            if (existingRequest != null) {
                setNeutralButton(R.string.delete, null)
            }
        }
    }

    override fun onShowDialog() {
        amountBox = AmountBox(dialog)
        amountBox.listener = { updateUI() }

        btnCopy.setOnClickListener {
            copyToClipboard(getUri(), R.string.request_uri)
        }
        tvAddress.text = address.callAttr("to_ui_string").toString()

        etDescription.addAfterTextChangedListener { updateUI() }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOK() }

        if (existingRequest != null) {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                showDialog(this, RequestDeleteDialog(address))
            }
        }
        updateUI()
    }

    override fun onFirstShowDialog() {
        val request = existingRequest
        if (request != null) {
            val model = RequestModel(request)
            amountBox.amount = model.amount
            etDescription.setText(model.description)
        } else {
            amountBox.requestFocus()
        }
    }

    private fun updateUI() {
        showQR(imgQR, getUri())
    }

    private fun getUri(): String {
        val amount = try {
            amountBox.amount
        } catch (e: ToastException) { null }
        return libWeb.callAttr("create_URI", address, amount, description).toString()
    }

    private fun onOK() {
        try {
            wallet.callAttr(
                "add_payment_request",
                wallet.callAttr("make_payment_request", address, amountBox.amount, description),
                daemonModel.config)
        } catch (e: ToastException) { e.show() }

        daemonUpdate.setValue(Unit)
        dismiss()

        // If the dialog was opened from the Transactions screen, we should now switch to
        // the Requests screen so the user can verify that the request has been saved.
        (activity as MainActivity).navBottom.selectedItemId = R.id.navRequests
    }

    val description
        get() = etDescription.text.toString()
}


class RequestDeleteDialog() : AlertDialogFragment() {
    constructor(addr: PyObject) : this() {
        arguments = Bundle().apply {
            putString("address", addr.callAttr("to_storage_string").toString())
        }
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.confirm_delete)
            .setMessage(R.string.are_you_sure_you_wish_to_proceed)
            .setPositiveButton(R.string.delete) { _, _ ->
                daemonModel.wallet!!.callAttr("remove_payment_request",
                                              makeAddress(arguments!!.getString("address")!!),
                                              daemonModel.config)
                daemonUpdate.setValue(Unit)
                (targetFragment as RequestDialog).dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
    }
}
