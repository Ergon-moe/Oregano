package org.electroncash.electroncash3

import android.annotation.SuppressLint
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Observer
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.chaquo.python.Kwarg
import com.chaquo.python.PyObject
import kotlinx.android.synthetic.main.transaction_detail.*
import kotlinx.android.synthetic.main.transactions.*
import kotlin.math.roundToInt


val transactionsUpdate = MutableLiveData<Unit>().apply { value = Unit }


class TransactionsFragment : Fragment(), MainFragment {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.transactions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupVerticalList(rvTransactions)
        daemonUpdate.observe(viewLifecycleOwner, Observer { update() })
        transactionsUpdate.observe(viewLifecycleOwner, Observer { update() })
        settings.getString("base_unit").observe(viewLifecycleOwner, Observer { update() })

        btnSend.setOnClickListener {
            if (daemonModel.wallet!!.callAttr("is_watching_only").toBoolean()) {
                toast(R.string.this_wallet_is_watching_only_)
            } else if (daemonModel.wallet!!.callAttr("get_receiving_addresses")
                       .asList().isEmpty()) {
                // At least one receiving address is needed to call wallet.dummy_address.
                toast(R.string.electron_cash_is_generating_your_addresses__please_wait_)
            } else {
                showDialog(activity!!, SendDialog())
            }
        }
    }

    fun update() {
        val wallet = daemonModel.wallet
        if (wallet == null) {
            rvTransactions.adapter = null
            btnSend.hide()
        } else {
            rvTransactions.adapter = TransactionsAdapter(
                activity!!, wallet.callAttr("export_history",
                                            Kwarg("decimal_point", unitPlaces)).asList())
            btnSend.show()
        }
    }
}


class TransactionsAdapter(val activity: FragmentActivity, val transactions: List<PyObject>)
    : BoundAdapter<TransactionModel>(R.layout.transaction_list) {

    override fun getItem(position: Int): TransactionModel {
        return TransactionModel(transactions.get(itemCount - position - 1)  // Newest first
                                .asMap())
    }

    override fun getItemCount(): Int {
        return transactions.size
    }

    override fun onBindViewHolder(holder: BoundViewHolder<TransactionModel>, position: Int) {
        super.onBindViewHolder(holder, position)
        holder.itemView.setOnClickListener {
            val txid = holder.item.get("txid")
            val tx = daemonModel.wallet!!.get("transactions")!!.callAttr("get", txid)
            if (tx == null) {  // Can happen during wallet sync.
                toast(R.string.transaction_not)
            } else {
                showDialog(activity, TransactionDialog(txid))
            }
        }
    }
}

class TransactionModel(val txExport: Map<PyObject, PyObject>) {
    fun get(key: String) = txExport.get(PyObject.fromJava(key))!!.toString()

    fun getIcon(): Drawable {
        return app.resources.getDrawable(
            if (get("value")[0] == '+') R.drawable.ic_add_24dp
            else R.drawable.ic_remove_24dp)!!
    }

    @SuppressLint("StringFormatMatches")
    fun getConfirmationsStr(): String {
        val confirmations = Integer.parseInt(get("confirmations"))
        return when {
            confirmations <= 0 -> ""
            confirmations > 6 -> app.getString(R.string.confirmed)
            else -> app.getString(R.string.___confirmations, confirmations)
        }
    }
}


class TransactionDialog() : AlertDialogFragment() {
    constructor(txid: String) : this() {
        arguments = Bundle().apply { putString("txid", txid) }
    }
    val txid by lazy { arguments!!.getString("txid")!! }
    val wallet by lazy { daemonModel.wallet!! }

    override fun onBuildDialog(builder: AlertDialog.Builder) {
        builder.setView(R.layout.transaction_detail)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, {_, _ ->
                // Avoid doing a full update if the label hasn't changed, because this
                // currently scrolls the list back to the top.
                val newLabel = dialog.etDescription.text.toString()
                if (newLabel != wallet.callAttr("get_label", txid).toString()) {
                    setTxDescription(txid, newLabel)
                }
            })
    }

    override fun onShowDialog(dialog: AlertDialog) {
        dialog.btnExplore.setOnClickListener { exploreTransaction(activity!!, txid) }
        dialog.btnCopy.setOnClickListener { copyToClipboard(txid) }

        val tx = wallet.get("transactions")!!.callAttr("get", txid)!!
        val txInfo = wallet.callAttr("get_tx_info", tx).asList()
        dialog.tvTxid.text = txid

        val timestamp = txInfo.get(8).toLong()
        dialog.tvTimestamp.text = if (timestamp == 0L) getString(R.string.Unknown)
                                  else libUtil.callAttr("format_time", timestamp).toString()

        dialog.tvStatus.text = txInfo.get(1)!!.toString()

        val size = tx.callAttr("estimated_size").toInt()
        dialog.tvSize.text = getString(R.string.bytes, size)

        val fee = txInfo.get(5)?.toLong()
        if (fee == null) {
            dialog.tvFee.text = getString(R.string.Unknown)
        } else {
            val feeSpb = (fee.toDouble() / size.toDouble()).roundToInt()
            dialog.tvFee.text = String.format("%s (%s %s)",
                                              getString(R.string.sat_byte, feeSpb),
                                              formatSatoshis(fee), unitName)
        }

        dialog.etDescription.setText(txInfo.get(2)!!.toString())
    }
}


fun setTxDescription(txid: String, description: String) {
    val wallet = daemonModel.wallet!!
    wallet.callAttr("set_label", txid, description)
    wallet.get("storage")!!.callAttr("write")
    transactionsUpdate.setValue(Unit)
}