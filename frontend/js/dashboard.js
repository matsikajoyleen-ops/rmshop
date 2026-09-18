function todayIso() {
    return new Date().toISOString().split("T")[0];
}

function formatMoney(amount) {
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function loadDashboard() {
    const today = todayIso();
    document.getElementById("welcome-name").textContent = currentUser.fullName;

    try {
        const [revenueRes, lowStockRes, topEmployeeRes] = await Promise.all([
            fetch(`${API_BASE_URL}/reports/revenue?start=${today}&end=${today}`),
            fetch(`${API_BASE_URL}/products/low-stock`),
            fetch(`${API_BASE_URL}/reports/top-employee?start=${today}&end=${today}`)
        ]);

        const revenue = await revenueRes.json();
        const lowStock = await lowStockRes.json();
        const topEmployee = topEmployeeRes.ok ? await topEmployeeRes.json() : null;

        document.getElementById("revenue-value").textContent = formatMoney(revenue.revenue);
        document.getElementById("lowstock-value").textContent = lowStock.length;
        document.getElementById("topemployee-value").textContent = topEmployee ? topEmployee.employeeName : "No sales yet";
        document.getElementById("topemployee-sub").textContent = topEmployee ? `${topEmployee.itemsSold} items sold` : "";
    } catch (error) {
        console.error("Failed to load dashboard data", error);
    }
}

document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    logout();
});

loadDashboard();