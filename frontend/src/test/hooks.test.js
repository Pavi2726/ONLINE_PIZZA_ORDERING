import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';
import { usePagination } from '../hooks/usePagination';
import { useTheme } from '../hooks/useTheme';

describe('usePagination', () => {
    const items = Array.from({ length: 20 }, (_, i) => i + 1);

    it('pages by eight, matching the old app.js and admin-table.js', () => {
        const { result } = renderHook(() => usePagination(items));

        expect(result.current.pageCount).toBe(3);
        expect(result.current.pageItems).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);

        act(() => result.current.setPage(3));
        expect(result.current.pageItems).toEqual([17, 18, 19, 20]);
    });

    it('reports a single page for an empty list rather than zero', () => {
        const { result } = renderHook(() => usePagination([]));

        expect(result.current.pageCount).toBe(1);
        expect(result.current.pageItems).toEqual([]);
    });

    // A filter or a delete can shrink the list out from under the current page.
    it('pulls the view back when the list shrinks past the current page', () => {
        const { result, rerender } = renderHook(({ list }) => usePagination(list), {
            initialProps: { list: items },
        });

        act(() => result.current.setPage(3));
        expect(result.current.page).toBe(3);

        rerender({ list: items.slice(0, 5) });
        expect(result.current.page).toBe(1);
        expect(result.current.pageItems).toEqual([1, 2, 3, 4, 5]);
    });
});

describe('useTheme', () => {
    beforeEach(() => {
        localStorage.clear();
        document.documentElement.removeAttribute('data-bs-theme');
    });

    it('writes the same localStorage key the server-rendered app used', () => {
        const { result } = renderHook(() => useTheme());

        act(() => result.current.toggleTheme());

        expect(result.current.theme).toBe('dark');
        expect(localStorage.getItem('pizza-theme')).toBe('dark');
        expect(document.documentElement.getAttribute('data-bs-theme')).toBe('dark');
    });

    it('seeds from the attribute the pre-paint script already set', () => {
        document.documentElement.setAttribute('data-bs-theme', 'dark');

        const { result } = renderHook(() => useTheme());

        expect(result.current.theme).toBe('dark');
    });
});
