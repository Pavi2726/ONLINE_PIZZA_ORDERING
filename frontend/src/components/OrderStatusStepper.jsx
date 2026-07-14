const STEPS = ['Placed', 'Processing', 'Out for Delivery', 'Delivered'];

/**
 * Port of the order-status Thymeleaf fragment. stepIndex and estimatedWindow are
 * computed on the server (from the OrderStatus enum), so the progression rules are
 * not restated here.
 */
export default function OrderStatusStepper({ status, stepIndex, estimatedWindow }) {
    if (status === 'CANCELLED') {
        return (
            <div className="alert alert-danger mb-0 py-2">
                <i className="bi bi-x-circle-fill" /> This order was cancelled.
            </div>
        );
    }

    return (
        <div>
            <div className="d-flex justify-content-between text-center">
                {STEPS.map((label, index) => (
                    <div className="flex-fill" key={label}>
                        <i
                            className={
                                stepIndex >= index
                                    ? 'bi bi-check-circle-fill text-success'
                                    : 'bi bi-circle text-muted'
                            }
                            style={{ fontSize: '1.5rem' }}
                        />
                        <div className="small">{label}</div>
                    </div>
                ))}
            </div>
            {estimatedWindow && (
                <p className="text-center text-muted small mt-2 mb-0">
                    Estimated: <span>{estimatedWindow}</span>
                </p>
            )}
        </div>
    );
}
