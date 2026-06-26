// Lightweight client-side pagination for the admin pizza table.
(function () {
    "use strict";

    const PAGE_SIZE = 8;
    const table = document.getElementById("pizzaTable");
    const pagination = document.getElementById("pagination");
    if (!table || !pagination) {
        return;
    }

    const rows = Array.prototype.slice.call(table.querySelectorAll("tbody .pizza-row"));
    const pageCount = Math.ceil(rows.length / PAGE_SIZE);

    if (pageCount <= 1) {
        return; // No pagination needed.
    }

    let currentPage = 1;

    function renderPage(page) {
        currentPage = page;
        rows.forEach(function (row, index) {
            const start = (page - 1) * PAGE_SIZE;
            const end = start + PAGE_SIZE;
            row.style.display = index >= start && index < end ? "" : "none";
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
        pagination.innerHTML = "";
        pagination.appendChild(makeItem("\u00AB", currentPage - 1, currentPage === 1, false));
        for (let p = 1; p <= pageCount; p++) {
            pagination.appendChild(makeItem(String(p), p, false, p === currentPage));
        }
        pagination.appendChild(makeItem("\u00BB", currentPage + 1, currentPage === pageCount, false));
    }

    renderPage(1);
})();
