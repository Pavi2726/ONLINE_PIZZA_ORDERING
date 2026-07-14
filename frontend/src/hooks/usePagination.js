import { useEffect, useMemo, useState } from 'react';

export const PAGE_SIZE = 8;

/**
 * Client-side pagination, replacing the two hand-rolled implementations in app.js
 * (customer pizza grid) and admin-table.js (admin pizza table) — both paged by 8.
 */
export function usePagination(items, pageSize = PAGE_SIZE) {
    const [page, setPage] = useState(1);
    const list = items ?? [];
    const pageCount = Math.max(1, Math.ceil(list.length / pageSize));

    // A new list (a filter changing, a reload after a delete) means whatever page the
    // view was on no longer corresponds to anything the visitor chose — start over.
    useEffect(() => {
        setPage(1);
    }, [items]);

    // Belt-and-suspenders: also clamp if the page count alone shrinks past the current page.
    useEffect(() => {
        setPage((current) => Math.min(current, pageCount));
    }, [pageCount]);

    const pageItems = useMemo(
        () => list.slice((page - 1) * pageSize, page * pageSize),
        [list, page, pageSize]
    );

    return { pageItems, page, pageCount, setPage };
}
