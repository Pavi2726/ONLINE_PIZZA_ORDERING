// Shared client-side behaviour for the Pizza Ordering System.

(function () {
    "use strict";

    // ---- Live order total calculation (place-order page) ----
    const orderForm = document.getElementById("orderForm");
    if (orderForm) {
        const unitPrice = parseFloat(orderForm.dataset.unitPrice) || 0;
        const taxRate = parseFloat(orderForm.dataset.taxRate) || 0;
        const quantityInput = document.getElementById("quantity");

        const subtotalEl = document.getElementById("subtotal");
        const taxEl = document.getElementById("tax");
        const grandTotalEl = document.getElementById("grandTotal");

        const recalc = function () {
            let qty = parseInt(quantityInput.value, 10);
            if (isNaN(qty) || qty < 1) {
                qty = 0;
            }
            const subtotal = unitPrice * qty;
            const tax = subtotal * taxRate;
            const total = subtotal + tax;
            subtotalEl.textContent = subtotal.toFixed(2);
            taxEl.textContent = tax.toFixed(2);
            grandTotalEl.textContent = total.toFixed(2);
        };

        quantityInput.addEventListener("input", recalc);
        recalc();
    }

    // ---- Prevent duplicate form submissions ----
    document.querySelectorAll("form[method='post']").forEach(function (form) {
        form.addEventListener("submit", function (event) {
            // Deferred so this runs after every other submit listener on the
            // form (e.g. a page's own client-side validation, which may still
            // call preventDefault()) and after the browser has already
            // captured the submitter's name/value for the request. Disabling
            // the button synchronously here would (a) leave it disabled
            // forever if a later listener cancels the submit, since nothing
            // else re-enables it, and (b) for forms where several buttons
            // share the submit but each carries its own name/value (e.g.
            // admin order-status actions), drop that value from the POST
            // entirely by disabling it before the browser reads it.
            setTimeout(function () {
                if (event.defaultPrevented) {
                    return;
                }
                const submitBtn = form.querySelector("button[type='submit']");
                if (submitBtn && !submitBtn.disabled) {
                    submitBtn.disabled = true;
                    const spinner = submitBtn.querySelector(".spinner-border");
                    if (spinner) {
                        spinner.classList.remove("d-none");
                    }
                }
            }, 0);
        });
    });

    // ---- Client-side pagination for customer pizza cards ----
    const pizzaGrid = document.getElementById("pizzaGrid");
    const customerPagination = document.getElementById("customerPagination");
    if (pizzaGrid && customerPagination) {
        const PAGE_SIZE = 8;
        const cards = Array.prototype.slice.call(pizzaGrid.querySelectorAll(".pizza-card-col"));
        const pageCount = Math.ceil(cards.length / PAGE_SIZE);

        if (pageCount > 1) {
            let currentPage = 1;

            function renderPage(page) {
                currentPage = page;
                cards.forEach(function (card, index) {
                    const start = (page - 1) * PAGE_SIZE;
                    card.style.display = index >= start && index < start + PAGE_SIZE ? "" : "none";
                });
                renderControls();
            }

            function makeItem(label, page, disabled, active) {
                const li = document.createElement("li");
                li.className = "page-item" + (disabled ? " disabled" : "") + (active ? " active" : "");
                const a = document.createElement("a");
                a.className = "page-link";
                a.href = "#";
                a.textContent = label;
                a.addEventListener("click", function (e) {
                    e.preventDefault();
                    if (!disabled && page !== currentPage) {
                        renderPage(page);
                    }
                });
                li.appendChild(a);
                return li;
            }

            function renderControls() {
                customerPagination.innerHTML = "";
                customerPagination.appendChild(makeItem("\u00AB", currentPage - 1, currentPage === 1, false));
                for (let p = 1; p <= pageCount; p++) {
                    customerPagination.appendChild(makeItem(String(p), p, false, p === currentPage));
                }
                customerPagination.appendChild(
                    makeItem("\u00BB", currentPage + 1, currentPage === pageCount, false));
            }

            renderPage(1);
        }
    }

    // ---- Auto-dismiss alerts after a few seconds ----
    document.querySelectorAll(".alert-dismissible").forEach(function (alert) {
        setTimeout(function () {
            alert.classList.remove("show");
        }, 5000);
    });

    // ---- Bulk order-status select-all (admin order list) ----
    const selectAllOrders = document.getElementById("selectAllOrders");
    if (selectAllOrders) {
        selectAllOrders.addEventListener("change", function () {
            document.querySelectorAll('input[name="orderIds"]').forEach(function (cb) {
                cb.checked = selectAllOrders.checked;
            });
        });
    }

    // ---- Dark mode toggle ----
    const themeToggle = document.getElementById("themeToggle");
    if (themeToggle) {
        const themeIcon = themeToggle.querySelector("i");

        const updateIcon = function (theme) {
            if (theme === "dark") {
                themeIcon.classList.remove("bi-moon-stars");
                themeIcon.classList.add("bi-sun-fill");
            } else {
                themeIcon.classList.remove("bi-sun-fill");
                themeIcon.classList.add("bi-moon-stars");
            }
        };

        updateIcon(document.documentElement.getAttribute("data-bs-theme"));

        themeToggle.addEventListener("click", function () {
            const current = document.documentElement.getAttribute("data-bs-theme");
            const next = current === "dark" ? "light" : "dark";
            document.documentElement.setAttribute("data-bs-theme", next);
            localStorage.setItem("pizza-theme", next);
            updateIcon(next);
        });
    }
})();
