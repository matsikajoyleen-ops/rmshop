const form = document.getElementById("login-form");
const digitInputs = Array.from(document.querySelectorAll(".code-digit"));
const errorMessage = document.getElementById("error-message");
const loginBtn = document.getElementById("login-btn");
const btnText = loginBtn.querySelector(".btn-text");
const btnSpinner = loginBtn.querySelector(".btn-spinner");

digitInputs[0].focus();

digitInputs.forEach((input, index) => {
    input.addEventListener("input", () => {
        input.value = input.value.replace(/[^0-9]/g, "").slice(0, 1);
        if (input.value && index < digitInputs.length - 1) {
            digitInputs[index + 1].focus();
        }
        clearError();
    });

    input.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !input.value && index > 0) {
            digitInputs[index - 1].focus();
        }
    });

    input.addEventListener("paste", (e) => {
        e.preventDefault();
        const pasted = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
        if (!pasted) return;
        digitInputs.forEach((box, i) => { box.value = pasted[i] || ""; });
        const nextEmpty = digitInputs.findIndex(box => !box.value);
        (nextEmpty === -1 ? digitInputs[digitInputs.length - 1] : digitInputs[nextEmpty]).focus();
    });
});

function clearError() {
    errorMessage.hidden = true;
    errorMessage.textContent = "";
    digitInputs.forEach(input => input.classList.remove("input-error"));
}

function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
    digitInputs.forEach(input => input.classList.add("input-error"));
}

function setLoading(isLoading) {
    loginBtn.disabled = isLoading;
    btnText.hidden = isLoading;
    btnSpinner.hidden = !isLoading;
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const accessCode = digitInputs.map(input => input.value).join("");
    if (accessCode.length !== 6) {
        showError("Enter all 6 digits of your access code.");
        return;
    }

    clearError();
    setLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/users/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ accessCode })
        });

        if (response.status === 401) {
            showError("That access code doesn't match an active account.");
            digitInputs.forEach(input => input.value = "");
            digitInputs[0].focus();
            return;
        }

        if (!response.ok) {
            showError("Something went wrong. Try again in a moment.");
            return;
        }

        const user = await response.json();
        localStorage.setItem("rmshop_user", JSON.stringify(user));
        window.location.href = user.role === "MANAGER" ? "manager-dashboard.html" : "sales.html";

    } catch (error) {
        showError("Could not reach the server. Check your connection.");
    } finally {
        setLoading(false);
    }
});