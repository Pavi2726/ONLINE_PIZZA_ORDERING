import { useEffect } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';

import { setUnauthorizedHandler } from './api/client';
import { useSession } from './context/SessionContext';
import { RequireAdmin, RequireCustomer } from './components/Guards';
import CustomerLayout from './components/layout/CustomerLayout';
import AdminLayout from './components/layout/AdminLayout';

import Home from './pages/Home';
import Login from './pages/Login';
import Register from './pages/Register';
import PizzaList from './pages/PizzaList';
import Cart from './pages/Cart';
import Checkout from './pages/Checkout';
import OrderSuccess from './pages/OrderSuccess';
import OrderHistory from './pages/OrderHistory';
import EditOrder from './pages/EditOrder';
import NotFound from './pages/NotFound';

import AdminLogin from './pages/admin/AdminLogin';
import Dashboard from './pages/admin/Dashboard';
import AdminPizzaList from './pages/admin/AdminPizzaList';
import PizzaForm from './pages/admin/PizzaForm';
import CouponList from './pages/admin/CouponList';
import CouponForm from './pages/admin/CouponForm';
import CustomerList from './pages/admin/CustomerList';
import CustomerForm from './pages/admin/CustomerForm';
import AdminOrderList from './pages/admin/AdminOrderList';
import AdminOrderDetail from './pages/admin/AdminOrderDetail';

/**
 * Turns a 401 from any API call into the redirect the server used to perform itself.
 * Lives inside the Router so it can navigate.
 */
function ApiBridge() {
    const navigate = useNavigate();
    const { setCustomer, setAdmin } = useSession();

    useEffect(() => {
        setUnauthorizedHandler((path) => {
            if (path.startsWith('/admin')) {
                setAdmin(null);
                navigate('/admin/login');
            } else {
                setCustomer(null, 0);
                navigate('/login');
            }
        });
    }, [navigate, setCustomer, setAdmin]);

    return null;
}

export default function App() {
    return (
        <>
            <ApiBridge />
            <Routes>
                <Route element={<CustomerLayout />}>
                    <Route path="/" element={<Home />} />
                    <Route path="/login" element={<Login />} />
                    <Route path="/register" element={<Register />} />
                    <Route path="/pizzas" element={<PizzaList />} />

                    <Route element={<RequireCustomer />}>
                        <Route path="/cart" element={<Cart />} />
                        <Route path="/checkout" element={<Checkout />} />
                        <Route path="/orders/success/:orderNumber" element={<OrderSuccess />} />
                        <Route path="/orders/history" element={<OrderHistory />} />
                        <Route path="/orders/edit/:orderId" element={<EditOrder />} />
                    </Route>

                    <Route path="*" element={<NotFound />} />
                </Route>

                {/* The admin login has its own full-bleed background, not the admin shell. */}
                <Route path="/admin/login" element={<AdminLogin />} />
                <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />

                <Route element={<RequireAdmin />}>
                    <Route element={<AdminLayout />}>
                        <Route path="/admin/dashboard" element={<Dashboard />} />
                        <Route path="/admin/pizzas" element={<AdminPizzaList />} />
                        <Route path="/admin/pizzas/add" element={<PizzaForm />} />
                        <Route path="/admin/pizzas/edit/:id" element={<PizzaForm />} />
                        <Route path="/admin/coupons" element={<CouponList />} />
                        <Route path="/admin/coupons/add" element={<CouponForm />} />
                        <Route path="/admin/coupons/edit/:id" element={<CouponForm />} />
                        <Route path="/admin/customers" element={<CustomerList />} />
                        <Route path="/admin/customers/edit/:id" element={<CustomerForm />} />
                        <Route path="/admin/orders" element={<AdminOrderList />} />
                        <Route path="/admin/orders/:id" element={<AdminOrderDetail />} />
                    </Route>
                </Route>
            </Routes>
        </>
    );
}
