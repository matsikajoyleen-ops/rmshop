document.getElementById("welcome-name").textContent = currentUser.fullName;
document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    logout();
});

let products = [];
let cart = [];
let lastReceipt = null;

function formatMoney(amount) {
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadProducts() {
    const response = await fetch(`${API_BASE_URL}/products`);
    products = await response.json();
    renderProducts(products);
}

function renderProducts(list) {
    const grid = document.getElementById("product-grid");
    grid.innerHTML = "";
    list.forEach(product => {
        const card = document.createElement("button");
        card.className = "product-card";
        card.disabled = product.quantity <= 0;
        card.innerHTML = `
            <span class="product-card-name">${product.name}</span>
            <span class="product-card-price">${formatMoney(product.price)}</span>
            <span class="product-card-stock">${product.quantity} in stock</span>
        `;
        card.addEventListener("click", () => addToCart(product));
        grid.appendChild(card);
    });
}

document.getElementById("product-search").addEventListener("input", (e) => {
    const term = e.target.value.toLowerCase();
    renderProducts(products.filter(p => p.name.toLowerCase().includes(term)));
});

function addToCart(product) {
    const existing = cart.find(item => item.productId === product.id);
    if (existing) {
        if (existing.quantity < product.quantity) existing.quantity += 1;
    } else {
        cart.push({ productId: product.id, name: product.name, price: product.price, quantity: 1, availableStock: product.quantity });
    }
    renderCart();
}

function changeQuantity(productId, delta) {
    const item = cart.find(i => i.productId === productId);
    if (!item) return;
    item.quantity += delta;
    if (item.quantity <= 0 || item.quantity > item.availableStock) {
        cart = cart.filter(i => i.productId !== productId);
    }
    renderCart();
}

function renderCart() {
    const container = document.getElementById("cart-items");
    const emptyState = document.getElementById("cart-empty");
    const completeBtn = document.getElementById("complete-sale-btn");

    container.innerHTML = "";
    emptyState.hidden = cart.length > 0;
    completeBtn.disabled = cart.length === 0;

    let total = 0;
    cart.forEach(item => {
        total += item.price * item.quantity;
        const row = document.createElement("div");
        row.className = "cart-row";
        row.innerHTML = `
            <span class="cart-row-name">${item.name}</span>
            <div class="cart-row-qty">
                <button type="button" data-decrease="${item.productId}">&minus;</button>
                <span>${item.quantity}</span>
                <button type="button" data-increase="${item.productId}">+</button>
            </div>
            <span class="cart-row-subtotal">${formatMoney(item.price * item.quantity)}</span>
        `;
        container.appendChild(row);
    });

    document.getElementById("cart-total-value").textContent = formatMoney(total);
}

document.getElementById("cart-items").addEventListener("click", (e) => {
    const increaseId = e.target.dataset.increase;
    const decreaseId = e.target.dataset.decrease;
    if (increaseId) changeQuantity(Number(increaseId), 1);
    if (decreaseId) changeQuantity(Number(decreaseId), -1);
});

document.getElementById("complete-sale-btn").addEventListener("click", async () => {
    const lines = cart.map(item => ({ productId: item.productId, quantity: item.quantity }));

    try {
        const saleResponse = await fetch(`${API_BASE_URL}/sales`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ employeeId: currentUser.id, lines })
        });
        if (!saleResponse.ok) throw new Error("Sale failed");
        const sale = await saleResponse.json();

        const receiptResponse = await fetch(`${API_BASE_URL}/receipts`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ saleId: sale.id })
        });
        if (!receiptResponse.ok) throw new Error("Receipt save failed");
        lastReceipt = await receiptResponse.json();

        showReceipt(sale);
        cart = [];
        renderCart();
        loadProducts();

    } catch (error) {
        alert("Could not complete the sale. It may be low on stock, or the connection dropped. Try again.");
    }
});

function showReceipt(sale) {
    document.getElementById("receipt-no").textContent = `Receipt ${lastReceipt.receiptNo}`;
    const itemsEl = document.getElementById("receipt-items");
    itemsEl.innerHTML = "";
    sale.items.forEach(item => {
        const row = document.createElement("div");
        row.className = "cart-row";
        row.innerHTML = `
            <span class="cart-row-name">${item.productName} &times; ${item.quantity}</span>
            <span class="cart-row-subtotal">${formatMoney(item.subtotal)}</span>
        `;
        itemsEl.appendChild(row);
    });
    document.getElementById("receipt-total-value").textContent = formatMoney(sale.totalAmount);
    document.getElementById("receipt-modal").hidden = false;
}

document.getElementById("print-receipt-btn").addEventListener("click", async () => {
    if (!lastReceipt) return;
    await fetch(`${API_BASE_URL}/receipts/${lastReceipt.id}/print`, { method: "PUT" });
    window.print();
});

document.getElementById("new-sale-btn").addEventListener("click", () => {
    document.getElementById("receipt-modal").hidden = true;
});

loadProducts();