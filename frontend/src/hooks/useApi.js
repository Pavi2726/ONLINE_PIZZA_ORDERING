import { useCallback, useEffect, useRef, useState } from 'react';

/** Fetch-on-mount with a manual reload. The only data-fetching machinery this app needs. */
export function useApi(fetcher, deps = []) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const alive = useRef(true);

    useEffect(() => {
        alive.current = true;
        return () => {
            alive.current = false;
        };
    }, []);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const result = await fetcher();
            if (alive.current) {
                setData(result);
                setError(null);
            }
        } catch (err) {
            if (alive.current) setError(err);
        } finally {
            if (alive.current) setLoading(false);
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
