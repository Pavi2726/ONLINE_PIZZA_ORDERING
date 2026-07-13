import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { auth } from '../api';
import { ApiError } from '../api/client';
import { useSession } from '../context/SessionContext';
import { useAlerts } from '../context/AlertContext';
import { useSubmit } from '../hooks/useApi';
import SubmitButton from '../components/SubmitButton';

export default function Login() {
    const [form, setForm] = useState({ email: '', password: '' });
    const [errors, setErrors] = useState({});
    const [pending, run] = useSubmit();
    const { setCustomer } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    const update = (event) =>
        setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

    async function submit(event) {
        event.preventDefault();
        setErrors({});
        await run(async () => {
            try {
                const result = await auth.login(form);
                setCustomer(result.data.customer, result.data.cartItemCount);
                pushFromResponse(result);
                navigate('/');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    // A bad email/password is a 401 with no field errors — surface it as an alert.
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    return (
        <section className="container my-5">
            <div className="row justify-content-center">
                <div className="col-12 col-md-6 col-lg-5">
                    <div className="card shadow-sm">
                        <div className="card-body p-4 p-md-5">
                            <h2 className="fw-bold mb-1">
                                <i className="bi bi-box-arrow-in-right" /> Welcome back
                            </h2>
                            <p className="text-muted mb-4">Login to order your favourite pizza.</p>

                            <form onSubmit={submit} noValidate>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="email">
                                        Email
                                    </label>
                                    <input
                                        type="email"
                                        id="email"
                                        name="email"
                                        className={`form-control ${errors.email ? 'is-invalid' : ''}`}
                                        value={form.email}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.email}</div>
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="password">
                                        Password
                                    </label>
                                    <input
                                        type="password"
                                        id="password"
                                        name="password"
                                        className={`form-control ${errors.password ? 'is-invalid' : ''}`}
                                        value={form.password}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.password}</div>
                                </div>
                                <SubmitButton pending={pending} className="btn btn-danger w-100">
                                    <i className="bi bi-box-arrow-in-right" /> Login
                                </SubmitButton>
                            </form>

                            <p className="text-center mt-3 mb-0">
                                New here? <Link to="/register">Create an account</Link>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
