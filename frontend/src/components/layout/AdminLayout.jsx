import { useEffect } from 'react';
import { Offcanvas } from 'bootstrap';
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useSession } from '../../context/SessionContext';
import { useAlerts } from '../../context/AlertContext';
import { post } from '../../api/client';
import AlertStack from '../AlertStack';
import ThemeToggle from '../ThemeToggle';

const LINKS = [
    { to: '/admin/dashboard', icon: 'bi-speedometer2', label: 'Dashboard' },
    { to: '/admin/pizzas/add', icon: 'bi-plus-circle', label: 'Add Pizza' },
    { to: '/admin/pizzas', icon: 'bi-card-list', label: 'Manage Pizzas', end: true },
    { to: '/admin/drinks/add', icon: 'bi-cup-straw', label: 'Add Drink' },
    { to: '/admin/drinks', icon: 'bi-cup-hot', label: 'Manage Drinks', end: true },
    { to: '/admin/coupons', icon: 'bi-ticket-perforated', label: 'Manage Coupons', end: true },
    { to: '/admin/customers', icon: 'bi-people', label: 'Manage Customers', end: true },
    { to: '/admin/orders', icon: 'bi-receipt', label: 'Manage Orders', end: true },
];

function SidebarLinks({ onLogout }) {
    return (
        <ul className="nav flex-column admin-nav">
            {LINKS.map((link) => (
                <li className="nav-item" key={link.to}>
                    <NavLink className="nav-link" to={link.to} end={link.end}>
                        <i className={`bi ${link.icon}`} /> {link.label}
                    </NavLink>
                </li>
            ))}
            <li className="nav-item mt-2">
                <button className="nav-link text-warning btn btn-link text-start w-100" type="button" onClick={onLogout}>
                    <i className="bi bi-box-arrow-right" /> Logout
                </button>
            </li>
        </ul>
    );
}

export default function AdminLayout() {
    const { admin, setAdmin } = useSession();
    const { pushFromResponse } = useAlerts();
    const navigate = useNavigate();
    const location = useLocation();

    // The offcanvas is Bootstrap-managed and does not know the SPA navigated, so it
    // would stay open (backdrop and all) after tapping a link on mobile.
    useEffect(() => {
        const element = document.getElementById('adminSidebar');
        if (!element) return;
        Offcanvas.getInstance(element)?.hide();
    }, [location.pathname]);

    async function logout() {
        const result = await post('/admin/logout');
        setAdmin(null);
        pushFromResponse(result);
        navigate('/admin/login');
    }

    return (
        <div className="admin-body">
            <nav className="navbar navbar-dark admin-topbar sticky-top px-3">
                <button
                    className="btn btn-outline-light btn-sm d-lg-none"
                    type="button"
                    data-bs-toggle="offcanvas"
                    data-bs-target="#adminSidebar"
                >
                    <i className="bi bi-list" />
                </button>
                <span className="navbar-brand fw-bold mb-0">
                    <i className="bi bi-shield-lock" /> Pizza Palace Admin
                </span>
                <div className="ms-auto d-flex align-items-center gap-3">
                    <ThemeToggle />
                    {admin && (
                        <span className="text-white-50 small d-none d-sm-inline">
                            <i className="bi bi-person-circle" /> <span>{admin.name}</span>
                        </span>
                    )}
                    <button type="button" className="btn btn-sm btn-light" onClick={logout}>
                        <i className="bi bi-box-arrow-right" /> Logout
                    </button>
                </div>
            </nav>

            <div className="offcanvas offcanvas-start admin-sidebar" tabIndex="-1" id="adminSidebar">
                <div className="offcanvas-header">
                    <h5 className="offcanvas-title">Menu</h5>
                    <button
                        type="button"
                        className="btn-close btn-close-white"
                        data-bs-dismiss="offcanvas"
                        aria-label="Close"
                    />
                </div>
                <div className="offcanvas-body">
                    <SidebarLinks onLogout={logout} />
                </div>
            </div>

            <div className="container-fluid">
                <div className="row">
                    <aside className="col-lg-2 admin-sidebar p-3 d-none d-lg-block">
                        <SidebarLinks onLogout={logout} />
                    </aside>
                    <main className="col-lg-10 p-4">
                        <AlertStack bare />
                        <Outlet />
                    </main>
                </div>
            </div>
        </div>
    );
}
