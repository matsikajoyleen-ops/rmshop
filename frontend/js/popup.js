// Shared popup dialog for the login and forgot-code pages.
// showPopup({ icon, title, message, severity, attempt, maxAttempts,
//             countdownSeconds, onCountdownEnd, actions: [{ label, primary, onClick }] })
// Only one popup is shown at a time; showing a new one replaces the old.

let popupTimer = null;

function closePopup() {
    clearInterval(popupTimer);
    popupTimer = null;
    const existing = document.getElementById("rm-popup");
    if (existing) existing.remove();
    document.removeEventListener("keydown", popupKeyHandler);
}

function popupKeyHandler(e) {
    if (e.key === "Escape" || e.key === "Enter") {
        const popup = document.getElementById("rm-popup");
        // A lock countdown can't be dismissed early
        if (popup && !popup.dataset.locked) {
            e.preventDefault();
            closePopup();
        }
    }
}

function formatCountdown(totalSeconds) {
    const m = Math.floor(totalSeconds / 60);
    const s = totalSeconds % 60;
    return `${m}:${String(s).padStart(2, "0")}`;
}

function showPopup(options) {
    closePopup();
    const severity = options.severity || "info";

    const backdrop = document.createElement("div");
    backdrop.className = "modal-backdrop";
    backdrop.id = "rm-popup";

    const box = document.createElement("div");
    box.className = `modal popup severity-${severity}`;
    box.setAttribute("role", "alertdialog");
    box.setAttribute("aria-modal", "true");
    box.setAttribute("aria-labelledby", "rm-popup-title");
    box.setAttribute("aria-describedby", "rm-popup-message");

    const icon = document.createElement("div");
    icon.className = "popup-icon";
    icon.textContent = options.icon || "ℹ️";
    box.appendChild(icon);

    const title = document.createElement("h2");
    title.id = "rm-popup-title";
    title.textContent = options.title;
    box.appendChild(title);

    const message = document.createElement("p");
    message.id = "rm-popup-message";
    message.className = "popup-message";
    message.textContent = options.message;
    box.appendChild(message);

    if (options.attempt && options.maxAttempts) {
        const dots = document.createElement("div");
        dots.className = "attempt-dots";
        dots.setAttribute("aria-hidden", "true");
        for (let i = 1; i <= options.maxAttempts; i++) {
            const dot = document.createElement("span");
            dot.className = "attempt-dot" + (i <= options.attempt ? " used" : "");
            dots.appendChild(dot);
        }
        box.appendChild(dots);
        const caption = document.createElement("p");
        caption.className = "attempt-caption";
        caption.textContent = `Attempt ${options.attempt} of ${options.maxAttempts}`;
        box.appendChild(caption);
    }

    let countdownEl = null;
    if (options.countdownSeconds > 0) {
        backdrop.dataset.locked = "true";
        countdownEl = document.createElement("div");
        countdownEl.className = "popup-countdown";
        countdownEl.setAttribute("aria-live", "polite");
        box.appendChild(countdownEl);
    }

    const actions = document.createElement("div");
    actions.className = "modal-actions";
    const actionList = options.actions || (countdownEl ? [] : [{ label: "Try again", primary: true }]);
    actionList.forEach(action => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = action.primary ? "btn-primary btn-inline" : "btn-secondary";
        btn.textContent = action.label;
        btn.addEventListener("click", () => {
            closePopup();
            if (action.onClick) action.onClick();
        });
        actions.appendChild(btn);
    });
    if (actionList.length) box.appendChild(actions);

    backdrop.appendChild(box);
    document.body.appendChild(backdrop);
    document.addEventListener("keydown", popupKeyHandler);
    const firstButton = box.querySelector("button");
    if (firstButton) firstButton.focus();

    if (countdownEl) {
        const endsAt = Date.now() + options.countdownSeconds * 1000;
        const tick = () => {
            const left = Math.max(0, Math.round((endsAt - Date.now()) / 1000));
            countdownEl.textContent = formatCountdown(left);
            if (left <= 0) {
                closePopup();
                if (options.onCountdownEnd) options.onCountdownEnd();
            }
        };
        tick();
        popupTimer = setInterval(tick, 1000);
    }
}
