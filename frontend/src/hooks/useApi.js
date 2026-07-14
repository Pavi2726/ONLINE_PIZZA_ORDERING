import { useCallback, useEffect, useRef, useState } from 'react';

/** Fetch-on-mount with a manual reload. The only data-fetching machinery this app needs. */
export function useApi(fetcher, deps = []) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const alive = useRef(true);
    const requestId = useRef(0);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
        };
    }, []);

    const load = useCallback(async () => {
        // Tag this call so a slower, older request can't clobber a newer one's result
        // if responses resolve out of order (e.g. two filter changes in quick succession).
        const id = ++requestId.current;
        setLoading(true);
        try {
            const result = await fetcher();
            if (alive.current && id === requestId.current) {
                setData(result);
                setError(null);
            }
        } catch (err) {
            if (alive.current && id === requestId.current) setError(err);
        } finally {
            if (alive.current && id === requestId.current) setLoading(false);
        }
        // The caller controls invalidation through deps, as with any effect.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);

    useEffect(() => {
        load();
    }, [load]);

    return { data, setData, loading, error, reload: load };
}

/**
 * Runs one mutation at a time and reports whether it is in flight. Replaces the
 * double-submit guard app.js implemented by disabling the button on submit.
 */
export function useSubmit() {
    const [pending, setPending] = useState(false);

    const run = useCallback(async (action) => {
        setPending(true);
        try {
            return await action();
        } finally {
            setPending(false);
        }
    }, []);

    return [pending, run];
}
