import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { Landmark, Shield, Lock, User, Mail, ArrowRight, Loader2, CheckCircle2 } from 'lucide-react';

export default function LoginPage() {
  const { login, register, loading } = useAuth();
  const [isRegister, setIsRegister] = useState(false);

  // Form states
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (isRegister) {
      const res = await register({ username, password, email, fullName });
      if (res.success) {
        setSuccess('Account registered successfully! You can now log in.');
        setIsRegister(false);
      } else {
        const errorDetail = res.validation 
          ? Object.values(res.validation).join(' • ') 
          : res.message;
        setError(errorDetail);
      }
    } else {
      const res = await login(username, password);
      if (!res.success) {
        setError(res.message);
      }
    }
  };

  const handleQuickLogin = (u, p) => {
    setIsRegister(false);
    setError('');
    setUsername(u);
    setPassword(p);
  };

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Background glowing gradients */}
      <div className="absolute top-0 -left-4 w-72 h-72 bg-indigo-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob"></div>
      <div className="absolute top-0 -right-4 w-72 h-72 bg-purple-500 rounded-full mix-blend-multiply filter blur-3xl opacity-20 animate-blob animation-delay-2000"></div>

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10 text-center">
        <div className="w-16 h-16 rounded-2xl bg-indigo-600 flex items-center justify-center text-white mx-auto shadow-xl shadow-indigo-500/20 mb-4">
          <Landmark className="w-8 h-8" />
        </div>
        <h2 className="text-3xl font-extrabold text-white tracking-tight">Ledger<span className="text-indigo-400">Line</span></h2>
        <p className="mt-2 text-sm text-slate-400 font-medium">Mini Banking Engine • High-Integrity Double-Entry Accounting</p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md relative z-10 px-4">
        <div className="bg-white py-8 px-6 sm:px-10 shadow-2xl rounded-3xl border border-slate-100">
          <div className="flex bg-slate-100 p-1 rounded-xl mb-6">
            <button
              onClick={() => { setIsRegister(false); setError(''); setSuccess(''); }}
              className={`flex-1 py-2 rounded-lg text-sm font-bold transition ${
                !isRegister ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-500 hover:text-slate-900'
              }`}
            >
              Sign In
            </button>
            <button
              onClick={() => { setIsRegister(true); setError(''); setSuccess(''); }}
              className={`flex-1 py-2 rounded-lg text-sm font-bold transition ${
                isRegister ? 'bg-white text-indigo-600 shadow-sm' : 'text-slate-500 hover:text-slate-900'
              }`}
            >
              Register
            </button>
          </div>

          {error && (
            <div className="mb-4 p-3.5 rounded-xl bg-rose-50 border border-rose-200 text-rose-700 text-sm">
              {error}
            </div>
          )}

          {success && (
            <div className="mb-4 p-3.5 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-center space-x-2">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            {isRegister && (
              <>
                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Full Name</label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                    <input
                      type="text"
                      placeholder="Alice Smith"
                      value={fullName}
                      onChange={(e) => setFullName(e.target.value)}
                      required
                      className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Email Address</label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                    <input
                      type="email"
                      placeholder="alice@example.com"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      required
                      className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                    />
                  </div>
                </div>
              </>
            )}

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Username</label>
              <div className="relative">
                <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="text"
                  placeholder="alice"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  required
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-700 uppercase tracking-wider mb-1.5">Password</label>
              <div className="relative">
                <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3" />
                <input
                  type="password"
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  className="w-full pl-10 pr-3.5 py-2.5 rounded-xl border border-slate-300 text-sm focus:ring-2 focus:ring-indigo-500 outline-none"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-bold text-sm transition flex items-center justify-center space-x-2 shadow-lg shadow-indigo-100 disabled:opacity-50"
            >
              {loading && <Loader2 className="w-4 h-4 animate-spin" />}
              <span>{loading ? 'Please wait...' : isRegister ? 'Create Bank Account' : 'Sign In to Banking'}</span>
              {!loading && <ArrowRight className="w-4 h-4" />}
            </button>
          </form>

          {/* Quick Evaluator Access Pills */}
          <div className="mt-6 pt-6 border-t border-slate-100 text-center">
            <p className="text-[11px] font-bold text-slate-400 uppercase tracking-wider mb-2.5">
              Quick Evaluator Credentials
            </p>
            <div className="flex flex-wrap gap-2 justify-center">
              <button
                type="button"
                onClick={() => handleQuickLogin('alice', 'Password123!')}
                className="text-xs font-semibold px-2.5 py-1 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 transition"
              >
                Account Holder (alice)
              </button>
              <button
                type="button"
                onClick={() => handleQuickLogin('auditor', 'Password123!')}
                className="text-xs font-semibold px-2.5 py-1 rounded-lg bg-emerald-50 hover:bg-emerald-100 text-emerald-800 transition"
              >
                Auditor (auditor)
              </button>
              <button
                type="button"
                onClick={() => handleQuickLogin('admin', 'Password123!')}
                className="text-xs font-semibold px-2.5 py-1 rounded-lg bg-purple-50 hover:bg-purple-100 text-purple-800 transition"
              >
                Admin (admin)
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
