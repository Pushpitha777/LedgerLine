import React, { useState, useEffect } from 'react';
import { accountsApi } from '../api/client';
import { FileText, Printer, ArrowDownRight, ArrowUpRight, Scale, Loader2 } from 'lucide-react';

export default function StatementPage({ preselectedAccountId }) {
  const [accounts, setAccounts] = useState([]);
  const [selectedAccountId, setSelectedAccountId] = useState(preselectedAccountId || '');
  const [statement, setStatement] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchAccounts = async () => {
      try {
        const res = await accountsApi.list();
        const list = res.data.data || [];
        setAccounts(list);
        if (list.length > 0 && !selectedAccountId) {
          setSelectedAccountId(list[0].id);
        }
      } catch (err) {
        console.error('Failed to load accounts for statement:', err);
      }
    };
    fetchAccounts();
  }, []);

  useEffect(() => {
    if (!selectedAccountId) return;

    const fetchStatement = async () => {
      setLoading(true);
      try {
        const res = await accountsApi.getStatement(selectedAccountId);
        setStatement(res.data.data);
      } catch (err) {
        console.error('Failed to fetch statement:', err);
      } finally {
        setLoading(false);
      }
    };
    fetchStatement();
  }, [selectedAccountId]);

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight flex items-center space-x-2.5">
            <FileText className="w-7 h-7 text-indigo-600" />
            <span>Account Statement</span>
          </h1>
          <p className="text-sm text-slate-500 font-medium">Official immutable ledger statement with historical running balances</p>
        </div>

        <div className="flex items-center space-x-3">
          <select
            value={selectedAccountId}
            onChange={(e) => setSelectedAccountId(e.target.value)}
            className="px-3 py-2 bg-white border border-slate-300 rounded-xl font-mono text-sm font-semibold outline-none focus:ring-2 focus:ring-indigo-500"
          >
            {accounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.accountNumber} ({a.accountType}) - ₹{a.balanceRupees.toFixed(2)}
              </option>
            ))}
          </select>

          <button
            onClick={() => window.print()}
            className="flex items-center space-x-1.5 px-4 py-2 bg-white border border-slate-200 text-slate-700 text-sm font-semibold rounded-xl hover:bg-slate-50 shadow-sm transition"
          >
            <Printer className="w-4 h-4" />
            <span>Print</span>
          </button>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center p-16">
          <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
        </div>
      ) : statement ? (
        <div className="bg-white rounded-3xl border border-slate-200 shadow-sm p-6 sm:p-10 space-y-8 print:border-none print:shadow-none print:p-0">
          {/* Statement Header */}
          <div className="flex flex-col sm:flex-row justify-between border-b border-slate-100 pb-6 gap-4">
            <div>
              <div className="text-xs uppercase tracking-widest font-extrabold text-indigo-600 mb-1">Official Bank Document</div>
              <h2 className="text-xl font-extrabold text-slate-900">{statement.accountHolderName}</h2>
              <p className="text-xs font-mono text-slate-500">Account: {statement.accountNumber} • {statement.accountType}</p>
            </div>
            <div className="sm:text-right">
              <div className="text-xs text-slate-400">Current Balance</div>
              <div className="text-2xl font-extrabold text-slate-900 font-mono">₹{statement.currentBalanceRupees.toFixed(2)}</div>
              <div className="text-[11px] text-slate-400 font-mono">Generated: {new Date(statement.generatedAt).toLocaleString()}</div>
            </div>
          </div>

          {/* Statement Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 border-b border-slate-200 font-bold">
                <tr>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4">Reference</th>
                  <th className="py-3 px-4">Description</th>
                  <th className="py-3 px-4 text-center">Entry</th>
                  <th className="py-3 px-4 text-right">Amount</th>
                  <th className="py-3 px-4 text-right">Running Balance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {statement.entries.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="py-8 text-center text-slate-400 text-sm">
                      No ledger records recorded for this account.
                    </td>
                  </tr>
                ) : (
                  statement.entries.map((entry, idx) => {
                    const isCredit = entry.entryType === 'CREDIT';

                    return (
                      <tr key={idx} className="hover:bg-slate-50/70 transition">
                        <td className="py-3.5 px-4 text-xs text-slate-500 whitespace-nowrap">
                          {new Date(entry.date).toLocaleDateString()} {new Date(entry.date).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </td>
                        <td className="py-3.5 px-4 font-mono text-xs font-semibold text-slate-800">
                          {entry.transactionReference}
                        </td>
                        <td className="py-3.5 px-4 text-slate-700 text-xs">
                          {entry.description || entry.transactionType}
                        </td>
                        <td className="py-3.5 px-4 text-center">
                          <span
                            className={`inline-flex items-center text-[11px] font-bold px-2 py-0.5 rounded-full ${
                              isCredit
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : 'bg-rose-50 text-rose-700 border border-rose-200'
                            }`}
                          >
                            {entry.entryType}
                          </span>
                        </td>
                        <td className={`py-3.5 px-4 text-right font-mono font-bold text-xs ${isCredit ? 'text-emerald-700' : 'text-slate-900'}`}>
                          {isCredit ? '+' : '-'}₹{entry.amountRupees.toFixed(2)}
                        </td>
                        <td className="py-3.5 px-4 text-right font-mono font-extrabold text-slate-900 text-xs">
                          ₹{entry.balanceAfterRupees.toFixed(2)}
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 text-xs text-slate-500 flex items-center justify-between">
            <span>Verified by LedgerLine Double-Entry Invariant Core</span>
            <span className="font-mono text-[11px]">IMMUTABLE AUDIT TRAIL</span>
          </div>
        </div>
      ) : null}
    </div>
  );
}
