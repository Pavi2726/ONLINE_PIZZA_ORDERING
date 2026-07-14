import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { auth } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSubmit } from '../hooks/useApi';
import SubmitButton from '../components/SubmitButton';

const EMPTY = {
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    address: '',
};

export default function Register() {
    const [form, setForm] = useState(EMPTY);
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState('');
    const [pending, run] = useSubmit();
    const { pushAlert, pushFromResponse } = useAlerts();
    const navigate = useNavigate();

    const update = (event) =>
        setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

    async function submit(event) {
        event.preventDefault();
        setErrors({});
        setFormError('');

        // The same two client-side checks the old page ran before posting. The server
        // enforces both regardless; this just saves a round trip.
        if (form.password.length < 8) {
            setFormError('Password must be at least 8 characters.');
            return;
        }
        if (form.password !== form.confirmPassword) {
            setFormError('Passwords do not match.');
            return;
        }

        await run(async () => {
            try {
                const result = await auth.register(form);
                pushFromResponse(result);
                navigate('/login');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    // A duplicate email or phone is a 409 with no field errors.
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    const field = (name, label, type = 'text', width = 'col-md-6') => (
        <div className={width}>
            <label className="form-label" htmlFor={name}>
                {label}
            </label>
            <input
                type={type}
                id={name}
                name={name}
                className={`form-control ${errors[name] ? 'is-invalid' : ''}`}
                value={form[name]}
                onChange={update}
            />
            <div className="invalid-feedback">{errors[name]}</div>
        </div>
    );

    return (
        <section className="container my-5">
            <div className="row justify-content-center">
                <div className="col-12 col-lg-8">
                    <div className="card shadow-sm">
                        <div className="card-body p-4 p-md-5">
                            <h2 className="fw-bold mb-1">
                                <i className="bi bi-person-plus" /> Create your account
                            </h2>
                            <p className="text-muted mb-4">Join Pizza Palace and start ordering.</p>

                            {formError && (
                                <div className="alert alert-danger" role="alert">
                                    {formError}
                                </div>
                            )}

                            <form onSubmit={submit} noValidate>
                                <div className="row g-3">
                                    {field('firstName', 'First Name')}
                                    {field('lastName', 'Last Name')}
                                    {field('email', 'Email', 'email')}
                                    {field('phone', 'Phone')}
                                    {field('password', 'Password', 'password')}
                                    {field('confirmPassword', 'Confirm Password', 'password')}
                                    <div className="col-12">
                                        <label className="form-label" htmlFor="address">
                                            Address
                                        </label>
                                        <textarea
                                            id="address"
                                            name="address"
                                            rows="2"
                                            className={`form-control ${errors.address ? 'is-invalid' : ''}`}
                                            value={form.address}
                                            onChange={update}
                                        />
                                        <div className="invalid-feedback">{errors.address}</div>
                                    </div>
                                </div>

                                <SubmitButton pending={pending} className="btn btn-danger w-100 mt-4">
                                    <i className="bi bi-check-circle" /> Register
                                </SubmitButton>
                            </form>

                            <p className="text-center mt-3 mb-0">
                                Already have an account? <Link to="/login">Login here</Link>
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
