function todayIso() {
    return new Date().toISOString().split("T")[0];
}

function formatMoney(amount) {
    return Number(amount).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function updateClock() {
    const clockEl = document.getElementById("digital-clock");
    if (!clockEl) return;
    const now = new Date();
    clockEl.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
}

function setGreeting() {
    const greetingEl = document.getElementById("dashboard-greeting");
    if (!greetingEl) return;

    const hour = new Date().getHours();
    let timeGreeting = "Good day";
    if (hour < 12) timeGreeting = "Good morning";
    else if (hour < 18) timeGreeting = "Good afternoon";
    else timeGreeting = "Good evening";

    const name = (currentUser && currentUser.fullName) ? currentUser.fullName.split(" ")[0] : "Manager";
    greetingEl.textContent = `${timeGreeting}, ${name} 👋`;
}

function setAvatar() {
    const avatarEl = document.getElementById("user-avatar");
    if (!avatarEl || !currentUser || !currentUser.fullName) return;
    const parts = currentUser.fullName.trim().split(" ");
    if (parts.length >= 2) {
        avatarEl.textContent = (parts[0][0] + parts[1][0]).toUpperCase();
    } else if (parts.length === 1 && parts[0].length > 0) {
        avatarEl.textContent = parts[0][0].toUpperCase();
    }
}

// Instant Cache Retrieval (Stale-While-Revalidate pattern)
function applyCachedData() {
    try {
        const cached = localStorage.getItem("rmshop_dashboard_cache");
        if (cached) {
            const data = JSON.parse(cached);
            if (data.revenue !== undefined) document.getElementById("revenue-value").textContent = "$" + formatMoney(data.revenue);
            if (data.lowStock !== undefined) {
                document.getElementById("lowstock-value").textContent = data.lowStock;
                const pill = document.getElementById("lowstock-pill");
                if (pill) {
                    pill.textContent = data.lowStock > 0 ? `${data.lowStock} items low` : "All levels healthy";
                    pill.className = data.lowStock > 0 ? "trend-pill trend-alert" : "trend-pill trend-up";
                }
            }
            if (data.topEmployeeName) {
                document.getElementById("topemployee-value").textContent = data.topEmployeeName;
                document.getElementById("topemployee-sub").textContent = `${data.topEmployeeSold || 0} items sold today`;
            }
        }
    } catch (_) {}
}

async function loadDashboard() {
    const today = todayIso();
    
    if (currentUser && currentUser.fullName) {
        document.getElementById("welcome-name").textContent = currentUser.fullName;
    }
    setGreeting();
    setAvatar();

    // Render cache immediately for 0ms visual delay
    applyCachedData();

    try {
        const [revenueRes, lowStockRes, topEmployeeRes] = await Promise.all([
            fetch(`${API_BASE_URL}/reports/revenue?start=${today}&end=${today}`, { headers: authHeaders() }).catch(() => null),
            fetch(`${API_BASE_URL}/products/low-stock`, { headers: authHeaders() }).catch(() => null),
            fetch(`${API_BASE_URL}/reports/top-employee?start=${today}&end=${today}`, { headers: authHeaders() }).catch(() => null)
        ]);

        let cachePayload = {};

        if (revenueRes && revenueRes.ok) {
            const revenue = await revenueRes.json();
            const val = revenue.revenue || 0;
            document.getElementById("revenue-value").textContent = "$" + formatMoney(val);
            cachePayload.revenue = val;
        }

        if (lowStockRes && lowStockRes.ok) {
            const lowStock = await lowStockRes.json();
            const count = Array.isArray(lowStock) ? lowStock.length : 0;
            document.getElementById("lowstock-value").textContent = count;
            const pill = document.getElementById("lowstock-pill");
            if (pill) {
                pill.textContent = count > 0 ? `${count} items low` : "All levels healthy";
                pill.className = count > 0 ? "trend-pill trend-alert" : "trend-pill trend-up";
            }
            cachePayload.lowStock = count;
        }

        if (topEmployeeRes && topEmployeeRes.ok) {
            const topEmployee = await topEmployeeRes.json();
            document.getElementById("topemployee-value").textContent = topEmployee ? topEmployee.employeeName : "No sales yet";
            document.getElementById("topemployee-sub").textContent = topEmployee ? `${topEmployee.itemsSold} items sold today` : "Waiting for first sale";
            if (topEmployee) {
                cachePayload.topEmployeeName = topEmployee.employeeName;
                cachePayload.topEmployeeSold = topEmployee.itemsSold;
            }
        }

        // Save fresh cache
        if (Object.keys(cachePayload).length > 0) {
            localStorage.setItem("rmshop_dashboard_cache", JSON.stringify(cachePayload));
        }

    } catch (error) {
        console.error("Failed to load live dashboard data", error);
    }
}

const logoutLink = document.getElementById("logout-link");
if (logoutLink) {
    logoutLink.addEventListener("click", (e) => {
        e.preventDefault();
        logout();
    });
}

// Start clock timer
updateClock();
setInterval(updateClock, 1000);

loadDashboard();
