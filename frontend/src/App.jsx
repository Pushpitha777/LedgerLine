import React, { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import StatementPage from './pages/StatementPage';
import AuditorPage from './pages/AuditorPage';
import AdminPage from './pages/AdminPage';
import Navbar from './components/Navbar';

function MainApp() {
  const { isAuthenticated, hasRole } = useAuth();
  const [activeTab, setActiveTab] = useState('dashboard');
  const [statementAccountId, setStatementAccountId] = useState(null);

  if (!isAuthenticated) {
    return <LoginPage />;
  }

  const navigateToStatement = (accountId) => {
    setStatementAccountId(accountId);
    setActiveTab('statement');
  };

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar activeTab={activeTab} setActiveTab={setActiveTab} />
      <main className="flex-1 pb-16">
        {activeTab === 'dashboard' && (
          <DashboardPage onNavigateToStatement={navigateToStatement} />
        )}
        {activeTab === 'statement' && (
          <StatementPage preselectedAccountId={statementAccountId} />
        )}
        {activeTab === 'auditor' && hasRole('ROLE_AUDITOR') && (
          <AuditorPage />
        )}
        {activeTab === 'admin' && hasRole('ROLE_ADMIN') && (
          <AdminPage />
        )}
      </main>
      <footer className="bg-white border-t border-slate-200 py-4 text-center text-xs text-slate-400">
        LedgerLine Banking Engine • Snovasys Fresher Hiring Challenge 2026 • Double-Entry ACID Relational Bookkeeper
      </footer>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <MainApp />
    </AuthProvider>
  );
}
