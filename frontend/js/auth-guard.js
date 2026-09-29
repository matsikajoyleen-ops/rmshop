const rmshopUserJson = localStorage.getItem("rmshop_user");
if (!rmshopUserJson) {
    window.location.href = "index.html";
}
const currentUser = JSON.parse(rmshopUserJson);

function logout() {
    localStorage.removeItem("rmshop_user");
    window.location.href = "index.html";
}

// Call at the top of any manager-only page. Sends an employee straight
// back to the sales screen instead of letting the page load at all.
function requireManager() {
    if (!currentUser || currentUser.role !== "MANAGER") {
        window.location.href = "sales.html";
    }
}