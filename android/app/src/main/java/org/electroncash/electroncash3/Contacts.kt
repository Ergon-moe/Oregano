package org.electroncash.electroncash3

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaquo.python.PyObject
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.android.synthetic.main.contact_detail.*
import kotlinx.android.synthetic.main.contacts.*
import java.util.*
import kotlin.collections.ArrayList


val contactsUpdate = MutableLiveData<Unit>().apply { value = Unit }


class ContactsFragment : Fragment(), MainFragment {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupVerticalList(rvContacts)
        val observer = Observer<Unit> {
            val wallet = daemonModel.wallet
            if (wallet == null) {
                rvContacts.adapter = null
                btnAdd.hide()
            } else {
                rvContacts.adapter = ContactsAdapter(activity!!, listContacts())
                btnAdd.show()
            }
        }
        daemonUpdate.observe(viewLifecycleOwner, observer)
        contactsUpdate.observe(viewLifecycleOwner, observer)
        settings.getBoolean("cashaddr_format").observe(viewLifecycleOwner, Observer {
            rvContacts.adapter?.notifyDataSetChanged()
        })

        btnAdd.setOnClickListener { showDialog(activity!!, ContactDialog()) }
    }
}


class ContactsAdapter(val activity: FragmentActivity, val contacts: Map<String, PyObject>)
    : BoundAdapter<ContactModel>(R.layout.contact_list) {

    val names = ArrayList<String>(contacts.keys)

    override fun getItemCount(): Int {
        return contacts.size
    }

    override fun getItem(position: Int): ContactModel {
        val name = names.get(position)
        return ContactModel(name, contacts.get(name)!!)
    }

    override fun onBindViewHolder(holder: BoundViewHolder<ContactModel>, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.itemView.setOnClickListener {
            showDialog(activity, ContactDialog().apply {
                arguments = Bundle().apply {
                    putString("address", holder.item.addrStorageString)
                }
            })
        }
    }
}


class ContactModel(val name: String, val addr: PyObject) {
    val addrUiString
        get() = addr.callAttr("to_ui_string").toString()
    val addrStorageString
        get() = addr.callAttr("to_storage_string").toString()

}


class ContactDialog : AlertDialogFragment() {
    val contacts by lazy { daemonModel.wallet!!.get("contacts")!! }
    val existingContact by lazy {
        val address = arguments?.getString("address")
        if (address == null) null
        else ContactModel(
            contacts.asMap().get(PyObject.fromJava(address))!!.asList().get(1).toString(),
            makeAddress(address))
    }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        with (builder) {
            setView(R.layout.contact_detail)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(android.R.string.ok, null)
            setNeutralButton(if (existingContact == null) R.string.qr_code
                             else R.string.delete,
                             null)
        }
    }

    override fun onShowDialog(dialog: AlertDialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOK() }

        val contact = existingContact
        if (contact == null) {
            for (btn in listOf(dialog.btnExplore, dialog.btnSend)) {
                (btn as View).visibility = View.INVISIBLE
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { scanQR(this) }
        } else {
            dialog.etName.setText(contact.name)
            with(dialog.etAddress) {
                setText(contact.addrUiString)
                setFocusable(false)
                setOnClickListener { copyToClipboard(text, R.string.address) }
            }
            dialog.btnExplore.setOnClickListener {
                exploreAddress(activity!!, contact.addr)
            }
            dialog.btnSend.setOnClickListener {
                dismiss()
                showDialog(activity!!, SendDialog().apply {
                    arguments = Bundle().apply {
                        putString("address", contact.addrUiString)
                    }
                })
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { onDelete() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null && result.contents != null) {
            dialog.etAddress.setText(result.contents)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun onOK() {
        val name = dialog.etName.text.toString()
        val address = dialog.etAddress.text.toString()
        try {
            if (name.isEmpty()) {
                throw ToastException(R.string.name_is)
            }
            contacts.asMap().put(makeAddress(address).callAttr("to_storage_string"),
                                 py.builtins.callAttr("tuple", arrayOf("address", name)))
            contactsUpdate.setValue(Unit)
            dismiss()
        } catch (e: ToastException) { e.show() }
    }

    fun onDelete() {
        contacts.callAttr("pop", existingContact!!.addrStorageString)
        contactsUpdate.setValue(Unit)
        dismiss()
    }
}


fun listContacts(): Map<String, PyObject> {
    val contacts = TreeMap<String, PyObject>()
    for (entry in daemonModel.wallet!!.get("contacts")!!.asMap().entries) {
        contacts.put(entry.value.asList().get(1).toString(),
                     makeAddress(entry.key.toString()))
    }
    return contacts
}
