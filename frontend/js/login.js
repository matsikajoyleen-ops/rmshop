const form = document.getElementById("login-form");
const formContainer = document.getElementById("form-container");
const digitInputs = Array.from(document.querySelectorAll(".code-digit"));
const errorMessage = document.getElementById("error-message");
const loginBtn = document.getElementById("login-btn");
const btnText = loginBtn.querySelector(".btn-text");
const btnSpinner = loginBtn.querySelector(".btn-spinner");
const numpad = document.getElementById("pos-numpad");

// Focus first input box
if (digitInputs[0]) digitInputs[0].focus();

// Background pre-warming: Wake up Render free-tier instance silently on page load
// This drastically cuts down perceived cold-start delay before the user even finishes typing!
function prewarmBackend() {
    try {
        fetch(`${API_BASE_URL}/products`, { method: "GET", cache: "no-store" })
            .catch(() => {}); // Silent catch; goal is just to trigger the server spin-up
    } catch (_) {}
}
prewarmBackend();

// Input listeners for hardware keyboard
digitInputs.forEach((input, index) => {
    input.addEventListener("input", () => {
        input.value = input.value.replace(/[^0-9]/g, "").slice(0, 1);
        if (input.value && index < digitInputs.length - 1) {
            digitInputs[index + 1].focus();
        }
        clearError();
        checkAutoSubmit();
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
        clearError();
        checkAutoSubmit();
    });
});

// Virtual On-Screen POS Numpad
if (numpad) {
    numpad.addEventListener("click", (e) => {
        const target = e.target.closest("button");
        if (!target) return;

        const digit = target.dataset.digit;
        const action = target.dataset.action;

        if (digit !== undefined) {
            const firstEmpty = digitInputs.find(input => !input.value);
            if (firstEmpty) {
                firstEmpty.value = digit;
                const nextIndex = digitInputs.indexOf(firstEmpty) + 1;
                if (nextIndex < digitInputs.length) {
                    digitInputs[nextIndex].focus();
                } else {
                    firstEmpty.focus();
                }
            }
            clearError();
            checkAutoSubmit();
        } else if (action === "backspace") {
            const filled = [...digitInputs].reverse().find(input => input.value);
            if (filled) {
                filled.value = "";
                filled.focus();
            }
            clearError();
        } else if (action === "clear") {
            digitInputs.forEach(input => input.value = "");
            digitInputs[0].focus();
            clearError();
        }
    });
}

function checkAutoSubmit() {
    const accessCode = digitInputs.map(input => input.value).join("");
    if (accessCode.length === 6) {
        setTimeout(() => {
            if (digitInputs.map(input => input.value).join("").length === 6 && !loginBtn.disabled) {
                form.requestSubmit();
            }
        }, 120);
    }
}

function clearError() {
    errorMessage.hidden = true;
    errorMessage.textContent = "";
    digitInputs.forEach(input => input.classList.remove("input-error"));
}

function showError(message) {
    errorMessage.textContent = message;
    errorMessage.hidden = false;
    digitInputs.forEach(input => input.classList.add("input-error"));

    if (formContainer) {
        formContainer.classList.remove("shake");
        void formContainer.offsetWidth; // Trigger reflow for animation restart
        formContainer.classList.add("shake");
    }
}

function setLoading(isLoading, statusText = "Verifying credentials...") {
    loginBtn.disabled = isLoading;
    btnText.hidden = isLoading;
    btnSpinner.hidden = !isLoading;

    let coldStartNotice = document.getElementById("cold-start-notice");
    if (isLoading) {
        if (!coldStartNotice) {
            coldStartNotice = document.createElement("p");
            coldStartNotice.id = "cold-start-notice";
            coldStartNotice.style.cssText = "font-size:0.82rem; color:var(--cyan-accent); text-align:center; margin-top:0.75rem; transition:opacity 0.3s;";
            loginBtn.parentNode.insertBefore(coldStartNotice, loginBtn.nextSibling);
        }
        coldStartNotice.textContent = statusText;
        coldStartNotice.hidden = false;
    } else if (coldStartNotice) {
        coldStartNotice.hidden = true;
    }
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();

    const accessCode = digitInputs.map(input => input.value).join("");
    if (accessCode.length !== 6) {
        showError("Please enter all 6 digits of your terminal code.");
        return;
    }

    clearError();
    setLoading(true, "Connecting to terminal...");

    // Timer to update status if backend takes > 2.5s due to Render free-tier cold start
    const coldStartTimer = setTimeout(() => {
        setLoading(true, "Waking up cloud server container (cold start in progress)...");
    }, 2500);

    try {
        const response = await fetch(`${API_BASE_URL}/users/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ accessCode })
        });

        clearTimeout(coldStartTimer);

        if (response.status === 401) {
            showError("Invalid access code. Please check with your store manager.");
            digitInputs.forEach(input => input.value = "");
            digitInputs[0].focus();
            return;
        }

        if (!response.ok) {
            showError("Server authentication error. Please try again shortly.");
            return;
        }

        const user = await response.json();
        localStorage.setItem("rmshop_user", JSON.stringify(user));
        window.location.href = user.role === "MANAGER" ? "manager-dashboard.html" : "sales.html";

    } catch (error) {
        clearTimeout(coldStartTimer);
        showError("Unable to reach store server. The server may still be waking up — please try again in a moment.");
    } finally {
        setLoading(false);
    }
});