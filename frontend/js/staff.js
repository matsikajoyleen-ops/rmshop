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

async function loadStaff() {
    const response = await fetch(`${API_BASE_URL}/users`);
    const users = await response.json();

    tableBody.innerHTML = "";
    emptyState.hidden = users.length > 0;

    users.forEach(user => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>${user.fullName}</td>
            <td>${user.role}</td>
            <td>${statusBadge(user.active)}</td>
            <td class="actions-cell">
                <button class="btn-link" data-attendance="${user.id}" data-name="${user.fullName}">Attendance</button>
                <button class="btn-link" data-reset="${user.id}" data-name="${user.fullName}">Reset code</button>
                ${user.active ? `<button class="btn-link" data-deactivate="${user.id}" data-name="${user.fullName}">Deactivate</button>` : ""}
            </td>
        `;
        tableBody.appendChild(row);
    });
}

function openModal(id) { document.getElementById(id).hidden = false; }
function closeModal(id) { document.getElementById(id).hidden = true; }

document.querySelectorAll("[data-close-modal]").forEach(btn => {
    btn.addEventListener("click", () => { btn.closest(".modal-backdrop").hidden = true; });
});

document.getElementById("add-employee-btn").addEventListener("click", () => {
    addEmployeeCode.clear();
    openModal("add-employee-modal");
});

document.getElementById("add-employee-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById("add-employee-error");
    errorEl.hidden = true;

    const fullName = document.getElementById("new-employee-name").value.trim();
    const role = document.getElementById("new-employee-role").value;
    const accessCode = addEmployeeCode.getValue();

    if (!fullName || accessCode.length !== 6) {
        errorEl.textContent = "Enter a name and all 6 digits of the access code.";
        errorEl.hidden = false;
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/users`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ fullName, role, accessCode })
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

    const deactivateBtn = e.target.closest("[data-deactivate]");
    if (deactivateBtn) {
        const confirmed = confirm(`Deactivate ${deactivateBtn.dataset.name}? They will no longer be able to log in.`);
        if (!confirmed) return;
        await fetch(`${API_BASE_URL}/users/${deactivateBtn.dataset.deactivate}/deactivate`, { method: "PUT" });
        loadStaff();
        return;
    }

    const attendanceBtn = e.target.closest("[data-attendance]");
    if (attendanceBtn) {
        document.getElementById("attendance-name").textContent = attendanceBtn.dataset.name;
        openModal("attendance-modal");

        const response = await fetch(`${API_BASE_URL}/users/${attendanceBtn.dataset.attendance}/attendance`);
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
            headers: { "Content-Type": "application/json" },
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

loadStaff();