import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useSession } from '../../context/SessionContext';
import { useAlerts } from '../../context/AlertContext';
import { post } from '../../api/client';
import AlertStack from '../AlertStack';
import ThemeToggle from '../ThemeToggle';

function Navbar() {
    const { customer, cartItemCount, setCustomer } = useSession();
    const { pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    async function logout() {
        const result = await post('/auth/logout');
        setCustomer(null, 0);
        pushFromResponse(result);
        navigate('/login');
    }

    return (
        <nav className="navbar navbar-expand-lg navbar-dark sticky-top app-navbar">
            <div className="container">
                <Link className="navbar-brand fw-bold" to="/">
                    <i className="bi bi-pizza-fill" /> Pizza Palace
                </Link>
                <button
                    className="navbar-toggler"
                    type="button"
                    data-bs-toggle="collapse"
                    data-bs-target="#mainNav"
                    aria-controls="mainNav"
                    aria-expanded="false"
                    aria-label="Toggle navigation"
                >
                    <span className="navbar-toggler-icon" />
                </button>
                <div className="collapse navbar-collapse" id="mainNav">
                    <ul className="navbar-nav ms-auto align-items-lg-center">
                        <li className="nav-item">
                            <ThemeToggle />
                        </li>
                        <li className="nav-item">
                            <NavLink className="nav-link" to="/">
                                <i className="bi bi-house-door" /> Home
                            </NavLink>
                        </li>
                        <li className="nav-item">
                            <NavLink className="nav-link" to="/pizzas">
                                <i className="bi bi-list-ul" /> Pizzas
                            </NavLink>
                        </li>
                        <li className="nav-item">
                            <NavLink className="nav-link" to="/drinks">
                                 Drinks
                            </NavLink>
                        </li>

                        {customer ? (
                            <>
                                <li className="nav-item">
                                    <NavLink className="nav-link" to="/cart">
                                        <i className="bi bi-cart3" /> Cart
                                        {cartItemCount > 0 && (
                                            <span
                                                className="badge rounded-pill bg-danger"
                                                aria-label="items in cart"
                                            >
                                                {cartItemCount}
                                            </span>
                                        )}
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink className="nav-link" to="/orders/history">
                                        <i className="bi bi-clock-history" /> Order History
                                    </NavLink>
                                </li>
                                <li className="nav-item dropdown">
                                    <a
                                        className="nav-link dropdown-toggle"
                                        href="#"
                                        role="button"
                                        data-bs-toggle="dropdown"
                                        aria-expanded="false"
                                    >
                                        <i className="bi bi-person-circle" /> <span>{customer.firstName}</span>
                                    </a>
                                    <ul className="dropdown-menu dropdown-menu-end">
                                        <li>
                                            <button className="dropdown-item" type="button" onClick={logout}>
                                                <i className="bi bi-box-arrow-right" /> Logout
                                            </button>
                                        </li>
                                    </ul>
                                </li>
                            </>
                        ) : (
                            <>
                                <li className="nav-item">
                                    <NavLink className="nav-link" to="/login">
                                        <i className="bi bi-box-arrow-in-right" /> Login
                                    </NavLink>
                                </li>
                                <li className="nav-item">
                                    <NavLink className="nav-link" to="/register">
                                        <i className="bi bi-person-plus" /> Register
                                    </NavLink>
                                </li>
                            </>
                        )}
                    </ul>
                </div>
            </div>
        </nav>
    );
}

function Footer() {
    return (
        <footer className="app-footer mt-5 py-4">
            <div className="container text-center">
                <p className="mb-1">
                    <i className="bi bi-pizza-fill" /> <strong>Pizza Palace</strong>
                </p>
                <p className="mb-1 small">Freshly baked, delivered hot to your door.</p>
                <p className="mb-0 small text-muted">
                    &copy; {new Date().getFullYear()} Pizza Palace. All rights reserved.
                </p>
            </div>
        </footer>
    );
}

export default function CustomerLayout() {
    return (
        <>
            <Navbar />
            <AlertStack />
            <Outlet />
            <Footer />
        </>
    );
}
