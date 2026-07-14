import { Link } from 'react-router-dom';
import { useSession } from '../context/SessionContext';

/** Client-side counterpart of the server's error.html. */
export default function NotFound() {
    const { admin } = useSession();

    return (
        <section className="container my-5">
            <div className="row justify-content-center text-center">
                <div className="col-12 col-md-8 col-lg-6">
                    <div className="card shadow-sm">
                        <div className="card-body p-5">
                            <i className="bi bi-exclamation-triangle-fill text-warning fs-1" />
                            <h1 className="fw-bold mt-3 mb-1">404</h1>
                            <p className="text-muted mb-4">
                                We couldn&apos;t find the page you were looking for.
                            </p>
                            {admin ? (
                                <Link to="/admin/dashboard" className="btn btn-danger">
                                    <i className="bi bi-speedometer2" /> Back to Dashboard
                                </Link>
                            ) : (
                                <Link to="/" className="btn btn-danger">
                                    <i className="bi bi-house-door" /> Back to Home
                                </Link>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
}
