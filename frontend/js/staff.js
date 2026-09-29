document.getElementById("welcome-name").textContent = currentUser.fullName;
document.getElementById("logout-link").addEventListener("click", (e) => {
    e.preventDefault();
    logout();
});

function formatDateTime(iso) {
    return new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

function buildDigitBoxes(container) {
    container.innerHTML = "";
    for (let i = 0; i < 6; i++) {
        const input = document.createElement("input");
        input.type = "text";
        input.inputMode = "numeric";
        input.maxLength = 1;
        input.className = "code-digit";
        input.autocomplete = "off";
        container.appendChild(input);
    }
    const inputs = Array.from(container.querySelectorAll(".code-digit"));
    inputs.forEach((input, index) => {
        input.addEventListener("input", () => {
            input.value = input.value.replace(/[^0-9]/g, "").slice(0, 1);
            if (input.value && index < inputs.length - 1) inputs[index + 1].focus();
        });
        input.addEventListener("keydown", (e) => {
            if (e.key === "Backspace" && !input.value && index > 0) inputs[index - 1].focus();
        });
    });
    return {
        getValue: () => inputs.map(i => i.value).join(""),
        clear: () => { inputs.forEach(i => i.value = ""); inputs[0].focus(); }
    };
}

const addEmployeeCode = buildDigitBoxes(document.getElementById("add-employee-code"));
const resetCode = buildDigitBoxes(document.getElementById("reset-code-inputs"));

const tableBody = document.getElementById("staff-body");
const emptyState = document.getElementById("empty-state");

function statusBadge(active) {
    return active ? `<span class="badge badge-ok">Active</span>` : `<span class="badge badge-critical">Deactivated</span>`;
}

function escapeHtml(text) {
    return String(text ?? "").replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function emailCell(user) {
    if (user.email) return escapeHtml(user.email);
    return user.role === "MANAGER"
        ? `<span class="muted-cell">Not set: can't reset by email</span>`
        : `<span class="muted-cell">—</span>`;
}

function renderStaffRows(users) {
    tableBody.innerHTML = "";
    emptyState.hidden = users.length > 0;

    users.forEach(user => {
        const name = escapeHtml(user.fullName);
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${name}</td>
            <td>${escapeHtml(user.role)}</td>
            <td>${emailCell(user)}</td>
            <td>${statusBadge(user.active)}</td>
            <td class="actions-cell">
                <button class="btn-link" data-attendance="${user.id}" data-name="${name}">Attendance</button>
                <button class="btn-link" data-reset="${user.id}" data-name="${name}">Reset code</button>
                <button class="btn-link" data-email="${user.id}" data-name="${name}" data-current="${escapeHtml(user.email || "")}">${user.email ? "Change email" : "Set email"}</button>
                ${user.active ? `<button class="btn-link" data-deactivate="${user.id}" data-name="${name}">Deactivate</button>` : ""}
            </td>
        `;
        tableBody.appendChild(row);
    });
}

async function loadStaff() {
    try {
        const cached = localStorage.getItem("rmshop_staff_cache");
        if (cached) {
            const list = JSON.parse(cached);
            if (Array.isArray(list) && list.length > 0) renderStaffRows(list);
        }
    } catch (_) {}

    try {
        const response = await fetch(`${API_BASE_URL}/users`, { headers: authHeaders() });
        if (response.ok) {
            const users = await response.json();
            localStorage.setItem("rmshop_staff_cache", JSON.stringify(users));
            renderStaffRows(users);
        }
    } catch (err) {
        console.error("Failed to refresh staff", err);
    }
}

function openModal(id) { document.getElementById(id).hidden = false; }
function closeModal(id) { document.getElementById(id).hidden = true; }

document.querySelectorAll("[data-close-modal]").forEach(btn => {
    btn.addEventListener("click", () => { btn.closest(".modal-backdrop").hidden = true; });
});

const roleSelect = document.getElementById("new-employee-role");
function syncEmailField() {
    document.getElementById("new-employee-email-label").hidden = roleSelect.value !== "MANAGER";
}
roleSelect.addEventListener("change", syncEmailField);

document.getElementById("add-employee-btn").addEventListener("click", () => {
    addEmployeeCode.clear();
    syncEmailField();
    openModal("add-employee-modal");
});

document.getElementById("add-employee-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("add-employee-error");
    errorEl.hidden = true;

    const fullName = document.getElementById("new-employee-name").value.trim();
    const role = document.getElementById("new-employee-role").value;
    const accessCode = addEmployeeCode.getValue();
    const email = role === "MANAGER" ? document.getElementById("new-employee-email").value.trim() : "";

    if (!fullName || accessCode.length !== 6) {
        errorEl.textContent = "Enter a name and all 6 digits of the access code.";
        errorEl.hidden = false;
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/users`, {
            method: "POST",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ fullName, role, accessCode, email: email || null })
        });
        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.message);
        }

        closeModal("add-employee-modal");
        e.target.reset();
        loadStaff();
    } catch (err) {
        errorEl.textContent = err.message || "Could not add the employee. Try again.";
        errorEl.hidden = false;
    }
});

tableBody.addEventListener("click", async (e) => {
    const resetBtn = e.target.closest("[data-reset]");
    if (resetBtn) {
        document.getElementById("reset-code-user-id").value = resetBtn.dataset.reset;
        document.getElementById("reset-code-name").textContent = resetBtn.dataset.name;
        resetCode.clear();
        openModal("reset-code-modal");
        return;
    }

    const emailBtn = e.target.closest("[data-email]");
    if (emailBtn) {
        document.getElementById("email-user-id").value = emailBtn.dataset.email;
        document.getElementById("email-modal-name").textContent = emailBtn.dataset.name;
        document.getElementById("email-input").value = emailBtn.dataset.current;
        document.getElementById("email-modal-error").hidden = true;
        openModal("email-modal");
        document.getElementById("email-input").focus();
        return;
    }

    const deactivateBtn = e.target.closest("[data-deactivate]");
    if (deactivateBtn) {
        const confirmed = confirm(`Deactivate ${deactivateBtn.dataset.name}? They will no longer be able to log in.`);
        if (!confirmed) return;
        await fetch(`${API_BASE_URL}/users/${deactivateBtn.dataset.deactivate}/deactivate`, { method: "PUT", headers: authHeaders() });
        loadStaff();
        return;
    }

    const attendanceBtn = e.target.closest("[data-attendance]");
    if (attendanceBtn) {
        document.getElementById("attendance-name").textContent = attendanceBtn.dataset.name;
        openModal("attendance-modal");

        const response = await fetch(`${API_BASE_URL}/users/${attendanceBtn.dataset.attendance}/attendance`, { headers: authHeaders() });
        const logs = await response.json();

        const listEl = document.getElementById("attendance-list");
        const emptyEl = document.getElementById("attendance-empty");
        listEl.innerHTML = "";
        emptyEl.hidden = logs.length > 0;

        logs.forEach(log => {
            const row = document.createElement("div");
            row.className = "attendance-row";
            row.textContent = formatDateTime(log.loginTime);
            listEl.appendChild(row);
        });
    }
});

document.getElementById("reset-code-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("reset-code-error");
    errorEl.hidden = true;

    const userId = document.getElementById("reset-code-user-id").value;
    const newAccessCode = resetCode.getValue();

    if (newAccessCode.length !== 6) {
        errorEl.textContent = "Enter all 6 digits.";
        errorEl.hidden = false;
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/users/${userId}/access-code`, {
            method: "PUT",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ newAccessCode })
        });
        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            throw new Error(data.message);
        }

        closeModal("reset-code-modal");
    } catch (err) {
        errorEl.textContent = err.message || "Could not reset the code. Try again.";
        errorEl.hidden = false;
    }
});


document.getElementById("email-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("email-modal-error");
    const input = document.getElementById("email-input");
    errorEl.hidden = true;

    const userId = document.getElementById("email-user-id").value;
    try {
        const response = await fetch(`${API_BASE_URL}/users/${userId}/email`, {
            method: "PUT",
            headers: authHeaders({ "Content-Type": "application/json" }),
            body: JSON.stringify({ email: input.value })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            // The server says exactly what's wrong with the address, e.g. a missing @
            errorEl.textContent = data.suggestion
                ? `${data.message} We filled it in for you. Click Save to use it.`
                : (data.message || "Could not save the email.");
            if (data.suggestion) input.value = data.suggestion;
            errorEl.hidden = false;
            input.focus();
            return;
        }
        closeModal("email-modal");
        loadStaff();
    } catch (err) {
        errorEl.textContent = "Could not reach the server. Try again.";
        errorEl.hidden = false;
    }
});

loadStaff();
