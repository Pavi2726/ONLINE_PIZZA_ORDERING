const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * Rupee amount with two decimals. Jackson serializes BigDecimal 12.50 as 12.5,
 * so prices must never be rendered straight off the wire.
 */
export function money(value) {
    const n = Number(value);
    return '₹' + (Number.isFinite(n) ? n.toFixed(2) : '0.00');
}

const pad = (n) => String(n).padStart(2, '0');

/**
 * `dd-MMM-yyyy HH:mm` (or `dd MMM yyyy HH:mm` with sep=' '), matching the two
 * #temporals.format patterns the templates used. LocalDateTime arrives without
 * a zone, which Date parses as local time — the intent.
 */
export function dateTime(value, sep = '-') {
    if (!value) return '';
    const d = new Date(value);
    if (Number.isNaN(d.getTime())) return '';
    const date = [pad(d.getDate()), MONTHS[d.getMonth()], d.getFullYear()].join(sep);
    return `${date} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** Thymeleaf's #strings.abbreviate: the ellipsis fits *within* maxLength. */
export function abbreviate(text, maxLength) {
    if (!text || text.length <= maxLength) return text || '';
    return text.slice(0, maxLength - 3) + '...';
}

/** Inline data-URI placeholder; the old via.placeholder.com fallback is unreliable. */
export const PIZZA_PLACEHOLDER =
    "data:image/svg+xml;charset=UTF-8," +
    encodeURIComponent(
        `<svg xmlns="http://www.w3.org/2000/svg" width="400" height="250">
            <rect width="400" height="250" fill="#e9ecef"/>
            <text x="50%" y="50%" font-family="Segoe UI, sans-serif" font-size="22"
                  fill="#adb5bd" text-anchor="middle" dominant-baseline="middle">Pizza</text>
        </svg>`
    );

/** Null the handler first, or a failing placeholder would loop forever. */
export function onImageError(event) {
    event.currentTarget.onerror = null;
    event.currentTarget.src = PIZZA_PLACEHOLDER;
}
