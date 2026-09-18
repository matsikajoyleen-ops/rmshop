const rmshopUserJson = localStorage.getItem("rmshop_user");
if (!rmshopUserJson) {
    window.location.href = "index.html";
}
const currentUser = JSON.parse(rmshopUserJson);

function logout() {
    localStorage.removeItem("rmshop_user");
    window.location.href = "index.html";
}