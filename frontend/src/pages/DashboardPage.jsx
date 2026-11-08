import React, { useState, useEffect } from 'react';
import { accountsApi, transactionsApi } from '../api/client';
import TransactionModal from '../components/TransactionModal';
import { 
  PlusCircle, 
  ArrowDownRight, 
  ArrowUpRight, 
  ArrowRightLeft, 
  CreditCard, 
  ShieldCheck, 
  Clock, 
  Copy, 
  Check, 
  RefreshCw,
  AlertTriangle
} from 'lucide-react';

export default function DashboardPage({ onNavigateToStatement }) {
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [modalMode, setModalMode] = useState(null); // 'DEPOSIT' | 'WITHDRAW' | 'TRANSFER'
  const [copiedId, setCopiedId] = useState(null);
  const [creatingAccount, setCreatingAccount] = useState(false);
  const [newAccType, setNewAccType] = useState('SAVINGS');

  const fetchData = async () => {
    try {
      const [accRes, txRes] = await Promise.all([
        accountsApi.list(),
        transactionsApi.list(0, 15)
      ]);
      setAccounts(accRes.data.data || []);
      setTransactions(txRes.data.data.content || []);
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
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

  const handleCopy = (num) => {
    navigator.clipboard.writeText(num);
    setCopiedId(num);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleOpenAction = (account, mode) => {
    setSelectedAccount(account);
    setModalMode(mode);
  };

  const handleCreateAccount = async () => {
    try {
      await accountsApi.create({ accountType: newAccType });
      setCreatingAccount(false);
      fetchData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to create account');
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600"></div>
      </div>
    );
  }

  const totalBalanceRupees = accounts.reduce((acc, a) => acc + (a.balanceRupees || 0), 0);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8">
      {/* Top Header Summary */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight">Banking Overview</h1>
          <p className="text-sm text-slate-500 font-medium">Real-time accounts balance and double-entry money movements</p>
        </div>
        <div className="flex items-center space-x-3">
          <button
            onClick={handleRefresh}
            disabled={refreshing}
            className="flex items-center space-x-1.5 px-3 py-2 bg-white border border-slate-200 text-slate-700 text-sm font-semibold rounded-xl hover:bg-slate-50 transition shadow-sm"
          >
            <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </button>
          <button
            onClick={() => setCreatingAccount(true)}
            className="flex items-center space-x-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-100 transition"
          >
            <PlusCircle className="w-4 h-4" />
            <span>Open Account</span>
          </button>
        </div>
      </div>

      {/* Primary Balance Stat Banner */}
      <div className="bg-gradient-to-r from-slate-900 via-indigo-950 to-slate-900 rounded-3xl p-6 sm:p-8 text-white shadow-xl relative overflow-hidden">
        <div className="absolute right-0 top-0 -mt-12 -mr-12 w-64 h-64 bg-indigo-500/10 rounded-full blur-2xl"></div>
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <span className="text-xs uppercase font-bold tracking-widest text-indigo-300">Total Net Portfolio</span>
            <div className="mt-1 flex items-baseline space-x-2">
              <span className="text-4xl sm:text-5xl font-extrabold tracking-tight">₹{totalBalanceRupees.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
              <span className="text-indigo-200 text-xs font-mono font-medium">INR</span>
            </div>
            <p className="mt-2 text-xs text-slate-400 flex items-center space-x-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
              <span>Double-entry protected • 100% Integer Paise Precision</span>
            </p>
          </div>

          <div className="flex flex-wrap gap-2">
            {accounts.map((acc) => (
              <div key={acc.id} className="bg-white/10 backdrop-blur-md px-4 py-2.5 rounded-2xl border border-white/10 text-left">
                <div className="text-[10px] font-mono uppercase tracking-wider text-indigo-200">{acc.accountType}</div>
                <div className="text-lg font-bold font-mono">₹{acc.balanceRupees.toFixed(2)}</div>
                <div className="text-[11px] text-slate-300 font-mono">{acc.accountNumber}</div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Account Cards Grid */}
      <div>
        <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center space-x-2">
          <CreditCard className="w-5 h-5 text-indigo-600" />
          <span>My Accounts ({accounts.length})</span>
        </h2>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {accounts.map((acc) => {
            const isFrozen = acc.status === 'FROZEN';

            return (
              <div
                key={acc.id}
                className="bg-white rounded-2xl p-6 border border-slate-200 shadow-sm hover:shadow-md transition flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100">
                      {acc.accountType}
                    </span>
                    <span
                      className={`text-xs font-semibold px-2.5 py-0.5 rounded-full ${
                        isFrozen ? 'bg-rose-50 text-rose-700 border border-rose-200' : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                      }`}
                    >
                      {acc.status}
                    </span>
                  </div>

                  <div className="text-xs text-slate-400 uppercase font-semibold">Available Balance</div>
                  <div className="text-3xl font-extrabold text-slate-900 tracking-tight my-1">
                    ₹{acc.balanceRupees.toFixed(2)}
                  </div>
                  <div className="text-[11px] text-slate-400 font-mono mb-4">
                    Exact: {acc.balancePaise.toLocaleString()} paise
                  </div>

                  <div className="flex items-center justify-between p-2.5 bg-slate-50 rounded-xl border border-slate-100 font-mono text-xs text-slate-600 mb-5">
                    <span>{acc.accountNumber}</span>
                    <button
                      onClick={() => handleCopy(acc.accountNumber)}
                      className="text-slate-400 hover:text-indigo-600 p-1"
                      title="Copy Account Number"
                    >
                      {copiedId === acc.accountNumber ? <Check className="w-4 h-4 text-emerald-600" /> : <Copy className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                {isFrozen ? (
                  <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center space-x-2">
                    <AlertTriangle className="w-4 h-4 flex-shrink-0" />
                    <span>Account is frozen. Financial transactions are restricted.</span>
                  </div>
                ) : (
                  <div className="grid grid-cols-3 gap-2">
                    <button
                      onClick={() => handleOpenAction(acc, 'DEPOSIT')}
                      className="py-2 px-2 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-bold rounded-xl transition flex items-center justify-center space-x-1"
                    >
                      <ArrowDownRight className="w-3.5 h-3.5" />
                      <span>Deposit</span>
                    </button>
                    <button
                      onClick={() => handleOpenAction(acc, 'WITHDRAW')}
                      className="py-2 px-2 bg-amber-50 hover:bg-amber-100 text-amber-800 text-xs font-bold rounded-xl transition flex items-center justify-center space-x-1"
                    >
                      <ArrowUpRight className="w-3.5 h-3.5" />
                      <span>Withdraw</span>
                    </button>
                    <button
                      onClick={() => handleOpenAction(acc, 'TRANSFER')}
                      className="py-2 px-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-800 text-xs font-bold rounded-xl transition flex items-center justify-center space-x-1"
                    >
                      <ArrowRightLeft className="w-3.5 h-3.5" />
                      <span>Transfer</span>
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {/* Recent Transactions Table */}
      <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
        <div className="p-5 border-b border-slate-100 flex items-center justify-between">
          <div>
            <h3 className="font-bold text-slate-900 text-base">Recent Transactions</h3>
            <p className="text-xs text-slate-500">Atomic fund movements with immutable double-entry ledger pairing</p>
          </div>
          <button
            onClick={() => onNavigateToStatement && onNavigateToStatement(accounts[0]?.id)}
            className="text-xs font-semibold text-indigo-600 hover:text-indigo-800"
          >
            Full Statements &rarr;
          </button>
        </div>

        {transactions.length === 0 ? (
          <div className="p-8 text-center text-sm text-slate-400">
            No transactions found yet. Use Deposit, Withdraw, or Transfer above to perform money movements.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 border-b border-slate-100 font-bold">
                <tr>
                  <th className="px-6 py-3.5">Reference</th>
                  <th className="px-6 py-3.5">Type</th>
                  <th className="px-6 py-3.5">Route</th>
                  <th className="px-6 py-3.5 text-right">Amount</th>
                  <th className="px-6 py-3.5">Status</th>
                  <th className="px-6 py-3.5">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.map((tx) => {
                  const isDeposit = tx.transactionType === 'DEPOSIT';
                  const isWithdraw = tx.transactionType === 'WITHDRAWAL';
                  const isTransfer = tx.transactionType === 'TRANSFER';

                  return (
                    <tr key={tx.id} className="hover:bg-slate-50/70 transition">
                      <td className="px-6 py-4 font-mono text-xs font-medium text-slate-800">
                        {tx.referenceNumber}
                      </td>
                      <td className="px-6 py-4">
                        <span
                          className={`inline-flex items-center space-x-1 text-xs font-bold px-2 py-0.5 rounded-full ${
                            isDeposit
                              ? 'bg-emerald-50 text-emerald-700'
                              : isWithdraw
                              ? 'bg-amber-50 text-amber-700'
                              : 'bg-indigo-50 text-indigo-700'
                          }`}
                        >
                          {isDeposit && <ArrowDownRight className="w-3 h-3" />}
                          {isWithdraw && <ArrowUpRight className="w-3 h-3" />}
                          {isTransfer && <ArrowRightLeft className="w-3 h-3" />}
                          <span>{tx.transactionType}</span>
                        </span>
                      </td>
                      <td className="px-6 py-4 font-mono text-xs text-slate-600">
                        {tx.sourceAccountNumber} &rarr; {tx.destinationAccountNumber}
                      </td>
                      <td className="px-6 py-4 text-right font-bold text-slate-900 font-mono">
                        ₹{tx.amountRupees.toFixed(2)}
                      </td>
                      <td className="px-6 py-4">
                        <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-700">
                          {tx.status}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-xs text-slate-500">
                        {new Date(tx.createdAt).toLocaleDateString()} {new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal for Transactions */}
      <TransactionModal
        isOpen={!!modalMode}
        onClose={() => setModalMode(null)}
        mode={modalMode}
        account={selectedAccount}
        onComplete={fetchData}
      />

      {/* Modal for Open New Account */}
      {creatingAccount && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-sm w-full p-6 shadow-2xl border border-slate-100">
            <h3 className="text-lg font-bold text-slate-900 mb-2">Open New Bank Account</h3>
            <p className="text-xs text-slate-500 mb-4">Choose your preferred account tier:</p>

            <div className="space-y-3 mb-6">
              <label className="flex items-center space-x-3 p-3 rounded-xl border border-slate-200 cursor-pointer hover:bg-slate-50">
                <input
                  type="radio"
                  name="accType"
                  value="SAVINGS"
                  checked={newAccType === 'SAVINGS'}
                  onChange={() => setNewAccType('SAVINGS')}
                  className="text-indigo-600"
                />
                <div>
                  <div className="text-sm font-bold text-slate-900">Savings Account</div>
                  <div className="text-xs text-slate-500">Standard personal account with interest yield</div>
                </div>
              </label>

              <label className="flex items-center space-x-3 p-3 rounded-xl border border-slate-200 cursor-pointer hover:bg-slate-50">
                <input
                  type="radio"
                  name="accType"
                  value="CHECKING"
                  checked={newAccType === 'CHECKING'}
                  onChange={() => setNewAccType('CHECKING')}
                  className="text-indigo-600"
                />
                <div>
                  <div className="text-sm font-bold text-slate-900">Checking Account</div>
                  <div className="text-xs text-slate-500">High-velocity account for daily merchant payments</div>
                </div>
              </label>
            </div>

            <div className="flex space-x-3">
              <button
                type="button"
                onClick={() => setCreatingAccount(false)}
                className="flex-1 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-semibold text-sm hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleCreateAccount}
                className="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold text-sm"
              >
                Confirm Open
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
