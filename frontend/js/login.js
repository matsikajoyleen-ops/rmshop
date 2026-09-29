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
    loginBtn.disabled = isLoading || terminalLocked;
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

// ---- Wrong-attempt handling ------------------------------------------------
// The server counts wrong codes per device and answers with a code such as
// WRONG_ACCESS_CODE (plus attempt / attemptsRemaining) or ACCOUNT_LOCKED
// (plus lockedSeconds). Each attempt gets its own popup.

const LOCK_STORAGE_KEY = "rmshop_login_locked_until";
let terminalLocked = false;

const ATTEMPT_POPUPS = {
    1: { icon: "🔢", title: "Incorrect access code" },
    2: { icon: "⚠️", title: "Second wrong attempt" },
    3: { icon: "⚠️", title: "Careful: 2 attempts left" },
    4: { icon: "🛑", title: "Last attempt before lockout" }
};

function setTerminalLocked(locked) {
    terminalLocked = locked;
    loginBtn.disabled = locked;
    digitInputs.forEach(input => { input.disabled = locked; });
    if (numpad) numpad.querySelectorAll("button").forEach(btn => { btn.disabled = locked; });
}

function resetDigits() {
    digitInputs.forEach(input => input.value = "");
    if (!terminalLocked) digitInputs[0].focus();
}

function lockTerminal(seconds, message) {
    try { localStorage.setItem(LOCK_STORAGE_KEY, String(Date.now() + seconds * 1000)); } catch (_) {}
    setTerminalLocked(true);
    resetDigits();
    showError("Terminal locked after too many wrong codes.");
    showPopup({
        icon: "🔒",
        title: "Terminal locked",
        message,
        severity: "danger",
        attempt: 5,
        maxAttempts: 5,
        countdownSeconds: seconds,
        onCountdownEnd: unlockTerminal
    });
}

function unlockTerminal() {
    try { localStorage.removeItem(LOCK_STORAGE_KEY); } catch (_) {}
    setTerminalLocked(false);
    clearError();
    resetDigits();
    showPopup({
        icon: "🔓",
        title: "Terminal unlocked",
        message: "You can try your access code again. If you've forgotten it, managers can reset it by email.",
        severity: "success",
        actions: [{ label: "OK", primary: true, onClick: () => digitInputs[0].focus() }]
    });
}

// Keep the lock across page refreshes (the server enforces it regardless)
(function restoreLock() {
    let until = 0;
    try { until = Number(localStorage.getItem(LOCK_STORAGE_KEY)) || 0; } catch (_) {}
    const seconds = Math.round((until - Date.now()) / 1000);
    if (seconds > 0) {
        lockTerminal(seconds, "Too many wrong access codes were entered on this terminal. Please wait for the timer to finish.");
    } else if (until) {
        try { localStorage.removeItem(LOCK_STORAGE_KEY); } catch (_) {}
    }
})();

function goToForgot() {
    window.location.href = "forgot-password.html";
}

function handleLoginError(status, data) {
    const message = data.message || "Something went wrong. Please try again.";

    switch (data.code) {
        case "WRONG_ACCESS_CODE": {
            const attempt = data.attempt || 1;
            const look = ATTEMPT_POPUPS[attempt] || ATTEMPT_POPUPS[4];
            showError(`Wrong code: ${data.attemptsRemaining} attempt${data.attemptsRemaining === 1 ? "" : "s"} left.`);
            resetDigits();
            const actions = [{ label: "Try again", primary: true, onClick: () => digitInputs[0].focus() }];
            if (attempt >= 2) actions.push({ label: "Forgot access code?", onClick: goToForgot });
            showPopup({ ...look, message, severity: data.severity, attempt, maxAttempts: data.maxAttempts, actions });
            return;
        }
        case "ACCOUNT_LOCKED":
            lockTerminal(data.lockedSeconds || 300, message);
            return;
        case "ACCOUNT_DEACTIVATED":
            showError("This account is deactivated.");
            resetDigits();
            showPopup({ icon: "🚫", title: "Account deactivated", message, severity: "danger",
                actions: [{ label: "OK", primary: true }] });
            return;
        case "CODE_REQUIRED":
        case "CODE_HAS_SPACES":
        case "CODE_NOT_NUMERIC":
        case "CODE_TOO_SHORT":
        case "CODE_TOO_LONG":
            // Typing mistakes: shown inline, they don't count as attempts
            showError(message);
            return;
    }

    if (status >= 500) {
        showError("The server had a problem.");
        showPopup({ icon: "🛠️", title: "Server error", severity: "warning",
            message: "The store server ran into a problem while checking your code. Please try again in a moment." });
    } else {
        showError(message);
    }
}

form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (terminalLocked) return;

    const accessCode = digitInputs.map(input => input.value).join("");
    if (accessCode.length !== 6) {
        const missing = 6 - accessCode.length;
        showError(accessCode.length === 0
            ? "Enter your 6-digit access code."
            : `You've entered ${accessCode.length} of 6 digits. Enter ${missing} more.`);
        return;
    }

    if (!navigator.onLine) {
        showError("This device is offline.");
        showPopup({ icon: "📡", title: "No internet connection", severity: "warning",
            message: "This device isn't connected to the internet. Check the Wi-Fi or data connection, then try again." });
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
        const data = await response.json().catch(() => ({}));

        if (!response.ok) {
            handleLoginError(response.status, data);
            return;
        }

        try { localStorage.removeItem(LOCK_STORAGE_KEY); } catch (_) {}
        localStorage.setItem("rmshop_user", JSON.stringify(data));
        window.location.href = data.role === "MANAGER" ? "manager-dashboard.html" : "sales.html";

    } catch (error) {
        clearTimeout(coldStartTimer);
        showError("Unable to reach the store server.");
        showPopup({ icon: "📡", title: "Can't reach the server", severity: "warning",
            message: "The store server didn't respond. It may still be waking up. Wait a few seconds and try again." });
    } finally {
        setLoading(false);
    }
});
