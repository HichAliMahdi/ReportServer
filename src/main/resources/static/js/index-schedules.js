// ==================== Schedule Functions ====================

        function initScheduleDropdowns() {
            // Populate day of month (1-31)
            const dayOfMonthSelect = document.getElementById('scheduleDayOfMonth');
            dayOfMonthSelect.innerHTML = '';
            for (let i = 1; i <= 31; i++) {
                const opt = document.createElement('option');
                opt.value = i;
                opt.textContent = i;
                dayOfMonthSelect.appendChild(opt);
            }
            // Populate hours (0-23)
            const hourSelect = document.getElementById('scheduleHour');
            hourSelect.innerHTML = '';
            for (let i = 0; i < 24; i++) {
                const opt = document.createElement('option');
                opt.value = i;
                opt.textContent = String(i).padStart(2, '0') + ':00';
                hourSelect.appendChild(opt);
            }
            hourSelect.value = '8';
            // Populate minutes (0-59 in steps of 5)
            const minuteSelect = document.getElementById('scheduleMinute');
            minuteSelect.innerHTML = '';
            for (let i = 0; i < 60; i += 5) {
                const opt = document.createElement('option');
                opt.value = i;
                opt.textContent = ':' + String(i).padStart(2, '0');
                minuteSelect.appendChild(opt);
            }
        }

        function updateScheduleFields() {
            const type = document.getElementById('scheduleType').value;
            document.getElementById('scheduleFieldMonth').style.display = 'none';
            document.getElementById('scheduleFieldDayOfMonth').style.display = 'none';
            document.getElementById('scheduleFieldDayOfWeek').style.display = 'none';
            document.getElementById('scheduleFieldHour').style.display = 'none';

            switch (type) {
                case 'HOURLY':
                    // Only minute
                    break;
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
            }
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
                tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: #999;">No scheduled reports configured</td></tr>';
                return;
            }
            tbody.innerHTML = schedules.map(s => {
                const statusBadge = s.enabled
                    ? '<span style="background:#d4edda;color:#155724;padding:4px 10px;border-radius:12px;font-size:12px;font-weight:600;">Active</span>'
                    : '<span style="background:#f8d7da;color:#721c24;padding:4px 10px;border-radius:12px;font-size:12px;font-weight:600;">Paused</span>';
                const nextRun = s.nextRunTime ? formatDateTime(s.nextRunTime) : '—';
                const lastRun = s.lastRunTime ? formatDateTime(s.lastRunTime) : 'Never';
                return `<tr>
                    <td><strong>${escapeHtml(s.name)}</strong>${s.description ? '<br><small style="color:#999;">' + escapeHtml(s.description) + '</small>' : ''}</td>
                    <td>${escapeHtml(s.reportName)}</td>
                    <td>${formatScheduleType(s)}</td>
                    <td>${s.format.toUpperCase()}</td>
                    <td>${nextRun}</td>
                    <td>${lastRun}</td>
                    <td>${statusBadge}</td>
                    <td style="white-space: nowrap;">
                        <button class="btn-small" onclick="editSchedule(${s.id})" title="Edit">\u270f\ufe0f</button>
                        <button class="btn-small" onclick="toggleSchedule(${s.id}, ${!s.enabled})" title="${s.enabled ? 'Pause' : 'Resume'}" style="background:${s.enabled ? '#ffc107' : '#28a745'};color:${s.enabled ? '#333' : '#fff'};">${s.enabled ? '\u23f8' : '\u25b6\ufe0f'}</button>
                        <button class="btn-small" onclick="runScheduleNow(${s.id})" title="Run Now" style="background:#17a2b8;">\u25b6</button>
                        <button class="btn-small btn-danger" onclick="deleteSchedule(${s.id}, '${escapeHtml(s.name)}')" title="Delete">\ud83d\uddd1</button>
                    </td>
                </tr>`;
            }).join('');
        }

        function formatDateTime(dt) {
            if (!dt) return '—';
            const d = new Date(dt);
            return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'});
        }

        function formatScheduleType(s) {
            const dayNames = ['', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
            const monthNames = ['', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
            const h = s.hourOfDay != null ? String(s.hourOfDay).padStart(2,'0') : '00';
            const m = s.minuteOfHour != null ? String(s.minuteOfHour).padStart(2,'0') : '00';
            switch (s.scheduleType) {
                case 'HOURLY': return `Hourly at :${m}`;
                case 'DAILY': return `Daily at ${h}:${m}`;
                case 'WEEKLY': return `Weekly on ${dayNames[s.dayOfWeek] || 'Mon'} at ${h}:${m}`;
                case 'MONTHLY': return `Monthly on day ${s.dayOfMonth || 1} at ${h}:${m}`;
                case 'YEARLY': return `Yearly on ${monthNames[s.monthOfYear] || 'Jan'} ${s.dayOfMonth || 1} at ${h}:${m}`;
                default: return s.scheduleType;
            }
        }

        function escapeHtml(str) {
            if (!str) return '';
            const div = document.createElement('div');
            div.textContent = str;
            return div.innerHTML;
        }

        function openScheduleModal(schedule) {
            document.getElementById('scheduleModalTitle').textContent = schedule ? 'Edit Scheduled Report' : 'Create Scheduled Report';
            document.getElementById('scheduleId').value = schedule ? schedule.id : '';
            document.getElementById('scheduleName').value = schedule ? schedule.name : '';
            document.getElementById('scheduleFormat').value = schedule ? schedule.format : 'pdf';
            document.getElementById('scheduleType').value = schedule ? schedule.scheduleType : 'WEEKLY';
            document.getElementById('scheduleOutputPath').value = schedule ? (schedule.outputPath || '') : '';
            document.getElementById('scheduleDescription').value = schedule ? (schedule.description || '') : '';

            // Populate report dropdown
            loadScheduleReports(schedule ? schedule.reportName : null);
            // Populate datasource dropdown
            loadScheduleDatasources(schedule ? schedule.datasourceId : null);
            // Init dropdowns
            initScheduleDropdowns();

            if (schedule) {
                if (schedule.dayOfWeek) document.getElementById('scheduleDayOfWeek').value = schedule.dayOfWeek;
                if (schedule.dayOfMonth) document.getElementById('scheduleDayOfMonth').value = schedule.dayOfMonth;
                if (schedule.monthOfYear) document.getElementById('scheduleMonth').value = schedule.monthOfYear;
                if (schedule.hourOfDay != null) document.getElementById('scheduleHour').value = schedule.hourOfDay;
                if (schedule.minuteOfHour != null) document.getElementById('scheduleMinute').value = schedule.minuteOfHour;
            }

            updateScheduleFields();
            document.getElementById('scheduleModal').style.display = 'block';
        }

        function closeScheduleModal() {
            document.getElementById('scheduleModal').style.display = 'none';
        }

        function loadScheduleReports(selectedReport) {
            fetch('/reports?page=0&size=200')
                .then(res => res.json())
                .then(payload => {
                    const reports = Array.isArray(payload) ? payload : (payload.content || []);
                    const sel = document.getElementById('scheduleReportName');
                    sel.innerHTML = '<option value="">-- Select a report --</option>';
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
                    sel.innerHTML = '<option value="">-- No datasource --</option>';
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

            const dto = {
                name: document.getElementById('scheduleName').value,
                reportName: document.getElementById('scheduleReportName').value,
                format: document.getElementById('scheduleFormat').value,
                scheduleType: type,
                outputPath: document.getElementById('scheduleOutputPath').value || null,
                description: document.getElementById('scheduleDescription').value || null,
                minuteOfHour: parseInt(document.getElementById('scheduleMinute').value),
                enabled: true
            };

            const dsId = document.getElementById('scheduleDatasource').value;
            if (dsId) dto.datasourceId = parseInt(dsId);

            if (['DAILY','WEEKLY','MONTHLY','YEARLY'].includes(type)) {
                dto.hourOfDay = parseInt(document.getElementById('scheduleHour').value);
            }
            if (type === 'WEEKLY') {
                dto.dayOfWeek = parseInt(document.getElementById('scheduleDayOfWeek').value);
            }
            if (['MONTHLY','YEARLY'].includes(type)) {
                dto.dayOfMonth = parseInt(document.getElementById('scheduleDayOfMonth').value);
            }
            if (type === 'YEARLY') {
                dto.monthOfYear = parseInt(document.getElementById('scheduleMonth').value);
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
