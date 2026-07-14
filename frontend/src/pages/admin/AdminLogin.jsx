import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useSession } from '../../context/SessionContext';
import { useAlerts } from '../../context/AlertContext';
import { useSubmit } from '../../hooks/useApi';
import AlertStack from '../../components/AlertStack';
import SubmitButton from '../../components/SubmitButton';

/** Sits outside the admin shell: it has its own full-bleed gradient background. */
export default function AdminLogin() {
    const [form, setForm] = useState({ email: '', password: '' });
    const [errors, setErrors] = useState({});
    const [pending, run] = useSubmit();
    const { setAdmin } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    const update = (event) =>
        setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

    async function submit(event) {
        event.preventDefault();
        setErrors({});
        await run(async () => {
            try {
                const result = await adminApi.login(form);
                setAdmin(result.data);
                pushFromResponse(result);
                navigate('/admin/dashboard');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    return (
        <div className="admin-login-bg">
            <section className="container min-vh-100 d-flex align-items-center justify-content-center py-5">
                <div className="col-12 col-md-6 col-lg-4">
                    <div className="card shadow">
                        <div className="card-body p-4 p-md-5">
                            <div className="text-center mb-4">
                                <i className="bi bi-shield-lock-fill text-danger" style={{ fontSize: '3rem' }} />
                                <h3 className="fw-bold mt-2">Admin Login</h3>
                                <p className="text-muted small mb-0">Pizza management access only</p>
                            </div>

                            <AlertStack bare />

                            <form onSubmit={submit} noValidate>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="email">
                                        Email
                                    </label>
                                    <div className="input-group">
                                        <span className="input-group-text">
                                            <i className="bi bi-envelope" />
                                        </span>
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
                                </div>
                                <div className="mb-3">
                                    <label className="form-label" htmlFor="password">
                                        Password
                                    </label>
                                    <div className="input-group">
                                        <span className="input-group-text">
                                            <i className="bi bi-key" />
                                        </span>
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
                                </div>
                                <SubmitButton pending={pending} className="btn btn-danger w-100">
                                    <i className="bi bi-box-arrow-in-right" /> Login
                                </SubmitButton>
                            </form>

                            <div className="text-center mt-3">
                                <Link to="/" className="small text-muted">
                                    <i className="bi bi-arrow-left" /> Back to site
                                </Link>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        </div>
    );
}
