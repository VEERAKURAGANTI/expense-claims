import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Layout from './components/Layout';
import RequireRole from './components/RequireRole';
import LoginPage from './pages/LoginPage';
import StaffDashboard from './pages/StaffDashboard';
import NewClaimPage from './pages/NewClaimPage';
import ClaimReviewPage from './pages/ClaimReviewPage';
import ClaimDetailPage from './pages/ClaimDetailPage';
import ManagerQueuePage from './pages/ManagerQueuePage';
import FinanceQueuePage from './pages/FinanceQueuePage';
import FinanceReportPage from './pages/FinanceReportPage';

function Home() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role === 'FINANCE') return <Navigate to="/finance/queue" replace />;
  if (user.role === 'MANAGER') return <Navigate to="/manager/queue" replace />;
  return <Navigate to="/claims" replace />;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Layout>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/" element={<Home />} />

            <Route path="/claims" element={<RequireRole roles={['STAFF', 'MANAGER']}><StaffDashboard /></RequireRole>} />
            <Route path="/claims/new" element={<RequireRole roles={['STAFF', 'MANAGER']}><NewClaimPage /></RequireRole>} />
            <Route path="/claims/:id/edit" element={<RequireRole roles={['STAFF', 'MANAGER']}><ClaimReviewPage /></RequireRole>} />
            <Route path="/claims/:id" element={<RequireRole><ClaimDetailPage /></RequireRole>} />

            <Route path="/manager/queue" element={<RequireRole roles={['MANAGER', 'FINANCE']}><ManagerQueuePage /></RequireRole>} />

            <Route path="/finance/queue" element={<RequireRole roles={['FINANCE']}><FinanceQueuePage /></RequireRole>} />
            <Route path="/finance/report" element={<RequireRole roles={['FINANCE']}><FinanceReportPage /></RequireRole>} />

            <Route path="*" element={<p>Page not found.</p>} />
          </Routes>
        </Layout>
      </BrowserRouter>
    </AuthProvider>
  );
}
