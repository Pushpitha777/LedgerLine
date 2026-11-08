import React from 'react';
import { useAuth } from '../context/AuthContext';
import { ShieldCheck, LogOut, User, Landmark, Scale, Settings } from 'lucide-react';

export default function Navbar({ activeTab, setActiveTab }) {
  const { user, logout, hasRole } = useAuth();

  return (
    <header className="bg-white border-b border-slate-200 sticky top-0 z-30">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex justify-between h-16">
          <div className="flex items-center space-x-8">
            <div className="flex items-center space-x-3 cursor-pointer" onClick={() => setActiveTab('dashboard')}>
              <div className="w-10 h-10 rounded-xl bg-indigo-600 flex items-center justify-center text-white shadow-md shadow-indigo-100">
                <Landmark className="w-5 h-5" />
              </div>
              <div>
                <span className="text-xl font-extrabold tracking-tight text-slate-900">Ledger<span className="text-indigo-600">Line</span></span>
                <span className="hidden sm:inline-block ml-2 text-xs font-semibold px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-100">Core Banking</span>
              </div>
            </div>

            <nav className="hidden md:flex space-x-1">
              <button
                onClick={() => setActiveTab('dashboard')}
                className={`px-3 py-2 rounded-lg text-sm font-semibold transition ${
                  activeTab === 'dashboard'
                    ? 'bg-indigo-50 text-indigo-600'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                Accounts & Dashboard
              </button>

              <button
                onClick={() => setActiveTab('statement')}
                className={`px-3 py-2 rounded-lg text-sm font-semibold transition ${
                  activeTab === 'statement'
                    ? 'bg-indigo-50 text-indigo-600'
                    : 'text-slate-600 hover:text-slate-900 hover:bg-slate-50'
                }`}
              >
                Statements
              </button>

              {hasRole('ROLE_AUDITOR') && (
                <button
                  onClick={() => setActiveTab('auditor')}
                  className={`flex items-center space-x-1.5 px-3 py-2 rounded-lg text-sm font-semibold transition ${
                    activeTab === 'auditor'
                      ? 'bg-emerald-50 text-emerald-700'
                      : 'text-slate-600 hover:text-emerald-700 hover:bg-emerald-50/50'
                  }`}
                >
                  <Scale className="w-4 h-4" />
                  <span>Auditor Invariant</span>
                </button>
              )}

              {hasRole('ROLE_ADMIN') && (
                <button
                  onClick={() => setActiveTab('admin')}
                  className={`flex items-center space-x-1.5 px-3 py-2 rounded-lg text-sm font-semibold transition ${
                    activeTab === 'admin'
                      ? 'bg-purple-50 text-purple-700'
                      : 'text-slate-600 hover:text-purple-700 hover:bg-purple-50/50'
                  }`}
                >
                  <Settings className="w-4 h-4" />
                  <span>Admin Panel</span>
                </button>
              )}
            </nav>
          </div>

          <div className="flex items-center space-x-4">
            <div className="flex items-center space-x-2.5">
              <div className="w-9 h-9 rounded-full bg-slate-100 flex items-center justify-center text-slate-600 font-bold border border-slate-200">
                {user?.fullName?.charAt(0) || user?.username?.charAt(0) || 'U'}
              </div>
              <div className="hidden sm:block text-left">
                <p className="text-sm font-bold text-slate-800 leading-tight">{user?.fullName || user?.username}</p>
                <div className="flex space-x-1">
                  {user?.roles?.map((r) => (
                    <span key={r} className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider">
                      {r.replace('ROLE_', '')}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <button
              onClick={logout}
              title="Sign Out"
              className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition"
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
