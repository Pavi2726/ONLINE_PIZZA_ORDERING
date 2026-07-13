import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useSubmit } from '../../hooks/useApi';
import SubmitButton from '../../components/SubmitButton';

const EMPTY = { firstName: '', lastName: '', email: '', phone: '', address: '' };

/** Admins edit a customer's details but never their password. */
export default function CustomerForm() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const [form, setForm] = useState(EMPTY);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        adminApi.customers
            .get(id)
            .then((customer) =>
                setForm({
                    firstName: customer.firstName,
                    lastName: customer.lastName,
                    email: customer.email,
                    phone: customer.phone,
                    address: customer.address ?? '',
                })
            )
            .catch((error) => {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            });
    }, [id, pushAlert]);

    const update = (event) =>
        setForm((current) => ({ ...current, [event.target.name]: event.target.value }));

    async function submit(event) {
        event.preventDefault();
        setErrors({});
        await run(async () => {
            try {
                pushFromResponse(await adminApi.customers.update(id, form));
                navigate('/admin/customers');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    // A duplicate email or phone is a 409 with no field errors.
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    const field = (name, label, type = 'text') => (
        <div className="col-md-6">
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
        <div className="row justify-content-center">
            <div className="col-12 col-xl-8">
                <div className="card shadow-sm">
                    <div className="card-body p-4 p-md-5">
                        <h2 className="fw-bold mb-4">
                            <i className="bi bi-person-gear" /> Edit Customer
                        </h2>

                        <form onSubmit={submit} noValidate>
                            <div className="row g-3">
                                {field('firstName', 'First Name')}
                                {field('lastName', 'Last Name')}
                                {field('email', 'Email', 'email')}
                                {field('phone', 'Phone')}
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

                            <div className="d-flex gap-2 mt-4">
                                <SubmitButton pending={pending} className="btn btn-danger">
                                    <i className="bi bi-check-circle" /> Update Customer
                                </SubmitButton>
                                <Link to="/admin/customers" className="btn btn-outline-secondary">
                                    Cancel
                                </Link>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    );
}
