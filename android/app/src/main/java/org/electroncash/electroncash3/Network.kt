package org.electroncash.electroncash3

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.EditTextPreferenceDialogFragmentCompat
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import com.chaquo.python.PyException


private val PROTOCOL_SUFFIX = ":s"

val libNetwork by lazy { libMod("network") }


fun initNetwork() {
    settings.getBoolean("auto_connect").observeForever { updateNetwork() }
    settings.getString("server").observeForever { updateNetwork() }
}


private fun updateNetwork() {
    daemonModel.network.callAttr("load_parameters")
}


// Hide the protocol suffix in the UI, but include it in the config setting because the
// back end requires it.
@Suppress("unused")
class ServerPreference: EditTextPreference {
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int)
        : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int)
        : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?)
        : super(context, attrs)
    constructor(context: Context?)
        : super(context)

    override fun getText(): String {
        var text = super.getText()
        if (text.endsWith(PROTOCOL_SUFFIX)) {
            text = text.dropLast(PROTOCOL_SUFFIX.length)
        }
        return text
    }

    // This method is called with the UI text by the dialog, and with the config text by the
    // base class during startup. So it needs to accept both formats, plus the empty string
    // which means to choose a random server.
    override fun setText(textIn: String) {
        var text = textIn
        if (!text.isEmpty()) {
            if (!text.endsWith(PROTOCOL_SUFFIX)) {
                text += PROTOCOL_SUFFIX
            }
            try {
                libNetwork.callAttr("deserialize_server", text)
            } catch (e: PyException) {
                throw InvalidServerException(e)
            }
        }
        super.setText(text)
    }
}


class ServerPreferenceDialog: EditTextPreferenceDialogFragmentCompat() {
    private lateinit var editText: EditText

    override fun onBindDialogView(view: View) {
        editText = view.findViewById(android.R.id.edit)!!
        editText.setHint(getString(R.string.host) + ":" + getString(R.string.port))
        editText.inputType = InputType.TYPE_CLASS_TEXT + InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        super.onBindDialogView(view)  // Do last: setting inputType resets cursor position.
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    (preference as EditTextPreference).setText(editText.text.toString())
                    onClick(dialog, AlertDialog.BUTTON_POSITIVE)
                    dismiss()
                } catch (e: InvalidServerException) {
                    toast(R.string.invalid_address)
                }
            }
        }
        return dialog
    }
}


class InvalidServerException(e: Throwable) : IllegalArgumentException(e)