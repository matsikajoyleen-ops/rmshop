// Manager "forgot access code" flow:
//   1. email  -> POST /auth/forgot-password  (a 6-digit code is emailed)
//   2. code   -> POST /auth/verify-reset-code
//   3. new access code twice -> POST /auth/reset-password
// The server names the exact problem ({ code, message, field }) and this page
// shows it next to the field at fault, or as a popup when the user has to act.

const steps = {
    email: document.getElementById("step-email"),
    code: document.getElementById("step-code"),
    newCode: document.getElementById("step-new")
};
const emailInput = document.getElementById("email");
const formContainer = document.getElementById("form-container");
const resendBtn = document.getElementById("resend-btn");

const fieldErrorIds = {
    email: "email-error",
    code: "code-error",
    newAccessCode: "new-code-error",
    confirmAccessCode: "confirm-code-error"
};

let currentEmail = "";
let verifiedCode = "";
let resendTimer = null;

// ---- 6-digit boxes ---------------------------------------------------------

function buildDigitBoxes(container, onComplete) {
    container.innerHTML = "";
    const inputs = [];
    for (let i = 0; i < 6; i++) {
        const input = document.createElement("input");
        input.type = "text";
        input.inputMode = "numeric";
        input.maxLength = 1;
        input.className = "code-digit";
        input.autocomplete = i === 0 ? "one-time-code" : "off";
        input.setAttribute("aria-label", `Digit ${i + 1}`);
        container.appendChild(input);
        inputs.push(input);
    }
    inputs.forEach((input, index) => {
        input.addEventListener("input", () => {
            input.value = input.value.replace(/[^0-9]/g, "").slice(0, 1);
            if (input.value && index < inputs.length - 1) inputs[index + 1].focus();
            container.dispatchEvent(new Event("digits-changed"));
            if (onComplete && inputs.every(i => i.value)) onComplete();
        });
        input.addEventListener("keydown", (e) => {
            if (e.key === "Backspace" && !input.value && index > 0) inputs[index - 1].focus();
        });
        input.addEventListener("paste", (e) => {
            e.preventDefault();
            const pasted = (e.clipboardData || window.clipboardData).getData("text").replace(/[^0-9]/g, "");
            if (!pasted) return;
            inputs.forEach((box, i) => { box.value = pasted[i] || ""; });
            const nextEmpty = inputs.findIndex(box => !box.value);
            (nextEmpty === -1 ? inputs[inputs.length - 1] : inputs[nextEmpty]).focus();
            container.dispatchEvent(new Event("digits-changed"));
            if (onComplete && inputs.every(i => i.value)) onComplete();
        });
    });
    return {
        getValue: () => inputs.map(i => i.value).join(""),
        clear: () => { inputs.forEach(i => i.value = ""); inputs[0].focus(); },
        focus: () => inputs[0].focus(),
        markError: (on) => inputs.forEach(i => i.classList.toggle("input-error", on)),
        container
    };
}

const confirmBoxes = buildDigitBoxes(document.getElementById("confirm-code-inputs"), () => steps.code.requestSubmit());
const newBoxes = buildDigitBoxes(document.getElementById("new-code-inputs"));
const repeatBoxes = buildDigitBoxes(document.getElementById("confirm-new-code-inputs"));

const boxesForField = { code: confirmBoxes, newAccessCode: newBoxes, confirmAccessCode: repeatBoxes };

// ---- helpers ---------------------------------------------------------------

function showStep(name) {
    Object.entries(steps).forEach(([key, el]) => { el.hidden = key !== name; });
    const index = { email: 1, code: 2, newCode: 3 }[name];
    [1, 2, 3].forEach(i => document.getElementById(`step-bar-${i}`).classList.toggle("done", i <= index));
    clearAllErrors();
    if (name === "email") emailInput.focus();
    if (name === "code") confirmBoxes.clear();
    if (name === "newCode") { newBoxes.clear(); repeatBoxes.clear(); newBoxes.focus(); }
}

function clearAllErrors() {
    Object.keys(fieldErrorIds).forEach(clearFieldError);
}

function clearFieldError(field) {
    const el = document.getElementById(fieldErrorIds[field]);
    if (el) { el.hidden = true; el.textContent = ""; }
    if (field === "email") emailInput.classList.remove("input-error");
    if (boxesForField[field]) boxesForField[field].markError(false);
}

function showFieldError(field, message, extra) {
    const el = document.getElementById(fieldErrorIds[field] || fieldErrorIds.email);
    el.textContent = message;
    if (extra) el.appendChild(extra);
    el.hidden = false;
    if (field === "email") emailInput.classList.add("input-error");
    if (boxesForField[field]) boxesForField[field].markError(true);
    formContainer.classList.remove("shake");
    void formContainer.offsetWidth;
    formContainer.classList.add("shake");
}

function setBusy(button, busy) {
    button.disabled = busy;
    const text = button.querySelector(".btn-text");
    const spinner = button.querySelector(".btn-spinner");
    if (text) text.hidden = busy;
    if (spinner) spinner.hidden = !busy;
}

function maskEmail(email) {
    const [name, domain] = email.split("@");
    const visible = name.length <= 2 ? name[0] : name.slice(0, 2);
    return `${visible}${"•".repeat(Math.max(1, name.length - visible.length))}@${domain}`;
}

function startResendCountdown(seconds) {
    clearInterval(resendTimer);
    let left = seconds;
    const tick = () => {
        if (left <= 0) {
            clearInterval(resendTimer);
            resendBtn.disabled = false;
            resendBtn.textContent = "Resend code";
            return;
        }
        resendBtn.disabled = true;
        resendBtn.textContent = `Resend in ${left}s`;
        left--;
    };
    tick();
    resendTimer = setInterval(tick, 1000);
}

async function callApi(path, body, button) {
    if (!navigator.onLine) {
        showPopup({ icon: "📡", title: "No internet connection", severity: "warning",
            message: "This device isn't connected to the internet. Check the Wi-Fi or data connection, then try again." });
        return null;
    }
    setBusy(button, true);
    try {
        const response = await fetch(`${API_BASE_URL}${path}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });
        const data = await response.json().catch(() => ({}));
        return { ok: response.ok, status: response.status, data };
    } catch (_) {
        showPopup({ icon: "📡", title: "Can't reach the server", severity: "warning",
            message: "The store server didn't respond. It may still be waking up. Wait a few seconds and try again." });
        return null;
    } finally {
        setBusy(button, false);
    }
}

function requestNewCodePopup(icon, title, message) {
    showPopup({
        icon, title, message, severity: "danger",
        actions: [
            { label: "Send a new code", primary: true, onClick: () => requestCode(currentEmail, document.getElementById("resend-btn")) },
            { label: "Change email", onClick: () => showStep("email") }
        ]
    });
}

// Handles any error the reset endpoints can return
function handleError(status, data) {
    const message = data.message || "Something went wrong. Please try again.";
    switch (data.code) {
        case "EMAIL_POSSIBLE_TYPO": {
            const fix = document.createElement("button");
            fix.type = "button";
            fix.textContent = `Use ${data.suggestion}`;
            fix.addEventListener("click", () => {
                emailInput.value = data.suggestion;
                clearFieldError("email");
                emailInput.focus();
            });
            showFieldError("email", "Did you mean the address below? ", fix);
            return;
        }
        case "RESET_TOO_SOON":
        case "RESET_LIMIT_REACHED":
            if (!steps.code.hidden) {
                startResendCountdown(data.retryAfterSeconds || 60);
                showPopup({ icon: "⏳", title: "Please wait", message, severity: "warning", actions: [{ label: "OK", primary: true }] });
            } else {
                showFieldError("email", message);
            }
            return;
        case "EMAIL_SEND_FAILED":
            showPopup({ icon: "✉️", title: "Email not sent", message, severity: "warning", actions: [{ label: "OK", primary: true }] });
            return;
        case "RESET_CODE_WRONG":
            showStep("code");
            showFieldError("code", message);
            if (data.attemptsRemaining <= 2) {
                showPopup({ icon: "⚠️", title: `${data.attemptsRemaining} attempt${data.attemptsRemaining === 1 ? "" : "s"} left`,
                    message: `${message} Check the latest email from RMShop and type the code exactly as shown.`,
                    severity: "warning", actions: [{ label: "OK", primary: true, onClick: () => confirmBoxes.focus() }] });
            }
            return;
        case "RESET_CODE_EXPIRED":
            requestNewCodePopup("⏰", "Code expired", message);
            return;
        case "RESET_CODE_LOCKED":
            requestNewCodePopup("🔒", "Code cancelled", message);
            return;
        case "RESET_CODE_INVALID":
            requestNewCodePopup("❓", "No active code", message);
            return;
    }

    if (data.field && fieldErrorIds[data.field]) {
        showFieldError(data.field, message);
    } else if (status >= 500) {
        showPopup({ icon: "🛠️", title: "Server error", severity: "warning",
            message: "The server ran into a problem. Please try again in a moment." });
    } else {
        showPopup({ icon: "⚠️", title: "Something went wrong", message, severity: "warning" });
    }
}

// ---- step 1: request a code ------------------------------------------------

async function requestCode(email, button) {
    const result = await callApi("/auth/forgot-password", { email }, button);
    if (!result) return;
    if (!result.ok) {
        handleError(result.status, result.data);
        return;
    }
    currentEmail = email.trim().toLowerCase();
    document.getElementById("code-sent-to").textContent = `If ${maskEmail(currentEmail)} is a manager's email, a code was sent to it.`;
    document.getElementById("request-note").textContent = result.data.message;
    showStep("code");
    startResendCountdown(result.data.resendAvailableInSeconds || 60);
}

emailInput.addEventListener("input", () => clearFieldError("email"));

steps.email.addEventListener("submit", (e) => {
    e.preventDefault();
    clearFieldError("email");
    requestCode(emailInput.value, document.getElementById("send-btn"));
});

// ---- step 2: confirm the code ----------------------------------------------

confirmBoxes.container.addEventListener("digits-changed", () => clearFieldError("code"));

steps.code.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = confirmBoxes.getValue();
    if (code.length !== 6) {
        showFieldError("code", code.length === 0
            ? "Enter the 6-digit code from the email."
            : `You've entered ${code.length} of 6 digits. Enter ${6 - code.length} more.`);
        return;
    }
    const result = await callApi("/auth/verify-reset-code", { email: currentEmail, code }, document.getElementById("verify-btn"));
    if (!result) return;
    if (!result.ok) {
        handleError(result.status, result.data);
        return;
    }
    verifiedCode = code;
    showStep("newCode");
});

resendBtn.addEventListener("click", () => requestCode(currentEmail, resendBtn));
document.getElementById("change-email-btn").addEventListener("click", () => showStep("email"));

// ---- step 3: set the new access code ---------------------------------------

newBoxes.container.addEventListener("digits-changed", () => clearFieldError("newAccessCode"));
repeatBoxes.container.addEventListener("digits-changed", () => clearFieldError("confirmAccessCode"));

steps.newCode.addEventListener("submit", async (e) => {
    e.preventDefault();
    const newAccessCode = newBoxes.getValue();
    const confirmAccessCode = repeatBoxes.getValue();
    if (newAccessCode.length !== 6) {
        showFieldError("newAccessCode", `Your new code has ${newAccessCode.length} of 6 digits.`);
        return;
    }
    if (confirmAccessCode.length !== 6) {
        showFieldError("confirmAccessCode", "Type all 6 digits again to confirm.");
        return;
    }
    const result = await callApi("/auth/reset-password",
        { email: currentEmail, code: verifiedCode, newAccessCode, confirmAccessCode },
        document.getElementById("save-btn"));
    if (!result) return;
    if (!result.ok) {
        handleError(result.status, result.data);
        return;
    }
    try { localStorage.removeItem("rmshop_login_locked_until"); } catch (_) {}
    showPopup({
        icon: "✅", title: "Access code changed", severity: "success",
        message: `${result.data.message} Taking you to the login screen…`,
        actions: [{ label: "Go to login", primary: true, onClick: () => { window.location.href = "index.html"; } }]
    });
    setTimeout(() => { window.location.href = "index.html"; }, 4000);
});
