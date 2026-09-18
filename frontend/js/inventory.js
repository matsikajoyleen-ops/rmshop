document.getElementById("welcome-name").textContent = currentUser.fullName;
document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    logout();
});

const tableBody = document.getElementById("products-body");
const emptyState = document.getElementById("empty-state");

function formatMoney(amount) {
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function statusBadge(level) {
    const labels = { SUFFICIENT: "In stock", LOW: "Low stock", CRITICAL: "Critical" };
    const classes = { SUFFICIENT: "badge-ok", LOW: "badge-low", CRITICAL: "badge-critical" };
    return `<span class="badge ${classes[level]}">${labels[level]}</span>`;
}

async function loadProducts() {
    const response = await fetch(`${API_BASE_URL}/products`);
    const products = await response.json();

    tableBody.innerHTML = "";
    emptyState.hidden = products.length > 0;

    products.forEach(product => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${product.name}</td>
            <td>${product.quantity}</td>
            <td>${formatMoney(product.price)}</td>
            <td>${statusBadge(product.stockLevel)}</td>
            <td class="actions-cell">
                <button class="btn-link" data-restock="${product.id}" data-name="${product.name}">Restock</button>
                <button class="btn-link" data-thresholds="${product.id}" data-name="${product.name}" data-low="${product.lowStockThreshold}" data-critical="${product.criticalStockThreshold}">Thresholds</button>
            </td>
        `;
        tableBody.appendChild(row);
    });
}

function openModal(id) { document.getElementById(id).hidden = false; }
function closeModal(id) { document.getElementById(id).hidden = true; }

document.querySelectorAll("[data-close-modal]").forEach(btn => {
    btn.addEventListener("click", () => { btn.closest(".modal-backdrop").hidden = true; });
});

document.getElementById("add-product-btn").addEventListener("click", () => openModal("add-product-modal"));

document.getElementById("add-product-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("add-product-error");
    errorEl.hidden = true;

    const name = document.getElementById("new-product-name").value.trim();
    const quantity = Number(document.getElementById("new-product-quantity").value);
    const price = Number(document.getElementById("new-product-price").value);

    if (!name) {
        errorEl.textContent = "Enter a product name.";
        errorEl.hidden = false;
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/products`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, quantity, price })
        });
        if (!response.ok) throw new Error();

        closeModal("add-product-modal");
        e.target.reset();
        loadProducts();
    } catch {
        errorEl.textContent = "Could not add the product. Try again.";
        errorEl.hidden = false;
    }
});

tableBody.addEventListener("click", (e) => {
    const restockBtn = e.target.closest("[data-restock]");
    if (restockBtn) {
        document.getElementById("restock-product-id").value = restockBtn.dataset.restock;
        document.getElementById("restock-product-name").textContent = restockBtn.dataset.name;
        openModal("restock-modal");
        return;
    }

    const thresholdsBtn = e.target.closest("[data-thresholds]");
    if (thresholdsBtn) {
        document.getElementById("thresholds-product-id").value = thresholdsBtn.dataset.thresholds;
        document.getElementById("thresholds-product-name").textContent = thresholdsBtn.dataset.name;
        document.getElementById("thresholds-low").value = thresholdsBtn.dataset.low;
        document.getElementById("thresholds-critical").value = thresholdsBtn.dataset.critical;
        openModal("thresholds-modal");
    }
});

document.getElementById("restock-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("restock-error");
    errorEl.hidden = true;

    const id = document.getElementById("restock-product-id").value;
    const quantityAdded = Number(document.getElementById("restock-quantity").value);
    const cost = Number(document.getElementById("restock-cost").value);

    try {
        const response = await fetch(`${API_BASE_URL}/products/${id}/restock`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ quantityAdded, cost })
        });
        if (!response.ok) throw new Error();

        closeModal("restock-modal");
        e.target.reset();
        loadProducts();
    } catch {
        errorEl.textContent = "Could not record the restock. Try again.";
        errorEl.hidden = false;
    }
});

document.getElementById("thresholds-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("thresholds-error");
    errorEl.hidden = true;

    const id = document.getElementById("thresholds-product-id").value;
    const lowStockThreshold = Number(document.getElementById("thresholds-low").value);
    const criticalStockThreshold = Number(document.getElementById("thresholds-critical").value);

    try {
        const response = await fetch(`${API_BASE_URL}/products/${id}/thresholds`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ lowStockThreshold, criticalStockThreshold })
        });
        if (!response.ok) throw new Error();

        closeModal("thresholds-modal");
        loadProducts();
    } catch {
        errorEl.textContent = "Could not update thresholds. Try again.";
        errorEl.hidden = false;
    }
});

loadProducts();