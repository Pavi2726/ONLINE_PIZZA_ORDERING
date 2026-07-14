import { useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { cart as cartApi, orders as orderApi, pizzas as pizzaApi } from '../api';
import { ApiError } from '../api/client';
import { useAlerts } from '../context/AlertContext';
import { useSession } from '../context/SessionContext';
import { useApi, useSubmit } from '../hooks/useApi';
import { usePagination } from '../hooks/usePagination';
import Pagination from '../components/Pagination';
import { money, onImageError } from '../lib/format';

/**
 * The menu, in two modes. With `?orderId=` present the page is adding pizzas to an
 * order still inside its edit window rather than to the cart — the same dual mode the
 * Thymeleaf page had. It is purely a UI state; the catalogue endpoint is the same.
 */
export default function PizzaList() {
    const [params, setParams] = useSearchParams();
    const navigate = useNavigate();
    const { customer, setCartItemCount } = useSession();
    const { pushAlert, pushFromResponse } = useAlerts();
    const [pending, run] = useSubmit();
    const [busyId, setBusyId] = useState(null);

    const search = params.get('search') ?? '';
    const category = params.get('category') ?? '';
    const sort = params.get('sort') ?? '';
    const orderId = params.get('orderId');

    // Filters live in the URL, so a filtered menu stays shareable and survives reload.
    const { data, loading } = useApi(
        () => pizzaApi.list({ search, category, sort }),
        [search, category, sort]
    );

    const list = data?.pizzas ?? [];
    const categories = data?.categories ?? [];
    const { pageItems, page, pageCount, setPage } = usePagination(list);

    function applyFilters(event) {
        event.preventDefault();
        const form = new FormData(event.currentTarget);
        const next = {};
        ['search', 'category', 'sort'].forEach((key) => {
            const value = form.get(key);
            if (value) next[key] = value;
        });
        if (orderId) next.orderId = orderId;
        setParams(next);
    }

    async function addToCart(pizzaId) {
        if (!customer) {
            navigate('/login');
            return;
        }
        setBusyId(pizzaId);
        await run(async () => {
            try {
                const result = await cartApi.addItem(pizzaId);
                setCartItemCount(result.data.itemCount);
                pushFromResponse(result);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    async function addToOrder(pizzaId) {
        setBusyId(pizzaId);
        await run(async () => {
            try {
                const result = await orderApi.addItem(orderId, pizzaId, 1);
                pushFromResponse(result);
                navigate(`/orders/edit/${orderId}`);
            } catch (error) {
                if (error instanceof ApiError) pushAlert('danger', error.message);
            } finally {
                setBusyId(null);
            }
        });
    }

    return (
        <section className="container my-5">
            <div className="mb-4">
                <h2 className="fw-bold mb-0">
                    <i className="bi bi-pizza" /> Our Menu
                </h2>
            </div>

            <form
                onSubmit={applyFilters}
                className="row g-2 align-items-end mb-4 p-3 bg-light rounded"
            >
                <div className="col-12 col-md-5">
                    <label className="form-label small fw-semibold" htmlFor="search">
                        Search by name
                    </label>
                    <div className="input-group">
                        <span className="input-group-text">
                            <i className="bi bi-search" />
                        </span>
                        <input
                            type="text"
                            id="search"
                            name="search"
                            className="form-control"
                            defaultValue={search}
                            placeholder="e.g. Margherita"
                        />
                    </div>
                </div>
                <div className="col-6 col-md-3">
                    <label className="form-label small fw-semibold" htmlFor="category">
                        Category
                    </label>
                    <select id="category" name="category" className="form-select" defaultValue={category}>
                        <option value="">All categories</option>
                        {categories.map((cat) => (
                            <option key={cat} value={cat}>
                                {cat}
                            </option>
                        ))}
                    </select>
                </div>
                <div className="col-6 col-md-2">
                    <label className="form-label small fw-semibold" htmlFor="sort">
                        Sort by price
                    </label>
                    <select id="sort" name="sort" className="form-select" defaultValue={sort}>
                        <option value="">Default</option>
                        <option value="priceAsc">Low &rarr; High</option>
                        <option value="priceDesc">High &rarr; Low</option>
                    </select>
                </div>
                <div className="col-12 col-md-2 d-grid">
                    <button type="submit" className="btn btn-danger">
                        <i className="bi bi-funnel" /> Apply
                    </button>
                </div>
            </form>

            {!loading && list.length === 0 && (
                <div className="text-center text-muted py-5">
                    <i className="bi bi-search fs-1" />
                    <p className="mt-2">No pizzas match your search.</p>
                </div>
            )}

            <div className="row g-4">
                {pageItems.map((pizza) => (
                    <div className="col-12 col-sm-6 col-lg-4 col-xl-3" key={pizza.id}>
                        <div className="card h-100 pizza-card shadow-sm">
                            <div className="position-relative">
                                <img
                                    src={pizza.imageUrl}
                                    className="card-img-top pizza-img"
                                    alt={pizza.name}
                                    onError={onImageError}
                                />
                                <span
                                    className={`badge position-absolute top-0 end-0 m-2 ${
                                        pizza.available ? 'bg-success' : 'bg-secondary'
                                    }`}
                                >
                                    {pizza.available ? 'Available' : 'Unavailable'}
                                </span>
                            </div>
                            <div className="card-body d-flex flex-column">
                                <div className="d-flex justify-content-between align-items-start">
                                    <h5 className="card-title fw-semibold mb-1">{pizza.name}</h5>
                                    <span className="badge bg-light text-dark border">{pizza.category}</span>
                                </div>
                                <p className="card-text small text-muted flex-grow-1">{pizza.description}</p>
                                <div className="d-flex justify-content-between align-items-center mt-2">
                                    <span className="fs-5 fw-bold text-danger">{money(pizza.price)}</span>

                                    {!pizza.available ? (
                                        <button className="btn btn-sm btn-secondary" disabled>
                                            Unavailable
                                        </button>
                                    ) : orderId ? (
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-primary"
                                            disabled={pending && busyId === pizza.id}
                                            onClick={() => addToOrder(pizza.id)}
                                        >
                                            🍕 Add to This Order
                                        </button>
                                    ) : (
                                        <button
                                            type="button"
                                            className="btn btn-sm btn-warning"
                                            disabled={pending && busyId === pizza.id}
                                            onClick={() => addToCart(pizza.id)}
                                        >
                                            <i className="bi bi-cart-plus" /> Add to Cart
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                ))}
            </div>

            <Pagination page={page} pageCount={pageCount} onChange={setPage} />
        </section>
    );
}
