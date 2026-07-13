/**
 * A submit/action button that disables itself while its action is in flight — the
 * double-submit guard app.js applied globally, made explicit per button.
 */
export default function SubmitButton({
    pending = false,
    className = 'btn btn-danger',
    type = 'submit',
    children,
    ...rest
}) {
    return (
        <button type={type} className={className} disabled={pending || rest.disabled} {...rest}>
            {pending && (
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
            )}
            {children}
        </button>
    );
}
