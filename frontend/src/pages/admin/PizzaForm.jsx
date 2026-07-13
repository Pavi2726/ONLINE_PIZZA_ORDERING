import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useSubmit } from '../../hooks/useApi';
import SubmitButton from '../../components/SubmitButton';
import { onImageError } from '../../lib/format';

const EMPTY = { name: '', category: '', description: '', price: '', available: true };

/** Add and edit share one form; on edit the image is optional and the current one is shown. */
export default function PizzaForm() {
    const { id } = useParams();
    const isEdit = Boolean(id);
    const navigate = useNavigate();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();

    const [form, setForm] = useState(EMPTY);
    const [imageUrl, setImageUrl] = useState('');
    const [image, setImage] = useState(null);
    const [errors, setErrors] = useState({});

    useEffect(() => {
        if (!isEdit) return;
        adminApi.pizzas
            .get(id)
            .then((pizza) => {
                setForm({
                    name: pizza.name,
                    category: pizza.category,
                    description: pizza.description,
                    price: pizza.price,
                    available: pizza.available,
                });
                setImageUrl(pizza.imageUrl);
            })
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

        // Scalars plus the file in one multipart body — the server binds it straight
        // onto PizzaDTO, so the existing bean validation applies unchanged.
        const body = new FormData();
        Object.entries(form).forEach(([key, value]) => body.append(key, value));
        if (image) body.append('image', image);

        await run(async () => {
            try {
                const result = isEdit
                    ? await adminApi.pizzas.update(id, body)
                    : await adminApi.pizzas.create(body);
                pushFromResponse(result);
                navigate('/admin/pizzas');
            } catch (error) {
                if (error instanceof ApiError) {
                    setErrors(error.fieldErrors);
                    if (Object.keys(error.fieldErrors).length === 0) pushAlert('danger', error.message);
                }
            }
        });
    }

    return (
        <div className="row justify-content-center">
            <div className="col-12 col-xl-9">
                <div className="card shadow-sm">
                    <div className="card-body p-4 p-md-5">
                        <h2 className="fw-bold mb-4">
                            {isEdit ? (
                                <>
                                    <i className="bi bi-pencil-square" /> Edit Pizza
                                </>
                            ) : (
                                <>
                                    <i className="bi bi-plus-circle" /> Add a New Pizza
                                </>
                            )}
                        </h2>

                        <form onSubmit={submit} noValidate>
                            <div className="row g-3">
                                <div className="col-md-8">
                                    <label className="form-label" htmlFor="name">
                                        Pizza Name
                                    </label>
                                    <input
                                        type="text"
                                        id="name"
                                        name="name"
                                        maxLength="120"
                                        className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                                        value={form.name}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.name}</div>
                                </div>

                                <div className="col-md-4">
                                    <label className="form-label" htmlFor="category">
                                        Category
                                    </label>
                                    <input
                                        type="text"
                                        id="category"
                                        name="category"
                                        maxLength="60"
                                        placeholder="e.g. Veg, Non-Veg"
                                        className={`form-control ${errors.category ? 'is-invalid' : ''}`}
                                        value={form.category}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.category}</div>
                                </div>

                                <div className="col-12">
                                    <label className="form-label" htmlFor="description">
                                        Description
                                    </label>
                                    <textarea
                                        id="description"
                                        name="description"
                                        rows="3"
                                        maxLength="1000"
                                        className={`form-control ${errors.description ? 'is-invalid' : ''}`}
                                        value={form.description}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.description}</div>
                                </div>

                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="price">
                                        Price (₹)
                                    </label>
                                    <input
                                        type="number"
                                        id="price"
                                        name="price"
                                        step="0.01"
                                        min="0.01"
                                        className={`form-control ${errors.price ? 'is-invalid' : ''}`}
                                        value={form.price}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.price}</div>
                                </div>

                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="image">
                                        Pizza Image {isEdit && <span className="text-muted small">(optional)</span>}
                                    </label>
                                    <input
                                        type="file"
                                        id="image"
                                        accept="image/*"
                                        className={`form-control ${errors.imageUrl ? 'is-invalid' : ''}`}
                                        onChange={(event) => setImage(event.target.files[0] ?? null)}
                                    />
                                    <div className="invalid-feedback">{errors.imageUrl}</div>
                                </div>

                                {isEdit && imageUrl && (
                                    <div className="col-12">
                                        <label className="form-label">Current Image</label>
                                        <div>
                                            <img
                                                src={imageUrl}
                                                className="img-fluid rounded current-image"
                                                alt={form.name}
                                                onError={onImageError}
                                            />
                                        </div>
                                    </div>
                                )}

                                <div className="col-12">
                                    <div className="form-check form-switch">
                                        <input
                                            className="form-check-input"
                                            type="checkbox"
                                            id="available"
                                            name="available"
                                            checked={form.available}
                                            onChange={update}
                                        />
                                        <label className="form-check-label" htmlFor="available">
                                            Available for ordering
                                        </label>
                                    </div>
                                </div>
                            </div>

                            <div className="d-flex gap-2 mt-4">
                                <SubmitButton pending={pending} className="btn btn-danger">
                                    <i className="bi bi-check-circle" /> {isEdit ? 'Update Pizza' : 'Save Pizza'}
                                </SubmitButton>
                                <Link to="/admin/pizzas" className="btn btn-outline-secondary">
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
