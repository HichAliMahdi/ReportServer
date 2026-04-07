// ==================== Schedule Functions ====================

function initScheduleDropdowns() {
    const dayOfMonthSelect = document.getElementById('scheduleDayOfMonth');
    const hourSelect = document.getElementById('scheduleHour');
    const minuteSelect = document.getElementById('scheduleMinute');

    if (!dayOfMonthSelect || !hourSelect || !minuteSelect) return;

    dayOfMonthSelect.replaceChildren();
    for (let i = 1; i <= 31; i++) {
        const opt = document.createElement('option');
        opt.value = i;
        opt.textContent = i;
        dayOfMonthSelect.appendChild(opt);
    }

    hourSelect.replaceChildren();
    for (let i = 0; i < 24; i++) {
        const opt = document.createElement('option');
        opt.value = i;
        opt.textContent = String(i).padStart(2, '0') + ':00';
        hourSelect.appendChild(opt);
    }
    hourSelect.value = '8';

    minuteSelect.replaceChildren();
    for (let i = 0; i < 60; i += 5) {
        const opt = document.createElement('option');
        opt.value = i;
        opt.textContent = ':' + String(i).padStart(2, '0');
        minuteSelect.appendChild(opt);
    }
    minuteSelect.value = '0';
}

function ensureSelectValue(selectId, value, text) {
    const select = document.getElementById(selectId);
    if (!select || value == null) return;
    const stringValue = String(value);
    const existing = Array.from(select.options).find(opt => opt.value === stringValue);
    if (!existing) {
        const opt = document.createElement('option');
        opt.value = stringValue;
        opt.textContent = text || stringValue;
        select.appendChild(opt);
    }
    select.value = stringValue;
}

function toDateTimeLocalValue(date) {
    const pad = value => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
        + `T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function getDefaultScheduleStartDateTime() {
    const now = new Date();
    const roundedMinutes = Math.ceil(now.getMinutes() / 5) * 5;
    now.setMinutes(roundedMinutes >= 60 ? 0 : roundedMinutes, 0, 0);
    if (roundedMinutes >= 60) {
        now.setHours(now.getHours() + 1);
    }
    return toDateTimeLocalValue(now);
}

function getScheduleStartDateTime(schedule) {
    if (schedule && schedule.nextRunTime) {
        const nextRun = new Date(schedule.nextRunTime);
        if (!Number.isNaN(nextRun.getTime())) {
            return toDateTimeLocalValue(nextRun);
        }
    }

    const fallback = new Date();
    fallback.setSeconds(0, 0);
    fallback.setHours(schedule && schedule.hourOfDay != null ? schedule.hourOfDay : 8);
    fallback.setMinutes(schedule && schedule.minuteOfHour != null ? schedule.minuteOfHour : 0);
    if (schedule && schedule.dayOfMonth) fallback.setDate(schedule.dayOfMonth);
    if (schedule && schedule.monthOfYear) fallback.setMonth(schedule.monthOfYear - 1);
    return toDateTimeLocalValue(fallback);
}

function updateScheduleRecurrenceSummary() {
    const summary = document.getElementById('scheduleRecurrenceSummary');
    const type = document.getElementById('scheduleType')?.value;
    const dayOfWeek = parseInt(document.getElementById('scheduleDayOfWeek')?.value, 10);
    const dayOfMonth = parseInt(document.getElementById('scheduleDayOfMonth')?.value, 10);
    const monthOfYear = parseInt(document.getElementById('scheduleMonth')?.value, 10);
    const hourOfDay = parseInt(document.getElementById('scheduleHour')?.value, 10);
    const minuteOfHour = parseInt(document.getElementById('scheduleMinute')?.value, 10);
    const startDateTime = document.getElementById('scheduleStartDateTime')?.value;

    if (!summary) return;

    const dayNames = ['', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const monthNames = ['', 'January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
    const h = Number.isInteger(hourOfDay) ? String(hourOfDay).padStart(2, '0') : '00';
    const m = Number.isInteger(minuteOfHour) ? String(minuteOfHour).padStart(2, '0') : '00';

    let recurrenceText = 'Schedule not configured';
    switch (type) {
        case 'HOURLY':
            recurrenceText = `Hourly at :${m}`;
            break;
        case 'DAILY':
            recurrenceText = `Daily at ${h}:${m}`;
            break;
        case 'WEEKLY':
            recurrenceText = `Weekly on ${dayNames[dayOfWeek] || 'Monday'} at ${h}:${m}`;
            break;
        case 'MONTHLY':
            recurrenceText = `Monthly on day ${dayOfMonth || 1} at ${h}:${m}`;
            break;
        case 'YEARLY':
            recurrenceText = `Yearly on ${monthNames[monthOfYear] || 'January'} ${dayOfMonth || 1} at ${h}:${m}`;
            break;
    }

    if (startDateTime) {
        const parsed = new Date(startDateTime);
        if (!Number.isNaN(parsed.getTime())) {
            recurrenceText += ` · Start ${parsed.toLocaleString()}`;
        }
    }

    summary.textContent = recurrenceText;
}

function updateScheduleFields() {
    const type = document.getElementById('scheduleType').value;

    document.getElementById('scheduleFieldMonth').style.display = 'none';
    document.getElementById('scheduleFieldDayOfMonth').style.display = 'none';
    document.getElementById('scheduleFieldDayOfWeek').style.display = 'none';
    document.getElementById('scheduleFieldHour').style.display = 'none';

    switch (type) {
        case 'DAILY':
            document.getElementById('scheduleFieldHour').style.display = 'block';
            break;
        case 'WEEKLY':
            document.getElementById('scheduleFieldDayOfWeek').style.display = 'block';
            document.getElementById('scheduleFieldHour').style.display = 'block';
            break;
        case 'MONTHLY':
            document.getElementById('scheduleFieldDayOfMonth').style.display = 'block';
            document.getElementById('scheduleFieldHour').style.display = 'block';
            break;
        case 'YEARLY':
            document.getElementById('scheduleFieldMonth').style.display = 'block';
            document.getElementById('scheduleFieldDayOfMonth').style.display = 'block';
            document.getElementById('scheduleFieldHour').style.display = 'block';
            break;
        default:
            break;
    }

    updateScheduleRecurrenceSummary();
}

function syncScheduleFieldsFromStartDate() {
    const value = document.getElementById('scheduleStartDateTime')?.value;
    if (!value) {
        updateScheduleRecurrenceSummary();
        return;
    }

    const start = new Date(value);
    if (Number.isNaN(start.getTime())) {
        updateScheduleRecurrenceSummary();
        return;
    }

    const weekday = ((start.getDay() + 6) % 7) + 1;
    ensureSelectValue('scheduleMinute', start.getMinutes(), ':' + String(start.getMinutes()).padStart(2, '0'));
    ensureSelectValue('scheduleHour', start.getHours(), String(start.getHours()).padStart(2, '0') + ':00');
    ensureSelectValue('scheduleDayOfWeek', weekday, null);
    ensureSelectValue('scheduleDayOfMonth', start.getDate(), null);
    ensureSelectValue('scheduleMonth', start.getMonth() + 1, null);

    updateScheduleRecurrenceSummary();
}

function updateScheduleDeliveryOptions() {
    const method = document.getElementById('scheduleDeliveryMethod')?.value;
    const warning = document.getElementById('scheduleDeliveryUnsupportedNotice');
    const saveBtn = document.getElementById('scheduleSaveBtn');
    const testBtn = document.getElementById('scheduleSendTestDeliveryBtn');
    const outputPath = document.getElementById('scheduleOutputPath');
    const emailGroup = document.getElementById('scheduleEmailRecipientsGroup');
    const webhookGroup = document.getElementById('scheduleWebhookUrlGroup');

    if (emailGroup) {
        emailGroup.style.display = method === 'EMAIL' ? 'block' : 'none';
    }
    if (webhookGroup) {
        webhookGroup.style.display = method === 'WEBHOOK' ? 'block' : 'none';
    }

    // Keep output path available for local archival regardless of delivery type.
    if (outputPath) {
        outputPath.disabled = false;
    }
    if (warning) {
        warning.style.display = 'none';
    }
    if (saveBtn) {
        saveBtn.disabled = false;
    }
    if (testBtn) {
        testBtn.disabled = method === 'FILE_SYSTEM';
    }

    if (method === 'FILE_SYSTEM') {
        showScheduleDeliveryTestMessage('File-system delivery does not require a test request.', 'info');
    } else {
        clearScheduleDeliveryTestMessage();
    }
}

function showScheduleDeliveryTestMessage(message, type = 'info') {
    const messageEl = document.getElementById('scheduleDeliveryTestMessage');
    if (!messageEl) return;

    messageEl.textContent = message || '';
    messageEl.className = 'scheduler-test-message';
    if (type === 'success' || type === 'error') {
        messageEl.classList.add(type);
    }
}

function clearScheduleDeliveryTestMessage() {
    showScheduleDeliveryTestMessage('', 'info');
}

function sendTestScheduleDelivery() {
    const method = document.getElementById('scheduleDeliveryMethod')?.value;
    const reportName = document.getElementById('scheduleReportName')?.value || 'report';
    const warning = document.getElementById('scheduleDeliveryUnsupportedNotice');
    const testBtn = document.getElementById('scheduleSendTestDeliveryBtn');

    if (!method || method === 'FILE_SYSTEM') {
        showScheduleDeliveryTestMessage('File-system delivery does not require a test request.', 'info');
        return;
    }

    const payload = {
        deliveryMethod: method,
        reportName
    };

    if (method === 'EMAIL') {
        const recipients = (document.getElementById('scheduleEmailRecipients')?.value || '').trim();
        if (!recipients) {
            if (warning) warning.style.display = 'block';
            showScheduleDeliveryTestMessage('Please provide at least one email recipient.', 'error');
            return;
        }
        payload.emailRecipients = recipients;
    }

    if (method === 'WEBHOOK') {
        const webhookUrl = (document.getElementById('scheduleWebhookUrl')?.value || '').trim();
        if (!webhookUrl) {
            if (warning) warning.style.display = 'block';
            showScheduleDeliveryTestMessage('Please provide a webhook URL.', 'error');
            return;
        }

        try {
            new URL(webhookUrl);
        } catch (_error) {
            if (warning) warning.style.display = 'block';
            showScheduleDeliveryTestMessage('Please provide a valid webhook URL.', 'error');
            return;
        }
        payload.webhookUrl = webhookUrl;
    }

    if (warning) warning.style.display = 'none';

    const headers = getHeadersWithCSRF({ 'Content-Type': 'application/json' });
    const originalLabel = testBtn ? testBtn.textContent : 'Send Test Delivery';
    if (testBtn) {
        testBtn.disabled = true;
        testBtn.textContent = 'Sending...';
    }

    showScheduleDeliveryTestMessage('Sending test delivery...', 'info');

    fetch('/api/schedules/test-delivery', {
        method: 'POST',
        headers,
        body: JSON.stringify(payload)
    })
        .then(async res => {
            const data = await res.json().catch(() => ({}));
            if (!res.ok || data.status === 'error') {
                throw new Error(data.message || 'Failed to send test delivery.');
            }
            return data;
        })
        .then(data => {
            showScheduleDeliveryTestMessage(data.message || 'Test delivery sent successfully.', 'success');
        })
        .catch(err => {
            showScheduleDeliveryTestMessage(err.message || 'Failed to send test delivery.', 'error');
        })
        .finally(() => {
            if (testBtn) {
                testBtn.textContent = originalLabel;
                updateScheduleDeliveryOptions();
            }
        });
}

function loadSchedules() {
    const headers = {};
    if (csrfHeader && csrfToken) {
        headers[csrfHeader] = csrfToken;
    }
    fetch('/api/schedules?page=0&size=100', { headers })
        .then(res => res.json())
        .then(payload => {
            const schedules = Array.isArray(payload) ? payload : (payload.content || []);
            renderSchedulesTable(schedules);
        })
        .catch(err => {
            console.error('Error loading schedules:', err);
        });
}

function renderSchedulesTable(schedules) {
    const tbody = document.getElementById('schedulesTableBody');
    if (!schedules || schedules.length === 0) {
        tbody.replaceChildren();
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 8;
        cell.style.textAlign = 'center';
        cell.style.color = '#999';
        cell.textContent = 'No scheduled reports configured';
        row.appendChild(cell);
        tbody.appendChild(row);
        return;
    }
    tbody.replaceChildren();
    schedules.forEach(s => {
        const row = document.createElement('tr');

        const nameCell = document.createElement('td');
        const nameStrong = document.createElement('strong');
        nameStrong.textContent = s.name || '';
        nameCell.appendChild(nameStrong);
        if (s.description) {
            const lineBreak = document.createElement('br');
            const description = document.createElement('small');
            description.style.color = '#999';
            description.textContent = s.description;
            nameCell.appendChild(lineBreak);
            nameCell.appendChild(description);
        }

        const reportCell = document.createElement('td');
        reportCell.textContent = s.reportName || '';

        const scheduleCell = document.createElement('td');
        scheduleCell.textContent = formatScheduleType(s);

        const formatCell = document.createElement('td');
        formatCell.textContent = String(s.format || '').toUpperCase();

        const nextRunCell = document.createElement('td');
        nextRunCell.textContent = s.nextRunTime ? formatDateTime(s.nextRunTime) : '—';

        const lastRunCell = document.createElement('td');
        lastRunCell.textContent = s.lastRunTime ? formatDateTime(s.lastRunTime) : 'Never';

        const statusCell = document.createElement('td');
        const statusBadge = document.createElement('span');
        statusBadge.style.padding = '4px 10px';
        statusBadge.style.borderRadius = '12px';
        statusBadge.style.fontSize = '12px';
        statusBadge.style.fontWeight = '600';
        if (s.enabled) {
            statusBadge.style.background = '#d4edda';
            statusBadge.style.color = '#155724';
            statusBadge.textContent = 'Active';
        } else {
            statusBadge.style.background = '#f8d7da';
            statusBadge.style.color = '#721c24';
            statusBadge.textContent = 'Paused';
        }
        statusCell.appendChild(statusBadge);

        const actionsCell = document.createElement('td');
        actionsCell.style.whiteSpace = 'nowrap';

        const editBtn = document.createElement('button');
        editBtn.className = 'btn-small';
        editBtn.type = 'button';
        editBtn.textContent = '✏️';
        editBtn.title = 'Edit';
        editBtn.addEventListener('click', () => editSchedule(s.id));

        const toggleBtn = document.createElement('button');
        toggleBtn.className = 'btn-small';
        toggleBtn.type = 'button';
        toggleBtn.title = s.enabled ? 'Pause' : 'Resume';
        toggleBtn.style.background = s.enabled ? '#ffc107' : '#28a745';
        toggleBtn.style.color = s.enabled ? '#333' : '#fff';
        toggleBtn.textContent = s.enabled ? '⏸' : '▶️';
        toggleBtn.addEventListener('click', () => toggleSchedule(s.id, !s.enabled));

        const runBtn = document.createElement('button');
        runBtn.className = 'btn-small';
        runBtn.type = 'button';
        runBtn.style.background = '#17a2b8';
        runBtn.title = 'Run Now';
        runBtn.textContent = '▶';
        runBtn.addEventListener('click', () => runScheduleNow(s.id));

        const deleteBtn = document.createElement('button');
        deleteBtn.className = 'btn-small btn-danger';
        deleteBtn.type = 'button';
        deleteBtn.title = 'Delete';
        deleteBtn.textContent = '🗑';
        deleteBtn.addEventListener('click', () => deleteSchedule(s.id, s.name || ''));

        actionsCell.appendChild(editBtn);
        actionsCell.appendChild(toggleBtn);
        actionsCell.appendChild(runBtn);
        actionsCell.appendChild(deleteBtn);

        row.appendChild(nameCell);
        row.appendChild(reportCell);
        row.appendChild(scheduleCell);
        row.appendChild(formatCell);
        row.appendChild(nextRunCell);
        row.appendChild(lastRunCell);
        row.appendChild(statusCell);
        row.appendChild(actionsCell);

        tbody.appendChild(row);
    });
}

function formatDateTime(dt) {
    if (!dt) return '—';
    const d = new Date(dt);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatScheduleType(s) {
    const dayNames = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const h = s.hourOfDay != null ? String(s.hourOfDay).padStart(2, '0') : '00';
    const m = s.minuteOfHour != null ? String(s.minuteOfHour).padStart(2, '0') : '00';
    switch (s.scheduleType) {
        case 'HOURLY':
            return `Hourly at :${m}`;
        case 'DAILY':
            return `Daily at ${h}:${m}`;
        case 'WEEKLY':
            return `Weekly on ${dayNames[s.dayOfWeek] || 'Mon'} at ${h}:${m}`;
        case 'MONTHLY':
            return `Monthly on day ${s.dayOfMonth || 1} at ${h}:${m}`;
        case 'YEARLY':
            return `Yearly on ${monthNames[s.monthOfYear] || 'Jan'} ${s.dayOfMonth || 1} at ${h}:${m}`;
        default:
            return s.scheduleType;
    }
}

function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function openScheduleModal(schedule) {
    const form = document.getElementById('scheduleForm');

    document.getElementById('scheduleModalTitle').textContent = schedule ? 'Edit Scheduled Report' : 'Create Scheduled Report';
    document.getElementById('scheduleId').value = schedule ? schedule.id : '';
    document.getElementById('scheduleName').value = schedule ? schedule.name : '';
    document.getElementById('scheduleFormat').value = schedule ? schedule.format : 'pdf';
    document.getElementById('scheduleType').value = schedule ? schedule.scheduleType : 'WEEKLY';
    document.getElementById('scheduleOutputPath').value = schedule ? (schedule.outputPath || '') : 'data/scheduled_output/';
    document.getElementById('scheduleDescription').value = schedule ? (schedule.description || '') : '';
    document.getElementById('scheduleStartDateTime').value = schedule
        ? getScheduleStartDateTime(schedule)
        : getDefaultScheduleStartDateTime();
    document.getElementById('scheduleDeliveryMethod').value = schedule && schedule.deliveryMethod
        ? String(schedule.deliveryMethod).toUpperCase()
        : 'FILE_SYSTEM';
    document.getElementById('scheduleEmailRecipients').value = schedule ? (schedule.emailRecipients || '') : '';
    document.getElementById('scheduleWebhookUrl').value = schedule ? (schedule.webhookUrl || '') : '';
    clearScheduleDeliveryTestMessage();

    if (form) {
        form.dataset.enabled = schedule ? String(schedule.enabled !== false) : 'true';
    }

    loadScheduleReports(schedule ? schedule.reportName : null);
    loadScheduleDatasources(schedule ? schedule.datasourceId : null);
    initScheduleDropdowns();

    if (schedule) {
        if (schedule.dayOfWeek) ensureSelectValue('scheduleDayOfWeek', schedule.dayOfWeek, null);
        if (schedule.dayOfMonth) ensureSelectValue('scheduleDayOfMonth', schedule.dayOfMonth, null);
        if (schedule.monthOfYear) ensureSelectValue('scheduleMonth', schedule.monthOfYear, null);
        if (schedule.hourOfDay != null) ensureSelectValue('scheduleHour', schedule.hourOfDay, String(schedule.hourOfDay).padStart(2, '0') + ':00');
        if (schedule.minuteOfHour != null) ensureSelectValue('scheduleMinute', schedule.minuteOfHour, ':' + String(schedule.minuteOfHour).padStart(2, '0'));
    } else {
        syncScheduleFieldsFromStartDate();
    }

    updateScheduleFields();
    updateScheduleDeliveryOptions();

    const modal = document.getElementById('scheduleModal');
    if (modal) modal.style.display = 'flex';
}

function closeScheduleModal() {
    const modal = document.getElementById('scheduleModal');
    if (modal) modal.style.display = 'none';
}

function loadScheduleReports(selectedReport) {
    fetch('/reports?page=0&size=200')
        .then(res => res.json())
        .then(payload => {
            const reports = Array.isArray(payload) ? payload : (payload.content || []);
            const sel = document.getElementById('scheduleReportName');
            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = '-- Select a report --';
            sel.replaceChildren(placeholder);
            reports.forEach(r => {
                const reportName = typeof r === 'string' ? r : r.reportFileName;
                const opt = document.createElement('option');
                opt.value = reportName;
                opt.textContent = reportName;
                if (selectedReport && reportName === selectedReport) opt.selected = true;
                sel.appendChild(opt);
            });
        });
}

function loadScheduleDatasources(selectedId) {
    fetch('/api/datasources')
        .then(res => res.json())
        .then(datasources => {
            const sel = document.getElementById('scheduleDatasource');
            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = '-- No datasource --';
            sel.replaceChildren(placeholder);
            datasources.forEach(ds => {
                const opt = document.createElement('option');
                opt.value = ds.id;
                opt.textContent = ds.name;
                if (selectedId && ds.id === selectedId) opt.selected = true;
                sel.appendChild(opt);
            });
        });
}

function saveSchedule(event) {
    event.preventDefault();

    const id = document.getElementById('scheduleId').value;
    const type = document.getElementById('scheduleType').value;
    const deliveryMethod = document.getElementById('scheduleDeliveryMethod').value;
    const form = document.getElementById('scheduleForm');
    const warning = document.getElementById('scheduleDeliveryUnsupportedNotice');

    const dto = {
        name: document.getElementById('scheduleName').value,
        reportName: document.getElementById('scheduleReportName').value,
        format: document.getElementById('scheduleFormat').value,
        scheduleType: type,
        outputPath: document.getElementById('scheduleOutputPath').value || null,
        description: document.getElementById('scheduleDescription').value || null,
        minuteOfHour: parseInt(document.getElementById('scheduleMinute').value, 10),
        enabled: form?.dataset.enabled !== 'false',
        deliveryMethod,
        deliveryEnabled: true
    };

    if (deliveryMethod === 'EMAIL') {
        const recipients = (document.getElementById('scheduleEmailRecipients').value || '').trim();
        if (!recipients) {
            if (warning) warning.style.display = 'block';
            showMessage('Please provide at least one email recipient.', 'error');
            return;
        }
        dto.emailRecipients = recipients;
    }

    if (deliveryMethod === 'WEBHOOK') {
        const webhookUrl = (document.getElementById('scheduleWebhookUrl').value || '').trim();
        if (!webhookUrl) {
            if (warning) warning.style.display = 'block';
            showMessage('Please provide a webhook URL.', 'error');
            return;
        }

        try {
            new URL(webhookUrl);
        } catch (_error) {
            if (warning) warning.style.display = 'block';
            showMessage('Please provide a valid webhook URL.', 'error');
            return;
        }
        dto.webhookUrl = webhookUrl;
    }

    if (warning) warning.style.display = 'none';

    const dsId = document.getElementById('scheduleDatasource').value;
    if (dsId) dto.datasourceId = parseInt(dsId, 10);

    if (['DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY'].includes(type)) {
        dto.hourOfDay = parseInt(document.getElementById('scheduleHour').value, 10);
    }
    if (type === 'WEEKLY') {
        dto.dayOfWeek = parseInt(document.getElementById('scheduleDayOfWeek').value, 10);
    }
    if (['MONTHLY', 'YEARLY'].includes(type)) {
        dto.dayOfMonth = parseInt(document.getElementById('scheduleDayOfMonth').value, 10);
    }
    if (type === 'YEARLY') {
        dto.monthOfYear = parseInt(document.getElementById('scheduleMonth').value, 10);
    }

    const headers = { 'Content-Type': 'application/json' };
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;

    const url = id ? `/api/schedules/${id}` : '/api/schedules';
    const method = id ? 'PUT' : 'POST';

    fetch(url, { method, headers, body: JSON.stringify(dto) })
        .then(res => {
            if (!res.ok) throw new Error('Failed to save schedule');
            return res.json();
        })
        .then(() => {
            closeScheduleModal();
            loadSchedules();
            showMessage('Schedule saved successfully!', 'success');
        })
        .catch(err => {
            alert('Error saving schedule: ' + err.message);
        });
}

function editSchedule(id) {
    const headers = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    fetch(`/api/schedules/${id}`, { headers })
        .then(res => res.json())
        .then(schedule => {
            openScheduleModal(schedule);
        })
        .catch(err => {
            alert('Error loading schedule: ' + err.message);
        });
}

function deleteSchedule(id, name) {
    if (!confirm(`Are you sure you want to delete the schedule "${name}"?`)) return;
    const headers = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    fetch(`/api/schedules/${id}`, { method: 'DELETE', headers })
        .then(res => {
            if (!res.ok) throw new Error('Failed to delete schedule');
            loadSchedules();
            showMessage('Schedule deleted successfully!', 'success');
        })
        .catch(err => {
            alert('Error deleting schedule: ' + err.message);
        });
}

function toggleSchedule(id, enabled) {
    const headers = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    fetch(`/api/schedules/${id}/toggle?enabled=${enabled}`, { method: 'POST', headers })
        .then(res => {
            if (!res.ok) throw new Error('Failed to toggle schedule');
            loadSchedules();
        })
        .catch(err => {
            alert('Error toggling schedule: ' + err.message);
        });
}

function runScheduleNow(id) {
    if (!confirm('Execute this scheduled report now?')) return;
    const headers = {};
    if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
    fetch(`/api/schedules/${id}/execute`, { method: 'POST', headers })
        .then(res => {
            if (!res.ok) throw new Error('Failed to execute schedule');
            return res.text();
        })
        .then(msg => {
            showMessage(msg || 'Report execution started!', 'success');
            setTimeout(loadSchedules, 2000);
        })
        .catch(err => {
            alert('Error executing schedule: ' + err.message);
        });
}
