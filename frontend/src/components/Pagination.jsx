/** Single page is not worth a control, exactly as the old scripts behaved. */
export default function Pagination({ page, pageCount, onChange }) {
    if (pageCount <= 1) return null;

    const pages = Array.from({ length: pageCount }, (_, i) => i + 1);

    return (
        <nav aria-label="Pagination">
            <ul className="pagination justify-content-center mt-4">
                <li className={`page-item ${page === 1 ? 'disabled' : ''}`}>
                    <button type="button" className="page-link" onClick={() => onChange(page - 1)}>
                        &laquo;
                    </button>
                </li>
                {pages.map((n) => (
                    <li key={n} className={`page-item ${n === page ? 'active' : ''}`}>
                        <button type="button" className="page-link" onClick={() => onChange(n)}>
                            {n}
                        </button>
                    </li>
                ))}
                <li className={`page-item ${page === pageCount ? 'disabled' : ''}`}>
                    <button type="button" className="page-link" onClick={() => onChange(page + 1)}>
                        &raquo;
                    </button>
                </li>
            </ul>
        </nav>
    );
}
