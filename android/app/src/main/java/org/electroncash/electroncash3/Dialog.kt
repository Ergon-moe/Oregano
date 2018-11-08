package org.electroncash.electroncash3

import android.app.Dialog
import android.app.ProgressDialog
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.PopupMenu


abstract class AlertDialogFragment : DialogFragment() {
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


class MessageDialog() : AlertDialogFragment() {
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


abstract class MenuDialog : AlertDialogFragment() {
    override fun onBuildDialog(builder: AlertDialog.Builder) {
        val menu = PopupMenu(app, null).menu
        onBuildDialog(builder, menu, MenuInflater(app))
        val items = Array(menu.size()) {
            menu.getItem(it).title
        }
        builder.setItems(items) { dialog, index ->
            onMenuItemSelected(menu.getItem(index))
        }
    }

    abstract fun onBuildDialog(builder: AlertDialog.Builder, menu: Menu,
                               inflater: MenuInflater)
    abstract fun onMenuItemSelected(item: MenuItem)
}


class ProgressDialogFragment : AlertDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        isCancelable = false
        return ProgressDialog(context).apply {
            setMessage(getString(R.string.please_wait))
        }
    }
}