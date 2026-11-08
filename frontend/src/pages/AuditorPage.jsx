import React, { useState, useEffect } from 'react';
import { auditorApi } from '../api/client';
import { Scale, CheckCircle2, AlertOctagon, RefreshCw, ShieldAlert, History, Loader2 } from 'lucide-react';

export default function AuditorPage() {
  const [summary, setSummary] = useState(null);
  const [entries, setEntries] = useState([]);
  const [auditLogs, setAuditLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [activeTab, setActiveTab] = useState('ledger'); // 'ledger' | 'security'

  const fetchData = async () => {
    try {
      const [sumRes, entriesRes, logsRes] = await Promise.all([
        auditorApi.getSummary(),
        auditorApi.getEntries(0, 30),
        auditorApi.getAuditLogs(0, 30)
      ]);
      setSummary(sumRes.data.data);
      setEntries(entriesRes.data.data.content || []);
      setAuditLogs(logsRes.data.data.content || []);
    } catch (err) {
      console.error('Failed to load auditor data:', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleRefresh = () => {
    setRefreshing(true);
    fetchData();
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-10 h-10 animate-spin text-emerald-600" />
      </div>
    );
  }

  const isBalanced = summary?.booksBalanced;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight flex items-center space-x-2.5">
            <Scale className="w-8 h-8 text-emerald-600" />
            <span>Auditor Governance & Balance Invariant</span>
          </h1>
          <p className="text-sm text-slate-500 font-medium">Mathematical continuous proof that Total Debits equal Total Credits</p>
        </div>

        <button
          onClick={handleRefresh}
          disabled={refreshing}
          className="flex items-center space-x-2 px-4 py-2 bg-white border border-slate-200 text-slate-700 text-sm font-semibold rounded-xl hover:bg-slate-50 shadow-sm transition"
        >
          <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
          <span>Re-verify Invariant</span>
        </button>
      </div>

      {/* Main Invariant Card */}
      <div className={`rounded-3xl p-6 sm:p-8 border shadow-xl relative overflow-hidden ${
        isBalanced ? 'bg-gradient-to-r from-emerald-950 via-slate-900 to-emerald-950 text-white border-emerald-500/20' : 'bg-rose-950 text-white border-rose-500'
      }`}>
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 relative z-10">
          <div>
            <div className="flex items-center space-x-2 mb-2">
              <span className={`text-xs font-bold uppercase tracking-widest px-3 py-1 rounded-full ${
                isBalanced ? 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30' : 'bg-rose-500/30 text-rose-200'
              }`}>
                {isBalanced ? 'Double-Entry Invariant Verified' : 'Critical Invariant Violation'}
              </span>
              <span className="text-xs text-slate-400">Verified: {new Date(summary?.verifiedAt).toLocaleTimeString()}</span>
            </div>

            <div className="flex items-center space-x-3 my-2">
              {isBalanced ? (
                <CheckCircle2 className="w-10 h-10 text-emerald-400 flex-shrink-0" />
              ) : (
                <AlertOctagon className="w-10 h-10 text-rose-400 flex-shrink-0" />
              )}
              <div>
                <h2 className="text-3xl font-extrabold tracking-tight">
                  {isBalanced ? 'The Books Are Perfectly Balanced' : 'Ledger Imbalance Detected'}
                </h2>
                <p className="text-xs text-slate-400 font-mono">
                  Invariant Formula: Σ Debits ≡ Σ Credits (Difference: {summary?.differencePaise} paise)
                </p>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
            <div className="bg-white/10 backdrop-blur-md px-4 py-3 rounded-2xl border border-white/10">
              <div className="text-[10px] font-mono uppercase text-emerald-300">Total Ledger Debits</div>
              <div className="text-xl font-bold font-mono">₹{summary?.totalDebitsRupees?.toFixed(2)}</div>
              <div className="text-[10px] text-slate-400 font-mono">{summary?.totalDebitsPaise?.toLocaleString()} paise</div>
            </div>

            <div className="bg-white/10 backdrop-blur-md px-4 py-3 rounded-2xl border border-white/10">
              <div className="text-[10px] font-mono uppercase text-emerald-300">Total Ledger Credits</div>
              <div className="text-xl font-bold font-mono">₹{summary?.totalCreditsRupees?.toFixed(2)}</div>
              <div className="text-[10px] text-slate-400 font-mono">{summary?.totalCreditsPaise?.toLocaleString()} paise</div>
            </div>

            <div className="col-span-2 sm:col-span-1 bg-white/10 backdrop-blur-md px-4 py-3 rounded-2xl border border-white/10">
              <div className="text-[10px] font-mono uppercase text-emerald-300">Total Entries</div>
              <div className="text-xl font-bold font-mono">{summary?.totalLedgerEntries?.toLocaleString()}</div>
              <div className="text-[10px] text-slate-400">Paired entries</div>
            </div>
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-slate-200">
        <nav className="flex space-x-6">
          <button
            onClick={() => setActiveTab('ledger')}
            className={`pb-4 px-1 text-sm font-bold border-b-2 transition flex items-center space-x-2 ${
              activeTab === 'ledger'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <History className="w-4 h-4" />
            <span>Immutable Ledger Audit Stream ({entries.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('security')}
            className={`pb-4 px-1 text-sm font-bold border-b-2 transition flex items-center space-x-2 ${
              activeTab === 'security'
                ? 'border-emerald-600 text-emerald-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <ShieldAlert className="w-4 h-4" />
            <span>Security & Access Event Logs ({auditLogs.length})</span>
          </button>
        </nav>
      </div>

      {/* Content Panes */}
      {activeTab === 'ledger' ? (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 border-b border-slate-200 font-bold">
                <tr>
                  <th className="py-3 px-6">Timestamp</th>
                  <th className="py-3 px-6">Transaction ID</th>
                  <th className="py-3 px-6">Account ID</th>
                  <th className="py-3 px-6 text-center">Entry Type</th>
                  <th className="py-3 px-6 text-right">Amount (INR)</th>
                  <th className="py-3 px-6 text-right">Balance After</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {entries.map((entry) => {
                  const isCredit = entry.entryType === 'CREDIT';
                  return (
                    <tr key={entry.id} className="hover:bg-slate-50/70 transition">
                      <td className="py-3.5 px-6 text-xs text-slate-500 font-mono">
                        {new Date(entry.createdAt).toLocaleTimeString()}
                      </td>
                      <td className="py-3.5 px-6 font-mono text-xs text-slate-700 truncate max-w-[150px]">
                        {entry.transaction?.referenceNumber || entry.id.substring(0, 8)}
                      </td>
                      <td className="py-3.5 px-6 font-mono text-xs text-slate-600">
                        {entry.account?.accountNumber || 'SYSTEM_VAULT'}
                      </td>
                      <td className="py-3.5 px-6 text-center">
                        <span className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full ${
                          isCredit ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' : 'bg-rose-50 text-rose-700 border border-rose-200'
                        }`}>
                          {entry.entryType}
                        </span>
                      </td>
                      <td className="py-3.5 px-6 text-right font-mono font-bold text-xs text-slate-900">
                        ₹{(entry.amountPaise / 100).toFixed(2)}
                      </td>
                      <td className="py-3.5 px-6 text-right font-mono text-xs text-slate-600">
                        ₹{(entry.balanceAfterPaise / 100).toFixed(2)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 border-b border-slate-200 font-bold">
                <tr>
                  <th className="py-3 px-6">Timestamp</th>
                  <th className="py-3 px-6">Event Type</th>
                  <th className="py-3 px-6">Principal</th>
                  <th className="py-3 px-6">IP Address</th>
                  <th className="py-3 px-6">Status</th>
                  <th className="py-3 px-6">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {auditLogs.map((log) => {
                  const isSuccess = log.status === 'SUCCESS';
                  return (
                    <tr key={log.id} className="hover:bg-slate-50/70 transition">
                      <td className="py-3.5 px-6 text-xs text-slate-500 font-mono">
                        {new Date(log.createdAt).toLocaleTimeString()}
                      </td>
                      <td className="py-3.5 px-6 font-mono text-xs font-bold text-slate-900">
                        {log.eventType}
                      </td>
                      <td className="py-3.5 px-6 text-xs font-semibold text-slate-700">
                        {log.principal}
                      </td>
                      <td className="py-3.5 px-6 font-mono text-xs text-slate-500">
                        {log.ipAddress || '127.0.0.1'}
                      </td>
                      <td className="py-3.5 px-6">
                        <span className={`text-[11px] font-bold px-2 py-0.5 rounded-full ${
                          isSuccess ? 'bg-emerald-50 text-emerald-700' : 'bg-rose-50 text-rose-700'
                        }`}>
                          {log.status}
                        </span>
                      </td>
                      <td className="py-3.5 px-6 text-xs text-slate-600 max-w-xs truncate">
                        {log.details}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
