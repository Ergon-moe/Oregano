package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModel
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.SeekBar
import android.widget.Toast
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.android.synthetic.main.amount_box.*
import kotlinx.android.synthetic.main.send.*


val MIN_FEE = 1
val MAX_FEE = 10


class SendDialog : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setTitle(R.string.send)
            .setView(R.layout.send)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton(R.string.qr_code, null)
    }

    override fun onShowDialog(dialog: AlertDialog) {
        val address = arguments?.getString("address")
        if (address != null) {
            dialog.etAddress.setText(address)
            dialog.etAmount.requestFocus()
        }
        val uri = arguments?.getString("uri")
        if (uri != null) {
            onUri(uri)
        }

        dialog.btnContacts.setOnClickListener {
            showDialog(activity!!, SendContactsDialog())
        }

        dialog.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!dialog.btnMax.isChecked) {  // Avoid infinite recursion.
                    updateUI()
                }
            }
        })
        dialog.tvUnit.setText(unitName)
        dialog.btnMax.setOnCheckedChangeListener { _, _ -> updateUI() }

        with (dialog.sbFee) {
            // setMin is not available until API level 26, so values are offset by MIN_FEE.
            progress = (daemonModel.config.callAttr("fee_per_kb").toInt() / 1000
                        - MIN_FEE)
            max = MAX_FEE - MIN_FEE
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    daemonModel.config.callAttr("set_key", "fee_per_kb", feeSpb * 1000)
                    updateUI()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        fiatUpdate.observe(this, Observer { updateUI() })
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOK() }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { scanQR(this) }
    }

    fun updateUI() {
        var addrOrDummy: String
        try {
            makeAddress(address)
            addrOrDummy = address
        } catch (e: ToastException) {
            addrOrDummy = daemonModel.wallet!!.callAttr("dummy_address")
                            .callAttr("to_ui_string").toString()
        }

        var tx: PyObject? = null
        dialog.etAmount.isEnabled = !dialog.btnMax.isChecked
        if (dialog.btnMax.isChecked) {
            try {
                tx = daemonModel.makeTx(addrOrDummy, null, unsigned=true)
                dialog.etAmount.setText(formatSatoshis(tx.callAttr("output_value").toLong()))
            } catch (e: ToastException) {}
        }
        amountBoxUpdate(dialog)

        var feeLabel = getString(R.string.sat_byte, feeSpb)
        try {
            if (tx == null) {
                tx = daemonModel.makeTx(addrOrDummy, amountBoxGet(dialog), unsigned = true)
            }
            val fee = tx.callAttr("get_fee").toLong()
            feeLabel += " (${formatSatoshisAndUnit(fee)})"
        } catch (e: ToastException) {}
        dialog.tvFeeLabel.setText(feeLabel)
    }

    // Receives the result of a QR scan.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            onUri(result.contents)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun onUri(uri: String) {
        try {
            val parsed: PyObject
            try {
                parsed = libWeb.callAttr("parse_URI", uri)!!
            } catch (e: PyException) {
                throw ToastException(e)
            }
            val address = parsed.callAttr("get", "address")
            if (address != null) {
                dialog.etAddress.setText(address.toString())
            }
            val amount = parsed.callAttr("get", "amount")
            if (amount != null) {
                dialog.etAmount.setText(formatSatoshis(amount.toLong()))
            }
            val description = parsed.callAttr("get", "message")
            if (description != null) {
                dialog.etDescription.setText(description.toString())
            }
        } catch (e: ToastException) {
            e.show()
        }
    }

    fun onOK() {
        try {
            val amount = amountBoxGet(dialog)
            daemonModel.makeTx(address, amount, unsigned=true)
            showDialog(activity!!, SendPasswordDialog().apply { arguments = Bundle().apply {
                putString("address", address)
                putLong("amount", amount)
                putString("description", this@SendDialog.dialog.etDescription.text.toString())
            }})
        } catch (e: ToastException) { e.show() }
        // Don't dismiss this dialog yet: the user might want to come back to it.
    }

    val address
        get() = dialog.etAddress.text.toString()

    val feeSpb
        get() = MIN_FEE + dialog.sbFee.progress
}


class SendContactsDialog : MenuDialog() {
    val contacts = listContacts()

    override fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu) {
        builder.setTitle(R.string.contacts)
        for (name in contacts.keys) {
            menu.add(name)
        }
    }

    override fun onShowDialog(dialog: AlertDialog) {
        if (contacts.isEmpty()) {
            toast(R.string.you_dont, Toast.LENGTH_LONG)
            dismiss()
        }
    }

    override fun onMenuItemSelected(item: MenuItem) {
        val address = contacts.get(item.title.toString())!!.callAttr("to_ui_string").toString()
        with (findDialog(activity!!, SendDialog::class)!!) {
            dialog.etAddress.setText(address)
            dialog.etAmount.requestFocus()
        }
    }
}


class SendPasswordDialog : PasswordDialog(runInBackground = true) {
    class Model : ViewModel() {
        val result = MutableLiveData<List<PyObject>>()
    }
    private val model by lazy { ViewModelProviders.of(this).get(Model::class.java) }

    override fun onShowDialog(dialog: AlertDialog) {
        super.onShowDialog(dialog)
        model.result.observe(this, Observer { onResult(it!!) })
    }

    override fun onPassword(password: String) {
        val tx = daemonModel.makeTx(arguments!!.getString("address")!!,
                                    arguments!!.getLong("amount"), password)
        if (! daemonModel.isConnected()) {
            throw ToastException(R.string.not_connected)
        }
        model.result.postValue(
            daemonModel.network.callAttr("broadcast_transaction", tx).asList())
    }

    fun onResult(result: List<PyObject>) {
        val success = result.get(0).toBoolean()
        if (success) {
            dismissDialog(activity!!, SendDialog::class)
            toast(R.string.payment_sent)
            val txid = result.get(1).toString()
            setDescription(txid, arguments!!.getString("description")!!)
            transactionsUpdate.setValue(Unit)
        } else {
            var message = result.get(1).toString()
            val reError = Regex("^error: (.*)")
            if (message.contains(reError)) {
                message = message.replace(reError, "$1")
            }
            toast(message, Toast.LENGTH_LONG)
        }
    }
}
