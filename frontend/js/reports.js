document.getElementById("welcome-name").textContent = currentUser.fullName;
document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    logout();
});

function formatMoney(amount) {
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
function todayIso() { return new Date().toISOString().split("T")[0]; }
function firstOfMonthIso() {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split("T")[0];
}

document.getElementById("range-start").value = firstOfMonthIso();
document.getElementById("range-end").value = todayIso();
document.getElementById("reconcile-date").value = todayIso();

async function loadRangeReports() {
    const start = document.getElementById("range-start").value;
    const end = document.getElementById("range-end").value;

    const [revenueRes, topEmpRes, projectionRes, mostWantedRes] = await Promise.all([
        fetch(`${API_BASE_URL}/reports/revenue?start=${start}&end=${end}`),
        fetch(`${API_BASE_URL}/reports/top-employee?start=${start}&end=${end}`),
        fetch(`${API_BASE_URL}/reports/projected-revenue?start=${start}&end=${end}&projectedDays=30`),
        fetch(`${API_BASE_URL}/reports/most-wanted?start=${start}&end=${end}`)
    ]);

    const revenue = await revenueRes.json();
    document.getElementById("revenue-value").textContent = formatMoney(revenue.revenue);
    document.getElementById("restock-cost-value").textContent = formatMoney(revenue.restockCost);
    document.getElementById("other-exp-value").textContent = formatMoney(revenue.otherExpenditures);
    document.getElementById("profit-value").textContent = formatMoney(revenue.profit);

    const topEmployee = topEmpRes.ok ? await topEmpRes.json() : null;
    document.getElementById("top-employee-value").textContent = topEmployee ? topEmployee.employeeName : "No sales yet";
    document.getElementById("top-employee-sub").textContent = topEmployee ? `${formatMoney(topEmployee.totalSales)} in sales, ${topEmployee.itemsSold} items` : "";

    const projection = await projectionRes.json();
    document.getElementById("projected-value").textContent = formatMoney(projection.projectedRevenue);
    document.getElementById("projected-sub").textContent = `Based on ${formatMoney(projection.averageDailyRevenue)}/day average`;

    const mostWanted = await mostWantedRes.json();
    document.getElementById("most-wanted-body").innerHTML = mostWanted.length
        ? mostWanted.map(p => `<tr><td>${p.productName}</td><td>${p.quantitySold}</td></tr>`).join("")
        : `<tr><td colspan="2">No sales in this range yet.</td></tr>`;
}

document.getElementById("apply-range-btn").addEventListener("click", loadRangeReports);

document.getElementById("reconcile-btn").addEventListener("click", async () => {
    const date = document.getElementById("reconcile-date").value;
    const actualCash = document.getElementById("reconcile-actual").value;
    if (!actualCash) return;

    const response = await fetch(`${API_BASE_URL}/reports/reconcile?date=${date}&actualCash=${actualCash}`);
    const result = await response.json();

    const discrepancy = Number(result.discrepancy);
    const status = discrepancy === 0 ? "Matches exactly" : discrepancy > 0 ? `${formatMoney(discrepancy)} over` : `${formatMoney(Math.abs(discrepancy))} short`;

    const resultEl = document.getElementById("reconcile-result");
    resultEl.innerHTML = `
        <div class="cart-row"><span>Expected cash</span><span>${formatMoney(result.expectedCash)}</span></div>
        <div class="cart-row"><span>Actual cash counted</span><span>${formatMoney(result.actualCash)}</span></div>
        <div class="cart-row reconcile-status"><span>Result</span><span>${status}</span></div>
    `;
    resultEl.hidden = false;
});

async function loadExpenditures() {
    const response = await fetch(`${API_BASE_URL}/expenditures`);
    const expenditures = await response.json();
    document.getElementById("expenditures-body").innerHTML = expenditures.length
        ? expenditures.map(e => `<tr><td>${e.description}</td><td>${formatMoney(e.amount)}</td><td>${e.expenseDate}</td></tr>`).join("")
        : `<tr><td colspan="3">No expenditures recorded yet.</td></tr>`;
}

document.getElementById("expenditure-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const description = document.getElementById("exp-description").value.trim();
    const amount = Number(document.getElementById("exp-amount").value);
    if (!description || !amount) return;

    await fetch(`${API_BASE_URL}/expenditures`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ description, amount })
    });

    e.target.reset();
    loadExpenditures();
    loadRangeReports();
});

function formatDateTime(iso) {
    return iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : "—";
}

async function loadReceipts() {
    const response = await fetch(`${API_BASE_URL}/receipts`);
    const receipts = await response.json();
    document.getElementById("receipts-body").innerHTML = receipts.length
        ? receipts.map(r => `<tr><td>${r.receiptNo}</td><td>${formatDateTime(r.savedAt)}</td><td>${formatDateTime(r.printedAt)}</td></tr>`).join("")
        : `<tr><td colspan="3">No receipts yet.</td></tr>`;
}

loadRangeReports();
loadExpenditures();
loadReceipts();