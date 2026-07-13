import { Link } from 'react-router-dom';
import { pizzas as pizzaApi } from '../api';
import { useApi } from '../hooks/useApi';
import { abbreviate, money, onImageError } from '../lib/format';

const FEATURED_LIMIT = 4;

export default function Home() {
    const { data } = useApi(() => pizzaApi.list(), []);

    // Same rule the home controller applied: the first four available pizzas.
    const featured = (data?.pizzas ?? []).filter((p) => p.available).slice(0, FEATURED_LIMIT);

    return (
        <>
            <header className="hero text-white text-center d-flex align-items-center">
                <div className="container py-5">
                    <h1 className="display-3 fw-bold mb-3">Hot, Fresh &amp; Delicious</h1>
                    <p className="lead mb-4">
                        Handcrafted pizzas baked to perfection and delivered to your door.
                    </p>
                    <Link to="/pizzas" className="btn btn-warning btn-lg px-4 fw-semibold">
                        <i className="bi bi-list-ul" /> Browse Our Menu
                    </Link>
                </div>
            </header>

            <section className="container my-5">
                <div className="text-center mb-4">
                    <h2 className="fw-bold">Featured Pizzas</h2>
                    <p className="text-muted">A taste of what&apos;s on the menu</p>
                </div>

                {featured.length === 0 ? (
                    <div className="text-center text-muted py-4">
                        <i className="bi bi-emoji-frown fs-1" />
                        <p className="mt-2">No pizzas available yet. Please check back soon!</p>
                    </div>
                ) : (
                    <div className="row g-4">
                        {featured.map((pizza) => (
                            <div className="col-12 col-sm-6 col-lg-3" key={pizza.id}>
                                <div className="card h-100 pizza-card shadow-sm">
                                    <img
                                        src={pizza.imageUrl}
                                        className="card-img-top pizza-img"
                                        alt={pizza.name}
                                        onError={onImageError}
                                    />
                                    <div className="card-body d-flex flex-column">
                                        <h5 className="card-title fw-semibold">{pizza.name}</h5>
                                        <p className="card-text small text-muted flex-grow-1">
                                            {abbreviate(pizza.description, 70)}
                                        </p>
                                        <div className="text-start">
                                            <span className="fw-bold text-danger">{money(pizza.price)}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                <div className="text-center mt-4">
                    <Link to="/pizzas" className="btn btn-outline-danger">
                        View Full Menu <i className="bi bi-arrow-right" />
                    </Link>
                </div>
            </section>
        </>
    );
}
