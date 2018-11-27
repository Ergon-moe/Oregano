package org.electroncash.electroncash3

import android.support.v4.app.Fragment
import com.google.zxing.integration.android.IntentIntegrator


fun scanQR(fragment: Fragment) {
    IntentIntegrator.forSupportFragment(fragment)
        .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        .setPrompt("")
        .setBeepEnabled(false)
        .initiateScan()
}
