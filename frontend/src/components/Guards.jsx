import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useSession } from '../context/SessionContext';

/**
 * Client-side mirrors of the server's auth interceptors. The server still enforces
 * access — every guarded API route 401s without a session — so these only spare the
 * user a pointless request and a flash of an empty page.
 */

function Spinner() {
    return (
        <div className="d-flex justify-content-center align-items-center py-5 flex-grow-1">
            <div className="spinner-border text-danger" role="status">
                <span className="visually-hidden">Loading…</span>
            </div>
        </div>
    );
}

export function RequireCustomer() {
    const { customer, loading } = useSession();
    const location = useLocation();

    if (loading) return <Spinner />;
    if (!customer) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
    return <Outlet />;
}

export function RequireAdmin() {
    const { admin, loading } = useSession();

    if (loading) return <Spinner />;
    if (!admin) return <Navigate to="/admin/login" replace />;
    return <Outlet />;
}

export { Spinner };
