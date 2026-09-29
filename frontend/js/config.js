const API_BASE_URL = "https://rmshop.onrender.com/api";

// Attaches the logged-in user's id so manager-only endpoints know who's asking.
function authHeaders(extra = {}) {
    const stored = localStorage.getItem("rmshop_user");
    const user = stored ? JSON.parse(stored) : null;
    return {
        ...extra,
        ...(user ? { "X-User-Id": String(user.id) } : {})
    };
}

// Pre-warm the backend on page load, then ping every 4 minutes while a tab is
// open so Render's free tier doesn't put the service to sleep mid-shift.
function pingBackend() {
    try {
        fetch(`${API_BASE_URL}/products`, { method: "GET", cache: "no-store", mode: "cors" })
            .catch(() => {});
    } catch (_) {}
}
pingBackend();
setInterval(pingBackend, 4 * 60 * 1000);
