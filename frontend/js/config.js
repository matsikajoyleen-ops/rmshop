const API_BASE_URL = "https://rmshop.onrender.com/api";
// TODO: swap for your real Railway URL once the backend is deployed

// 1. Instant background pre-warm on every page load
(function prewarmOnLoad() {
    try {
        fetch(`${API_BASE_URL}/products`, { method: "GET", cache: "no-store", mode: "cors" })
            .catch(() => {});
    } catch (_) {}
})();

// 2. Active tab keep-alive heartbeat: Pings Render every 4 minutes while any RMShop tab is open
// This completely stops Render from sleeping while you are using the app!
setInterval(() => {
    try {
        fetch(`${API_BASE_URL}/products`, { method: "GET", cache: "no-store", mode: "cors" })
            .catch(() => {});
    } catch (_) {}
}, 4 * 60 * 1000);