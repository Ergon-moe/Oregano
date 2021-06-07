#!/usr/bin/env python3
#
# Electrum - lightweight Bitcoin client
# Copyright (C) 2015 Thomas Voegtlin
#
# Permission is hereby granted, free of charge, to any person
# obtaining a copy of this software and associated documentation files
# (the "Software"), to deal in the Software without restriction,
# including without limitation the rights to use, copy, modify, merge,
# publish, distribute, sublicense, and/or sell copies of the Software,
# and to permit persons to whom the Software is furnished to do so,
# subject to the following conditions:
#
# The above copyright notice and this permission notice shall be
# included in all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
# EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
# NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
# BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
# ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
# CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

from .util import *

from oregano.i18n import _
from oregano.util import format_time, FileImportFailed
from oregano.paymentrequest import pr_tooltips


class InvoiceList(MyTreeWidget):
    filter_columns = [0, 1, 2, 3]  # Date, Requestor, Description, Amount

    def __init__(self, parent):
        MyTreeWidget.__init__(self, parent, self.create_menu, [_('Expires'), _('Requestor'), _('Description'), _('Amount'), _('Status')], 2)
        self.setSortingEnabled(True)
        self.header().setSectionResizeMode(1, QHeaderView.Interactive)
        self.setColumnWidth(1, 200)

    def on_update(self):
        inv_list = self.parent.invoices.unpaid_invoices()
        self.clear()
        for pr in inv_list:
            key = pr.get_id()
            status = self.parent.invoices.get_status(key)
            if status is None:
                continue
            requestor = pr.get_requestor()
            exp = pr.get_expiration_date()
            date_str = format_time(exp) if exp else _('Never')
            item = QTreeWidgetItem([date_str, requestor, pr.memo, self.parent.format_amount(pr.get_amount(), whitespaces=True), _(pr_tooltips.get(status,''))])
            item.setIcon(4, QIcon(pr_icons.get(status)))
            item.setData(0, Qt.UserRole, key)
            item.setFont(1, QFont(MONOSPACE_FONT))
            item.setFont(3, QFont(MONOSPACE_FONT))
            self.addTopLevelItem(item)
        self.setCurrentItem(self.topLevelItem(0))
        self.chkVisible(inv_list)

    def chkVisible(self, inv_list=None):
        inv_list = inv_list or self.parent.invoices.unpaid_invoices()
        b = len(inv_list) > 0 and self.parent.isVisible()
        self.setVisible(b)
        self.parent.invoices_label.setVisible(b)


    def import_invoices(self):
        wallet_folder = self.parent.get_wallet_folder()
        filename, __ = QFileDialog.getOpenFileName(self.parent, "Select your wallet file", wallet_folder)
        if not filename:
            return
        try:
            self.parent.invoices.import_file(filename)
        except FileImportFailed as e:
            self.parent.show_message(str(e))
        self.on_update()

    def create_menu(self, position):
        menu = QMenu()
        item = self.itemAt(position)
        if not item:
            return
        key = item.data(0, Qt.UserRole)
        column = self.currentColumn()
        column_title = self.headerItem().text(column)
        column_data = item.text(column)
        pr = self.parent.invoices.get(key)
        status = self.parent.invoices.get_status(key)
        if column_data:
            menu.addAction(_("Copy {}").format(column_title), lambda: self.parent.app.clipboard().setText(column_data.strip()))
        menu.addAction(_("Details"), lambda: self.parent.show_invoice(key))
        if status == PR_UNPAID:
            menu.addAction(_("Pay Now"), lambda: self.parent.do_pay_invoice(key))
        menu.addAction(_("Delete"), lambda: self.parent.delete_invoice(key))
        menu.exec_(self.viewport().mapToGlobal(position))
