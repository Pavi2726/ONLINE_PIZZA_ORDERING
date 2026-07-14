import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { admin as adminApi } from '../../api';
import { ApiError } from '../../api/client';
import { useAlerts } from '../../context/AlertContext';
import { useSubmit } from '../../hooks/useApi';
import SubmitButton from '../../components/SubmitButton';
import { onImageError } from '../../lib/format';

const CATEGORIES = [
    'Soft Drinks',
    'Diet Drinks',
    'Sugar-Free Drinks',
    'Zero Sugar',
    'Juices',
    'Coffee',
    'Tea',
    'Energy Drinks',
    'Milkshakes',
    'Water',
];

const SIZES = ['SMALL', 'MEDIUM', 'LARGE'];

const EMPTY = {
    name: '',
    category: '',
    description: '',
    price: '',
    size: 'MEDIUM',
    available: true,
};

/** Add and edit share one form; on edit the image is optional and the current one is shown. */
export default function DrinkForm() {
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
        adminApi.drinks
            .get(id)
            .then((drink) => {
                setForm({
                    name: drink.name,
                    category: drink.category,
                    description: drink.description,
                    price: drink.price,
                    size: drink.size ?? 'MEDIUM',
                    available: drink.available,
                });
                setImageUrl(drink.imageUrl);
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

        const body = new FormData();
        Object.entries(form).forEach(([key, value]) => body.append(key, value));
        if (image) body.append('image', image);

        await run(async () => {
            try {
                const result = isEdit
                    ? await adminApi.drinks.update(id, body)
                    : await adminApi.drinks.create(body);
                pushFromResponse(result);
                navigate('/admin/drinks');
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
                                    <i className="bi bi-pencil-square" /> Edit Drink
                                </>
                            ) : (
                                <>
                                    <i className="bi bi-plus-circle" /> Add a New Drink
                                </>
                            )}
                        </h2>

                        <form onSubmit={submit} noValidate>
                            <div className="row g-3">
                                <div className="col-md-8">
                                    <label className="form-label" htmlFor="drink-name">
                                        Drink Name
                                    </label>
                                    <input
                                        type="text"
                                        id="drink-name"
                                        name="name"
                                        maxLength="120"
                                        className={`form-control ${errors.name ? 'is-invalid' : ''}`}
                                        value={form.name}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.name}</div>
                                </div>

                                <div className="col-md-4">
                                    <label className="form-label" htmlFor="drink-size">
                                        Size
                                    </label>
                                    <select
                                        id="drink-size"
                                        name="size"
                                        className={`form-select ${errors.size ? 'is-invalid' : ''}`}
                                        value={form.size}
                                        onChange={update}
                                    >
                                        {SIZES.map((s) => (
                                            <option key={s} value={s}>
                                                {s.charAt(0) + s.slice(1).toLowerCase()}
                                            </option>
                                        ))}
                                    </select>
                                    <div className="invalid-feedback">{errors.size}</div>
                                </div>

                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="drink-category">
                                        Category
                                    </label>
                                    <select
                                        id="drink-category"
                                        name="category"
                                        className={`form-select ${errors.category ? 'is-invalid' : ''}`}
                                        value={form.category}
                                        onChange={update}
                                    >
                                        <option value="">Select a category</option>
                                        {CATEGORIES.map((cat) => (
                                            <option key={cat} value={cat}>
                                                {cat}
                                            </option>
                                        ))}
                                    </select>
                                    <div className="invalid-feedback">{errors.category}</div>
                                </div>

                                <div className="col-md-6">
                                    <label className="form-label" htmlFor="drink-price">
                                        Price (₹)
                                    </label>
                                    <input
                                        type="number"
                                        id="drink-price"
                                        name="price"
                                        step="0.01"
                                        min="0.01"
                                        className={`form-control ${errors.price ? 'is-invalid' : ''}`}
                                        value={form.price}
                                        onChange={update}
                                    />
                                    <div className="invalid-feedback">{errors.price}</div>
                                </div>

                                <div className="col-12">
                                    <label className="form-label" htmlFor="drink-description">
                                        Description
                                    </label>
                                    <textarea
                                        id="drink-description"
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
                                    <label className="form-label" htmlFor="drink-image">
                                        Drink Image {isEdit && <span className="text-muted small">(optional)</span>}
                                    </label>
                                    <input
                                        type="file"
                                        id="drink-image"
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
                                            id="drink-available"
                                            name="available"
                                            checked={form.available}
                                            onChange={update}
                                        />
                                        <label className="form-check-label" htmlFor="drink-available">
                                            Available for ordering
                                        </label>
                                    </div>
                                </div>
                            </div>

                            <div className="d-flex gap-2 mt-4">
                                <SubmitButton pending={pending} className="btn btn-primary">
                                    <i className="bi bi-check-circle" /> {isEdit ? 'Update Drink' : 'Save Drink'}
                                </SubmitButton>
                                <Link to="/admin/drinks" className="btn btn-outline-secondary">
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
