import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useSubmit } from '../../hooks/useApi';
import SubmitButton from '../../components/SubmitButton';

const EMPTY = { couponCode: '', discountPercentage: '', active: true };

export default function CouponForm() {
    const { id } = useParams();
    const isEdit = Boolean(id);
    const navigate = useNavigate();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const [form, setForm] = useState(EMPTY);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (!isEdit) return;
        adminApi.coupons
            .get(id)
            .then((coupon) =>
                setForm({
                    couponCode: coupon.couponCode,
                    discountPercentage: coupon.discountPercentage,
                    active: coupon.active,
                })
            )
            .catch((error) => {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            });
    }, [id, isEdit, pushAlert]);

    const update = (event) => {
        const { name, value, type, checked } = event.target;
        setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
    };

    async function submit(event) {
        event.preventDefault();
        setErrors({});

        const body = { ...form, discountPercentage: Number(form.discountPercentage) };

        await run(async () => {
            try {
                const result = isEdit
                    ? await adminApi.coupons.update(id, body)
                    : await adminApi.coupons.create(body);
                pushFromResponse(result);
                navigate('/admin/coupons');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    // A duplicate coupon code is a 409 with no field errors.
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    return (
        <div className="row justify-content-center">
            <div className="col-12 col-xl-7">
                <div className="card shadow-sm">
                    <div className="card-body p-4 p-md-5">
                        <h2 className="fw-bold mb-4">
                            <i className="bi bi-ticket-perforated" />{' '}
                            {isEdit ? 'Edit Coupon' : 'Add a New Coupon'}
                        </h2>

                        <form onSubmit={submit} noValidate>
                            <div className="row g-3">
                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="couponCode">
                                        Coupon Code
                                    </label>
                                    <input
                                        type="text"
                                        id="couponCode"
                                        name="couponCode"
                                        maxLength="50"
                                        placeholder="e.g. PIZZA20"
                                        className={`form-control ${errors.couponCode ? 'is-invalid' : ''}`}
                                        value={form.couponCode}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.couponCode}</div>
                                </div>

                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="discountPercentage">
                                        Discount (%)
                                    </label>
                                    <input
                                        type="number"
                                        id="discountPercentage"
                                        name="discountPercentage"
                                        min="1"
                                        max="100"
                                        className={`form-control ${
                                            errors.discountPercentage ? 'is-invalid' : ''
                                        }`}
                                        value={form.discountPercentage}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.discountPercentage}</div>
                                </div>

                                <div className="col-12">
                                    <div className="form-check form-switch">
                                        <input
                                            className="form-check-input"
                                            type="checkbox"
                                            id="active"
                                            name="active"
                                            checked={form.active}
                                            onChange={update}
                                        />
                                        <label className="form-check-label" htmlFor="active">
                                            Active
                                        </label>
                                    </div>
                                </div>
                            </div>

                            <div className="d-flex gap-2 mt-4">
                                <SubmitButton pending={pending} className="btn btn-danger">
                                    <i className="bi bi-check-circle" />{' '}
                                    {isEdit ? 'Update Coupon' : 'Save Coupon'}
                                </SubmitButton>
                                <Link to="/admin/coupons" className="btn btn-outline-secondary">
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
