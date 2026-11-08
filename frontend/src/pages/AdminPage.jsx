import React, { useState, useEffect } from 'react';
import { adminApi } from '../api/client';
import { Settings, Users, CreditCard, Lock, Unlock, ShieldAlert, Loader2, Check } from 'lucide-react';

export default function AdminPage() {
  const [users, setUsers] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('accounts'); // 'accounts' | 'users'

  // Freeze Modal State
  const [selectedAccount, setSelectedAccount] = useState(null);
  const [newStatus, setNewStatus] = useState('FROZEN');
  const [reason, setReason] = useState('');
  const [updating, setUpdating] = useState(false);

  const fetchData = async () => {
    try {
      const [uRes, aRes] = await Promise.all([
        adminApi.getUsers(0, 50),
        adminApi.getAccounts(0, 50)
      ]);
      setUsers(uRes.data.data.content || []);
      setAccounts(aRes.data.data.content || []);
    } catch (err) {
      console.error('Failed to load admin data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handleUpdateStatus = async (e) => {
    e.preventDefault();
    if (!selectedAccount) return;
    setUpdating(true);

    try {
      await adminApi.updateStatus(selectedAccount.id, {
        status: newStatus,
        reason: reason.trim() || 'Admin status modification'
      });
      setSelectedAccount(null);
      setReason('');
      fetchData();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to update account status');
    } finally {
      setUpdating(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="w-10 h-10 animate-spin text-purple-600" />
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-slate-900 tracking-tight flex items-center space-x-2.5">
          <Settings className="w-8 h-8 text-purple-600" />
          <span>System Administration Portal</span>
        </h1>
        <p className="text-sm text-slate-500 font-medium">User directory management, account status enforcement, and compliance controls</p>
      </div>

      {/* Tabs */}
      <div className="border-b border-slate-200">
        <nav className="flex space-x-6">
          <button
            onClick={() => setActiveTab('accounts')}
            className={`pb-4 px-1 text-sm font-bold border-b-2 transition flex items-center space-x-2 ${
              activeTab === 'accounts'
                ? 'border-purple-600 text-purple-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <CreditCard className="w-4 h-4" />
            <span>Bank Accounts Registry ({accounts.length})</span>
          </button>

          <button
            onClick={() => setActiveTab('users')}
            className={`pb-4 px-1 text-sm font-bold border-b-2 transition flex items-center space-x-2 ${
              activeTab === 'users'
                ? 'border-purple-600 text-purple-700'
                : 'border-transparent text-slate-500 hover:text-slate-800'
            }`}
          >
            <Users className="w-4 h-4" />
            <span>Registered Users Directory ({users.length})</span>
          </button>
        </nav>
      </div>

      {activeTab === 'accounts' ? (
        <div className="bg-white rounded-2xl border border-slate-200 overflow-hidden shadow-sm">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-[11px] uppercase tracking-wider text-slate-500 border-b border-slate-200 font-bold">
                <tr>
                  <th className="py-3 px-6">Account Number</th>
                  <th className="py-3 px-6">Type</th>
                  <th className="py-3 px-6 text-right">Balance (INR)</th>
                  <th className="py-3 px-6 text-right">Balance (Paise)</th>
                  <th className="py-3 px-6 text-center">Status</th>
                  <th className="py-3 px-6 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {accounts.map((acc) => {
                  const isFrozen = acc.status === 'FROZEN';
                  const isClosed = acc.status === 'CLOSED';

                  return (
                    <tr key={acc.id} className="hover:bg-slate-50/70 transition">
                      <td className="py-4 px-6 font-mono text-xs font-bold text-slate-900">
                        {acc.accountNumber}
                      </td>
                      <td className="py-4 px-6">
                        <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-slate-100 text-slate-700">
                          {acc.accountType}
                        </span>
                      </td>
                      <td className="py-4 px-6 text-right font-mono font-bold text-slate-900 text-sm">
                        ₹{acc.balanceRupees.toFixed(2)}
                      </td>
                      <td className="py-4 px-6 text-right font-mono text-xs text-slate-500">
                        {acc.balancePaise.toLocaleString()} paise
                      </td>
                      <td className="py-4 px-6 text-center">
                        <span className={`text-[11px] font-bold px-2.5 py-0.5 rounded-full ${
                          acc.status === 'ACTIVE'
                            ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                            : isFrozen
                            ? 'bg-rose-50 text-rose-700 border border-rose-200'
                            : 'bg-slate-100 text-slate-600 border border-slate-200'
                        }`}>
                          {acc.status}
                        </span>
                      </td>
                      <td className="py-4 px-6 text-right">
                        {isFrozen ? (
                          <button
                            onClick={() => {
                              setSelectedAccount(acc);
                              setNewStatus('ACTIVE');
                            }}
                            className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-800 transition inline-flex items-center space-x-1"
                          >
                            <Unlock className="w-3.5 h-3.5" />
                            <span>Unfreeze</span>
                          </button>
                        ) : !isClosed ? (
                          <button
                            onClick={() => {
                              setSelectedAccount(acc);
                              setNewStatus('FROZEN');
                            }}
                            className="text-xs font-semibold px-3 py-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-800 transition inline-flex items-center space-x-1"
                          >
                            <Lock className="w-3.5 h-3.5" />
                            <span>Freeze</span>
                          </button>
                        ) : (
                          <span className="text-xs text-slate-400">Closed</span>
                        )}
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
                  <th className="py-3 px-6">Username</th>
                  <th className="py-3 px-6">Full Name</th>
                  <th className="py-3 px-6">Email</th>
                  <th className="py-3 px-6">Roles</th>
                  <th className="py-3 px-6 text-center">Status</th>
                  <th className="py-3 px-6">Created At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {users.map((u) => (
                  <tr key={u.id} className="hover:bg-slate-50/70 transition">
                    <td className="py-3.5 px-6 font-mono text-xs font-bold text-slate-900">
                      {u.username}
                    </td>
                    <td className="py-3.5 px-6 text-xs text-slate-800 font-medium">
                      {u.fullName}
                    </td>
                    <td className="py-3.5 px-6 text-xs text-slate-600">
                      {u.email}
                    </td>
                    <td className="py-3.5 px-6">
                      <div className="flex flex-wrap gap-1">
                        {u.roles?.map((r) => (
                          <span key={r} className="text-[10px] font-bold px-2 py-0.5 rounded-md bg-purple-50 text-purple-700 border border-purple-100">
                            {r.replace('ROLE_', '')}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="py-3.5 px-6 text-center">
                      <span className="text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700">
                        {u.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-6 text-xs text-slate-500 font-mono">
                      {new Date(u.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Freeze / Status Update Modal */}
      {selectedAccount && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
            <h3 className="text-lg font-bold text-slate-900 mb-1">
              Update Account Status: {selectedAccount.accountNumber}
            </h3>
            <p className="text-xs text-slate-500 mb-4">
              Current Status: <span className="font-semibold">{selectedAccount.status}</span> &rarr; Target: <span className="font-semibold">{newStatus}</span>
            </p>

            <form onSubmit={handleUpdateStatus} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  Action
                </label>
                <select
                  value={newStatus}
                  onChange={(e) => setNewStatus(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm font-semibold focus:ring-2 focus:ring-purple-500 outline-none"
                >
                  <option value="ACTIVE">ACTIVE (Unfreeze)</option>
                  <option value="FROZEN">FROZEN (Prevent Withdrawals/Transfers)</option>
                  <option value="CLOSED">CLOSED (Permanent)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">
                  Administrative Reason (Audit Trail)
                </label>
                <textarea
                  rows={3}
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  required
                  placeholder="e.g. Unusual rapid transfer activity flagged by fraud detection system."
                  className="w-full px-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-purple-500 outline-none"
                ></textarea>
              </div>

              <div className="flex space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setSelectedAccount(null)}
                  disabled={updating}
                  className="flex-1 py-2.5 border border-slate-300 text-slate-700 rounded-xl font-semibold text-sm hover:bg-slate-50"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={updating}
                  className="flex-1 py-2.5 bg-purple-600 hover:bg-purple-700 text-white rounded-xl font-semibold text-sm flex items-center justify-center space-x-2"
                >
                  {updating && <Loader2 className="w-4 h-4 animate-spin" />}
                  <span>{updating ? 'Updating...' : 'Confirm Status Change'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
