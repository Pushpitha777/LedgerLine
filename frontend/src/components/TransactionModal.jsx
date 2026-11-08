import React, { useState, useEffect } from 'react';
import { transactionsApi, generateIdempotencyKey } from '../api/client';
import { X, ArrowDownRight, ArrowUpRight, ArrowRightLeft, ShieldCheck, Loader2 } from 'lucide-react';

export default function TransactionModal({ isOpen, onClose, mode, account, onComplete }) {
  const [amount, setAmount] = useState('');
  const [destinationAccount, setDestinationAccount] = useState('');
  const [description, setDescription] = useState('');
  const [idempotencyKey, setIdempotencyKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    if (isOpen) {
      setIdempotencyKey(generateIdempotencyKey());
      setAmount('');
      setDestinationAccount('');
      setDescription('');
      setError('');
      setSuccess('');
    }
  }, [isOpen, mode]);

  if (!isOpen) return null;

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    const parsedAmount = parseFloat(amount);
    if (isNaN(parsedAmount) || parsedAmount <= 0) {
      setError('Amount must be strictly greater than ₹0.00');
      return;
    }

    if (mode === 'WITHDRAW' && account && parsedAmount > account.balanceRupees) {
      setError(`Insufficient funds. Account balance is ₹${account.balanceRupees.toFixed(2)}`);
      return;
    }

    if (mode === 'TRANSFER' && !destinationAccount.trim()) {
      setError('Please provide a valid destination account number');
      return;
    }

    setLoading(true);

    try {
      let res;
      if (mode === 'DEPOSIT') {
        res = await transactionsApi.deposit(
          { destinationAccountId: account.id, amountRupees: parsedAmount, description },
          idempotencyKey
        );
      } else if (mode === 'WITHDRAW') {
        res = await transactionsApi.withdraw(
          { sourceAccountId: account.id, amountRupees: parsedAmount, description },
          idempotencyKey
        );
      } else if (mode === 'TRANSFER') {
        res = await transactionsApi.transfer(
          {
            sourceAccountId: account.id,
            destinationAccountNumber: destinationAccount.trim(),
            amountRupees: parsedAmount,
            description,
          },
          idempotencyKey
        );
      }

      setSuccess(`Transaction completed! Reference: ${res.data.data.referenceNumber}`);
      setTimeout(() => {
        onComplete();
        onClose();
      }, 1200);
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Transaction failed';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  const titles = {
    DEPOSIT: { label: 'Deposit Money', color: 'text-emerald-700', bg: 'bg-emerald-50', icon: ArrowDownRight },
    WITHDRAW: { label: 'Withdraw Cash', color: 'text-amber-700', bg: 'bg-amber-50', icon: ArrowUpRight },
    TRANSFER: { label: 'Inter-Account Transfer', color: 'text-indigo-700', bg: 'bg-indigo-50', icon: ArrowRightLeft },
  };

  const current = titles[mode] || titles.DEPOSIT;
  const Icon = current.icon;

  return (
    <div className="fixed inset-0 z-50 overflow-y-auto bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100 relative">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 p-1.5 rounded-lg hover:bg-slate-100 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-5">
          <div className={`w-12 h-12 rounded-xl ${current.bg} ${current.color} flex items-center justify-center`}>
            <Icon className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-lg font-bold text-slate-900">{current.label}</h3>
            <p className="text-xs text-slate-500 font-mono">
              Account: <span className="font-semibold">{account?.accountNumber}</span>
            </p>
          </div>
        </div>

        {error && (
          <div className="mb-4 p-3 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm">
            {error}
          </div>
        )}

        {success && (
          <div className="mb-4 p-3 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm">
            {success}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          {mode === 'TRANSFER' && (
            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                Destination Account Number
              </label>
              <input
                type="text"
                placeholder="e.g. LL1000000002"
                value={destinationAccount}
                onChange={(e) => setDestinationAccount(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 font-mono text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"
              />
            </div>
          )}

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Amount (INR ₹)
            </label>
            <div className="relative">
              <span className="absolute left-3.5 top-2.5 text-slate-400 font-bold">₹</span>
              <input
                type="number"
                step="0.01"
                min="0.01"
                placeholder="0.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
                className="w-full pl-8 pr-3.5 py-2.5 rounded-xl border border-slate-300 font-semibold text-slate-900 focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none text-base"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
              Description (Optional)
            </label>
            <input
              type="text"
              placeholder="e.g. Reimbursement, Groceries"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 outline-none"
            />
          </div>

          <div className="p-3 bg-slate-50 border border-slate-200 rounded-xl">
            <div className="flex items-center justify-between text-xs text-slate-500 font-mono mb-1">
              <span className="flex items-center space-x-1">
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-600" />
                <span>Idempotency-Key:</span>
              </span>
              <span className="truncate max-w-[200px] text-slate-700 font-semibold">{idempotencyKey}</span>
            </div>
            <p className="text-[11px] text-slate-400 leading-tight">
              Guarantees the transaction cannot be executed twice if you double-click or network drops.
            </p>
          </div>

          <div className="flex space-x-3 pt-2">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="flex-1 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-semibold text-sm hover:bg-slate-50 transition"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold text-sm transition flex items-center justify-center space-x-2 shadow-lg shadow-indigo-100 disabled:opacity-50"
            >
              {loading && <Loader2 className="w-4 h-4 animate-spin" />}
              <span>{loading ? 'Processing...' : 'Confirm'}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
