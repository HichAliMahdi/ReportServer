// Debug mode (disable in production)
const DEBUG = false;

// Get CSRF token from meta tags
const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

// Store current user role
let currentUserRole = 'READ_ONLY'; // Default role
let lastActionElement = null;

// Helper function for conditional logging
function debugLog(...args) {
    if (DEBUG && console.log) {
        console.log(...args);
    }
}

// Helper function to add CSRF token to fetch headers
function getHeadersWithCSRF(additionalHeaders = {}) {
    const headers = { ...additionalHeaders };
    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }
    return headers;
}

// Fetch the current user's role
function fetchCurrentUser() {
    return fetch('/api/current-user')
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                currentUserRole = data.role;
                updateTabVisibility();
            }
            return data;
        })
        .catch(error => {
            console.error('Error fetching current user:', error);
            return { status: 'error' };
        });
}

// Update tab visibility based on user role
function updateTabVisibility() {
    const restrictedTabs = {
        'builder': ['ADMIN', 'OPERATOR'],     // Report Builder
        'datasources': ['ADMIN', 'OPERATOR'], // Datasources
        'users': ['ADMIN']                     // User Management
    };

    // Only hide nav items, not tab content
    Object.keys(restrictedTabs).forEach(tabName => {
        const navElement = document.querySelector('.nav-item[data-tab="' + tabName + '"]');
        
        const isAllowed = restrictedTabs[tabName].includes(currentUserRole);
        
        if (navElement) {
            navElement.style.display = isAllowed ? 'block' : 'none';
        }
    });

    // Reports tab nav item is always visible
    const reportsNav = document.querySelector('.nav-item[data-tab="reports"]');
    if (reportsNav) {
        reportsNav.style.display = 'block';
    }
    
    // Schedules tab is only visible to ADMIN and OPERATOR
    const schedulesNav = document.querySelector('.nav-item[data-tab="schedules"]');
    if (schedulesNav) {
        const isReadOnly = currentUserRole === 'READ_ONLY';
        schedulesNav.style.display = isReadOnly ? 'none' : 'block';
    }

    // Reports tab section visibility (explicit role-based toggling)
    const generateReportSection = document.getElementById('generateReportSection');
    const uploadReportSection = document.getElementById('uploadReportSection');
    const jrxmlTemplatesSection = document.getElementById('jrxmlTemplatesSection');
    const adminGeneratedReportsSection = document.getElementById('adminGeneratedReportsSection');
    const readOnlyReportsSection = document.getElementById('readOnlyReportsSection');

    const isReadOnly = currentUserRole === 'READ_ONLY';

    if (generateReportSection) {
        generateReportSection.style.display = isReadOnly ? 'none' : 'block';
    }
    if (uploadReportSection) {
        uploadReportSection.style.display = isReadOnly ? 'none' : 'block';
    }
    if (jrxmlTemplatesSection) {
        jrxmlTemplatesSection.style.display = isReadOnly ? 'none' : 'block';
    }
    if (adminGeneratedReportsSection) {
        adminGeneratedReportsSection.style.display = isReadOnly ? 'none' : 'block';
    }
    if (readOnlyReportsSection) {
        readOnlyReportsSection.style.display = isReadOnly ? 'block' : 'none';
    }
}

// Load data on page load
window.onload = function() {
    fetchCurrentUser().then(() => {
        // Remove loading state after user role is determined
        document.body.classList.remove('page-loading');
        loadReports();
        loadDatasources();
    });

    // Attach click event listeners to nav items
    document.querySelectorAll('.nav-item').forEach(navItem => {
        const tabName = navItem.getAttribute('data-tab');
        if (tabName) {
            navItem.addEventListener('click', function() {
                switchTab(tabName);
            });
        }
    });

    // Close modal when clicking outside
    window.onclick = function(event) {
        const modal = document.getElementById('deleteConfirmModal');
        if (event.target === modal) {
            modal.style.display = 'none';
        }
        const scheduleModal = document.getElementById('scheduleModal');
        if (event.target === scheduleModal) {
            scheduleModal.style.display = 'none';
        }
    };
};

document.addEventListener('click', function(event) {
    const actionElement = event.target.closest('button, .btn, .report-action-btn');
    if (actionElement) {
        lastActionElement = actionElement;
    }
});

// Tab switching
function switchTab(tabName) {
    // Check if user has access to this tab
    const restrictedTabs = {
        'builder': ['ADMIN', 'OPERATOR'],
        'datasources': ['ADMIN', 'OPERATOR'],
        'users': ['ADMIN']
    };

    if (restrictedTabs[tabName] && !restrictedTabs[tabName].includes(currentUserRole)) {
        showMessage('You do not have access to this section', 'error');
        return;
    }

    // Hide all tabs
    document.querySelectorAll('.tab-content').forEach(tab => {
        tab.classList.remove('active');
    });
    document.querySelectorAll('.nav-item').forEach(navItem => {
        navItem.classList.remove('active');
    });

    // Show selected tab
    if (tabName === 'users') {
        window.location.href = '/users';
        return;
    }

    const tabElement = document.getElementById(tabName + 'Tab');
    const navElement = document.querySelector('.nav-item[data-tab="' + tabName + '"]');

    if (tabElement) {
        tabElement.classList.add('active');
    }

    if (navElement) {
        navElement.classList.add('active');
    }

    // Load data for specific tabs
    if (tabName === 'builder') {
        loadBuilderDatasources();
    } else if (tabName === 'datasources') {
        loadDatasources();
    } else if (tabName === 'schedules') {
        loadSchedules();
    }
}

// Toggle datasource dropdown visibility
document.getElementById('useDatabaseCheck').addEventListener('change', function() {
    const datasourceGroup = document.getElementById('datasourceGroup');
    datasourceGroup.style.display = this.checked ? 'block' : 'none';
});

function uploadFile() {
    const fileInput = document.getElementById('fileInput');
    const file = fileInput.files[0];

    if (!file) {
        showMessage('Please select a file', 'error');
        return;
    }

    // Check if it's a .jrxml file
    if (!file.name.toLowerCase().endsWith('.jrxml')) {
        showMessage('Please select a .jrxml file', 'error');
        return;
    }

    // Check file size (max 5MB)
    const maxSize = 5 * 1024 * 1024; // 5MB in bytes
    if (file.size > maxSize) {
        showMessage('File is too large. Maximum size is 5MB.', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    // Add CSRF token
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    const headers = {};
    if (token && header) {
        headers[header] = token;
    }

    showLoading('Uploading file...');

    fetch('/upload', {
        method: 'POST',
        headers: headers,
        body: formData
    })
    .then(response => {
        if (!response.ok) {
            return response.text().then(text => {
                throw new Error(text || 'Upload failed');
            });
        }
        return response.text();
    })
    .then(data => {
        hideLoading();
        showMessage(data, 'success');
        loadReports();
        fileInput.value = '';
    })
    .catch(error => {
        hideLoading();
        showMessage('Upload error: ' + error.message, 'error');
    });
}

function loadReports() {
    loadJrxmlTemplates();
    loadGeneratedReports();
}

function loadJrxmlTemplates() {
    const list = document.getElementById('jrxmlTemplateList');
    
    if (!list) return; // Element might not exist for READ_ONLY users

    // Show loading skeletons
    list.innerHTML = `
        <div class="skeleton skeleton-card"></div>
        <div class="skeleton skeleton-card"></div>
        <div class="skeleton skeleton-card"></div>
    `;

    fetch('/reports')
    .then(response => response.json())
    .then(reports => {
        const select = document.getElementById('reportSelect');
        
        if (select) {
            // Clear existing options (keep first one)
            select.innerHTML = '<option value="">-- Select a report --</option>';
        }
        
        list.innerHTML = '';

        if (reports.length === 0) {
            list.innerHTML = `
                <div class="empty-state">
                    <div class="empty-state-icon">📋</div>
                    <h3>No JRXML Templates Yet</h3>
                    <p>Upload your first JRXML report file to get started</p>
                    <button class="btn" onclick="document.getElementById('fileInput').click()">
                        📄 Upload Template
                    </button>
                </div>
            `;
            return;
        }

        reports.forEach(report => {
            // Add to dropdown
            if (select) {
                const option = document.createElement('option');
                option.value = report;
                option.textContent = report;
                select.appendChild(option);
            }

            // Add to list with buttons
            const item = document.createElement('div');
            item.className = 'report-item';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'report-item-name';
            nameDiv.textContent = report;

            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'report-item-actions';

            const downloadLink = document.createElement('a');
            downloadLink.href = `/api/builder/download/${encodeURIComponent(report)}`;
            downloadLink.download = report;
            downloadLink.className = 'report-action-btn report-action-download';
            downloadLink.innerHTML = '📥 Download';
            downloadLink.title = 'Download JRXML template';
            downloadLink.setAttribute('aria-label', `Download ${report}`);

            const editBtn = document.createElement('button');
            editBtn.className = 'report-action-btn report-action-edit';
            editBtn.innerHTML = '✏️ Edit';
            editBtn.title = 'Edit in JRXML editor';
            editBtn.setAttribute('aria-label', `Edit ${report}`);
            editBtn.onclick = () => openJrxmlEditor(report);
            editBtn.style.display = (currentUserRole === 'ADMIN' || currentUserRole === 'OPERATOR') ? 'inline-block' : 'none';

            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'report-action-btn report-action-delete';
            deleteBtn.innerHTML = '🗑️ Delete';
            deleteBtn.title = 'Delete template';
            deleteBtn.setAttribute('aria-label', `Delete ${report}`);
            deleteBtn.onclick = () => confirmDeleteReport(report, deleteBtn);
            deleteBtn.style.display = (currentUserRole === 'ADMIN' || currentUserRole === 'OPERATOR') ? 'inline-block' : 'none';

            actionsDiv.appendChild(downloadLink);
            actionsDiv.appendChild(editBtn);
            actionsDiv.appendChild(deleteBtn);

            item.appendChild(nameDiv);
            item.appendChild(actionsDiv);
            list.appendChild(item);
        });
    })
    .catch(error => {
        list.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">⚠️</div>
                <h3>Error Loading Templates</h3>
                <p>${error.message || 'Unable to load templates. Please try again.'}</p>
                <button class="btn" onclick="loadJrxmlTemplates()">
                    🔄 Retry
                </button>
            </div>
        `;
    });
}

function loadGeneratedReports() {
    const adminList = document.getElementById('generatedReportList');
    const readOnlyList = document.getElementById('readOnlyReportList');
    const readOnlySection = document.getElementById('readOnlyReportsSection');

    if (!adminList && !readOnlyList) return;

    // Show loading skeletons
    if (adminList) {
        adminList.innerHTML = `
            <div class="skeleton skeleton-card"></div>
            <div class="skeleton skeleton-card"></div>
        `;
    }

    fetch('/api/generated-reports')
    .then(response => response.json())
    .then(reportsData => {
        if (adminList) {
            adminList.innerHTML = '';
        }
        if (readOnlyList) {
            readOnlyList.innerHTML = '';
        }

        if (!reportsData || reportsData.length === 0) {
            const emptyMsg = `
                <div class="empty-state">
                    <div class="empty-state-icon">📊</div>
                    <h3>No Generated Reports Yet</h3>
                    <p>Generate reports using the Generate Report or Report Builder features</p>
                </div>
            `;
            if (adminList) {
                adminList.innerHTML = emptyMsg;
            }
            if (readOnlyList) {
                readOnlyList.innerHTML = emptyMsg;
            }
            return;
        }

        reportsData.forEach(report => {
            // Check if it should be displayed to this user
            if (currentUserRole === 'READ_ONLY' && !report.sharedWithReadOnly) {
                return; // Skip reports not shared with READ_ONLY users
            }

            const targetList = (currentUserRole === 'READ_ONLY') ? readOnlyList : adminList;
            if (!targetList) return;

            const item = document.createElement('div');
            item.className = 'report-item';

            const nameDiv = document.createElement('div');
            nameDiv.className = 'report-item-name';
            nameDiv.textContent = report.reportName + ' (' + report.reportFormat.toUpperCase() + ')';
            nameDiv.style.fontSize = '14px';

            const infoDiv = document.createElement('div');
            infoDiv.style.fontSize = '12px';
            infoDiv.style.color = '#999';
            infoDiv.style.marginTop = '5px';
            infoDiv.textContent = `Generated by: ${report.createdBy} on ${new Date(report.createdAt).toLocaleDateString()}`;

            const actionsDiv = document.createElement('div');
            actionsDiv.className = 'report-item-actions';

            // Download button - for all users
            const downloadBtn = document.createElement('button');
            downloadBtn.className = 'report-action-btn report-action-download';
            downloadBtn.innerHTML = '📥 Download';
            downloadBtn.title = 'Download generated report';
            downloadBtn.onclick = () => downloadGeneratedReport(report.reportFileName);
            actionsDiv.appendChild(downloadBtn);

            // Share/Unshare button - only for ADMIN/OPERATOR
            if (currentUserRole === 'ADMIN' || currentUserRole === 'OPERATOR') {
                const shareBtn = document.createElement('button');
                shareBtn.className = 'report-action-btn report-action-edit';
                shareBtn.innerHTML = report.sharedWithReadOnly ? '🔓 Unshare' : '🔒 Share';
                shareBtn.title = report.sharedWithReadOnly ? 'Remove from READ_ONLY users' : 'Share with READ_ONLY users';
                shareBtn.onclick = () => toggleShareReport(report.id, !report.sharedWithReadOnly, shareBtn);
                actionsDiv.appendChild(shareBtn);

                // Delete button - only for ADMIN/OPERATOR
                const deleteBtn = document.createElement('button');
                deleteBtn.className = 'report-action-btn report-action-delete';
                deleteBtn.innerHTML = '🗑️ Delete';
                deleteBtn.title = 'Delete generated report';
                deleteBtn.onclick = () => deleteGeneratedReport(report.id, deleteBtn);
                actionsDiv.appendChild(deleteBtn);
            }

            const nameContainer = document.createElement('div');
            nameContainer.appendChild(nameDiv);
            nameContainer.appendChild(infoDiv);

            item.appendChild(nameContainer);
            item.appendChild(actionsDiv);
            targetList.appendChild(item);
        });
    })
    .catch(error => {
        const emptyMsg = `
            <div class="empty-state">
                <div class="empty-state-icon">⚠️</div>
                <h3>Error Loading Reports</h3>
                <p>${error.message || 'Unable to load reports.'}</p>
                <button class="btn" onclick="loadGeneratedReports()">
                    🔄 Retry
                </button>
            </div>
        `;
        if (adminList) {
            adminList.innerHTML = emptyMsg;
        }
    });
}

function downloadGeneratedReport(fileName) {
    const a = document.createElement('a');
    a.href = `/api/download-generated-report/${encodeURIComponent(fileName)}`;
    a.download = fileName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
}

function toggleShareReport(reportId, shouldShare, actionButton) {
    const action = shouldShare ? 'share' : 'unshare';
    const message = shouldShare ? 'Share this report with READ_ONLY users?' : 'Unshare this report from READ_ONLY users?';
    
    showConfirmationModal(message, () => {
        fetch(`/api/generated-reports/${reportId}/toggle-share`, {
            method: 'POST',
            headers: getHeadersWithCSRF({
                'Content-Type': 'application/json'
            }),
            body: JSON.stringify({ share: shouldShare })
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                showMessage(data.message, 'success', actionButton);
                loadGeneratedReports();
            } else {
                showMessage(data.message, 'error', actionButton);
            }
        })
        .catch(error => {
            showMessage('Error: ' + error.message, 'error', actionButton);
        });
    });
}

function deleteGeneratedReport(reportId, actionButton) {
    showConfirmationModal('Delete this generated report? This action cannot be undone.', () => {
        fetch(`/api/generated-reports/${reportId}`, {
            method: 'DELETE',
            headers: getHeadersWithCSRF({
                'Content-Type': 'application/json'
            })
        })
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                showMessage(data.message, 'success', actionButton);
                loadGeneratedReports();
            } else {
                showMessage(data.message, 'error', actionButton);
            }
        })
        .catch(error => {
            showMessage('Error: ' + error.message, 'error', actionButton);
        });
    });
}

function confirmDeleteReport(reportName, actionButton) {
    const modal = document.getElementById('deleteConfirmModal');
    const modalMessage = document.getElementById('deleteConfirmMessage');
    const confirmBtn = document.getElementById('deleteConfirmBtn');

    modalMessage.textContent = `Are you sure you want to delete "${reportName}"?`;
    modal.style.display = 'block';

    // Remove old event listener and add new one
    confirmBtn.onclick = () => {
        modal.style.display = 'none';
        deleteReport(reportName, actionButton);
    };
}

function closeDeleteConfirm() {
    document.getElementById('deleteConfirmModal').style.display = 'none';
}

function deleteReport(reportName, actionButton) {
    const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    const headers = {
        'Content-Type': 'application/json'
    };

    if (csrfToken && csrfHeader) {
        headers[csrfHeader] = csrfToken;
    }

    fetch(`/reports/${encodeURIComponent(reportName)}`, {
        method: 'DELETE',
        headers: headers
    })
    .then(response => {
        return response.text().then(message => ({
            ok: response.ok,
            message: message
        }));
    })
    .then(result => {
        showMessage(result.message, result.ok ? 'success' : 'error', actionButton);
        if (result.ok) {
            loadReports();
        }
    })
    .catch(error => {
        showMessage('Error deleting report: ' + error.message, 'error', actionButton);
    });
}

function downloadReportForReadOnly(reportName) {
    try {
        showLoading('Generating report...');
        
        const csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
        const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

        const formData = new FormData();
        formData.append('reportName', reportName);
        formData.append('format', 'pdf');

        const headers = {};
        if (csrfToken && csrfHeader) {
            headers[csrfHeader] = csrfToken;
        }

        fetch('/download-report', {
            method: 'POST',
            headers: headers,
            body: formData
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => {
                    throw new Error(text || 'Download failed');
                });
            }
            return response.blob();
        })
        .then(blob => {
            hideLoading();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = reportName.replace('.jrxml', '.pdf');
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            showMessage('Report downloaded successfully', 'success');
        })
        .catch(error => {
            hideLoading();
            showMessage('Error downloading report: ' + error.message, 'error');
        });
    } catch (error) {
        hideLoading();
        showMessage('Error: ' + error.message, 'error');
    }
}


function loadReportParameters(reportName) {
    const parametersSection = document.getElementById('parametersSection');
    const parametersContainer = document.getElementById('parametersContainer');

    if (!reportName) {
        parametersSection.style.display = 'none';
        parametersContainer.innerHTML = '';
        return;
    }

    fetch(`/api/jrxml/parameters/${encodeURIComponent(reportName)}`)
    .then(response => response.json())
    .then(data => {
        if (!data.success || data.parameters.length === 0) {
            parametersSection.style.display = 'none';
            return;
        }

        parametersContainer.innerHTML = '';
        parametersSection.style.display = 'block';

        data.parameters.forEach(param => {
            const formGroup = document.createElement('div');
            formGroup.className = 'form-group';

            const label = document.createElement('label');
            label.textContent = param.name + ' (' + param.class.split('.').pop() + ')';
            label.setAttribute('for', 'param_' + param.name);

            let input;
            if (param.inputType === 'checkbox') {
                input = document.createElement('input');
                input.type = 'checkbox';
                input.id = 'param_' + param.name;
                input.name = param.name;
                input.setAttribute('data-param-type', param.class);
            } else if (param.inputType === 'date') {
                input = document.createElement('input');
                input.type = 'date';
                input.id = 'param_' + param.name;
                input.name = param.name;
                input.setAttribute('data-param-type', param.class);
            } else if (param.inputType === 'number') {
                input = document.createElement('input');
                input.type = 'number';
                input.step = 'any';
                input.id = 'param_' + param.name;
                input.name = param.name;
                input.setAttribute('data-param-type', param.class);
            } else {
                input = document.createElement('input');
                input.type = 'text';
                input.id = 'param_' + param.name;
                input.name = param.name;
                input.setAttribute('data-param-type', param.class);
            }

            formGroup.appendChild(label);
            formGroup.appendChild(input);
            parametersContainer.appendChild(formGroup);
        });
    })
    .catch(error => {
        console.error('Error loading report parameters:', error);
        parametersSection.style.display = 'none';
    });
}

// Add event listener to load parameters when report is selected
document.getElementById('reportSelect').addEventListener('change', function() {
    loadReportParameters(this.value);
});

function loadDatasources() {
    const tableBody = document.getElementById('datasourceTableBody');
    const select = document.getElementById('datasourceSelect');

    // Show loading state
    tableBody.innerHTML = '<tr><td colspan="4"><div class="skeleton skeleton-text"></div><div class="skeleton skeleton-text"></div></td></tr>';

    fetch('/api/datasources')
    .then(response => {
        if (!response.ok) throw new Error('Failed to load datasources');
        return response.json();
    })
    .then(datasources => {
        // Clear existing
        tableBody.innerHTML = '';
        select.innerHTML = '<option value="">-- Use default datasource --</option>';

        if (datasources.length === 0) {
            tableBody.innerHTML = `
                <tr><td colspan="4">
                    <div class="empty-state" style="padding: 40px 20px;">
                        <div class="empty-state-icon">\ud83d\uddc4\ufe0f</div>
                        <h3>No Datasources Configured</h3>
                        <p>Create your first datasource to connect to databases</p>
                        <button class="btn" onclick="openDatasourceModal()" style="margin-top: 15px;">
                            \u2795 Add Datasource
                        </button>
                    </div>
                </td></tr>
            `;
            return;
        }

        datasources.forEach(ds => {
            // Add to table
            const row = document.createElement('tr');

            // Create cells with text content (prevents XSS)
            const nameCell = document.createElement('td');
            nameCell.textContent = ds.name;

            const typeCell = document.createElement('td');
            const typeValue = ds.type || 'JDBC';
            typeCell.textContent = typeValue;

            const detailsCell = document.createElement('td');
            if (typeValue === 'JDBC' || typeValue === 'HIBERNATE') {
                detailsCell.textContent = `${ds.url || ''} (${ds.username || ''})`;
            } else if (typeValue === 'MONGODB') {
                detailsCell.textContent = ds.url || 'MongoDB connection';
            } else if (typeValue === 'REST_API') {
                detailsCell.textContent = ds.url || 'REST API endpoint';
            } else if (typeValue === 'CSV' || typeValue === 'XML' || typeValue === 'JSON') {
                detailsCell.textContent = ds.filePath ? `File: ${ds.filePath.split('/').pop()}` : 'No file';
            } else if (typeValue === 'EMPTY') {
                detailsCell.textContent = 'Empty datasource';
            } else if (typeValue === 'COLLECTION') {
                detailsCell.textContent = 'JavaBeans/POJOs/Collections';
            }

            const actionsCell = document.createElement('td');
            const editBtn = document.createElement('button');
            editBtn.className = 'btn-small';
            editBtn.textContent = 'Edit';
            editBtn.title = 'Edit datasource';
            editBtn.setAttribute('aria-label', `Edit ${ds.name}`);
            editBtn.onclick = () => editDatasource(ds.id);

            const deleteBtn = document.createElement('button');
            deleteBtn.className = 'btn-small btn-danger';
            deleteBtn.textContent = 'Delete';
            deleteBtn.title = 'Delete datasource';
            deleteBtn.setAttribute('aria-label', `Delete ${ds.name}`);
            deleteBtn.onclick = () => deleteDatasource(ds.id);

            actionsCell.appendChild(editBtn);
            actionsCell.appendChild(deleteBtn);

            // Add Test Query button for JDBC datasources
            if (typeValue === 'JDBC') {
                const testQueryBtn = document.createElement('button');
                testQueryBtn.className = 'btn-small';
                testQueryBtn.textContent = 'Test Query';
                testQueryBtn.style.background = '#17a2b8';
                testQueryBtn.title = 'Test SQL query';
                testQueryBtn.setAttribute('aria-label', `Test query on ${ds.name}`);
                testQueryBtn.onclick = () => openQueryTesterModal(ds.id, ds.name);
                actionsCell.appendChild(testQueryBtn);
            }

            row.appendChild(nameCell);
            row.appendChild(typeCell);
            row.appendChild(detailsCell);
            row.appendChild(actionsCell);

            tableBody.appendChild(row);

            // Add to select
            const option = document.createElement('option');
            option.value = ds.id;
            option.textContent = ds.name;
            select.appendChild(option);
        });
    })
    .catch(error => {
        tableBody.innerHTML = `
            <tr><td colspan="4">
                <div class="empty-state" style="padding: 30px 20px;">
                    <div class="empty-state-icon">\u26a0\ufe0f</div>
                    <h3>Error Loading Datasources</h3>
                    <p>${error.message || 'Unable to load datasources. Please try again.'}</p>
                    <button class="btn" onclick="loadDatasources()" style="margin-top: 15px;">
                        \ud83d\udd04 Retry
                    </button>
                </div>
            </td></tr>
        `;
    });
}

document.getElementById('generateForm').onsubmit = function(e) {
    e.preventDefault();

    const formData = new FormData(this);
    const params = new URLSearchParams();

    const reportName = formData.get('reportName');
    if (!reportName) {
        showGenerateMessage('Please select a report', 'error');
        return;
    }

    params.append('reportName', reportName);
    params.append('format', formData.get('format'));

    const useDatabase = document.getElementById('useDatabaseCheck').checked;
    params.append('useDatabase', useDatabase);

    if (useDatabase) {
        const datasourceId = formData.get('datasourceId');
        if (!datasourceId) {
            showGenerateMessage('Please select a datasource when using database connection', 'error');
            return;
        }
        params.append('datasourceId', datasourceId);
    }

    // Collect report parameters
    const parametersContainer = document.getElementById('parametersContainer');
    if (parametersContainer && parametersContainer.children.length > 0) {
        const paramInputs = parametersContainer.querySelectorAll('input, select, textarea');
        paramInputs.forEach(input => {
            if (input.type === 'checkbox') {
                params.append(input.name, input.checked ? 'true' : 'false');
            } else if (input.value) {
                params.append(input.name, input.value);
            }
        });
    }

    // Add CSRF token
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

    const headers = {};
    if (token && header) {
        headers[header] = token;
    }

    showLoading('Generating report...');
    const submitBtn = e.target.querySelector('button[type="submit"]');
    if (submitBtn) submitBtn.disabled = true;

    fetch('/generate?' + params.toString(), {
        method: 'POST',
        headers: headers
    })
    .then(response => {
        // Now expecting JSON response
        return response.json().then(data => ({
            ok: response.ok,
            data: data
        }));
    })
    .then(({ok, data}) => {
        hideLoading();
        if (submitBtn) submitBtn.disabled = false;
        
        if (!ok || data.status !== 'success') {
            throw new Error(data.message || 'Generation failed');
        }
        
        // Report generated successfully and saved to database
        showGenerateMessage('✓ Report generated successfully!', 'success');
        
        // Reload the generated reports list to show the new report
        setTimeout(() => {
            loadGeneratedReports();
        }, 500);
    })
    .catch(error => {
        hideLoading();
        if (submitBtn) submitBtn.disabled = false;
        showGenerateMessage('✗ Generation failed: ' + error.message, 'error');
        console.error('Report generation error:', error);
    });
};

// Datasource Modal Functions
function toggleDatasourceFields() {
    const type = document.getElementById('dsType').value;
    const jdbcFields = document.getElementById('jdbcFields');
    const fileFields = document.getElementById('fileFields');
    const emptyCollectionInfo = document.getElementById('emptyCollectionInfo');
    const configurationGroup = document.getElementById('configurationGroup');
    const testBtn = document.getElementById('testBtn');
    const emptyCollectionText = document.getElementById('emptyCollectionText');
    const fileHelpText = document.getElementById('fileHelpText');
    const configHelpText = document.getElementById('configHelpText');

    // Reset all fields
    jdbcFields.style.display = 'none';
    fileFields.style.display = 'none';
    emptyCollectionInfo.style.display = 'none';
    configurationGroup.style.display = 'none';

    // Clear required attributes
    document.getElementById('dsUrl').required = false;
    document.getElementById('dsUsername').required = false;
    document.getElementById('dsPassword').required = false;
    document.getElementById('dsDriver').required = false;
    document.getElementById('dsFile').required = false;

    if (type === 'JDBC' || type === 'HIBERNATE') {
        jdbcFields.style.display = 'block';
        document.getElementById('dsUrl').required = true;
        document.getElementById('dsUsername').required = true;
        document.getElementById('dsDriver').required = true;
        testBtn.style.display = 'inline-block';
        if (type === 'HIBERNATE') {
            document.getElementById('dsUrl').placeholder = 'jdbc:mysql://localhost:3306/mydb (Hibernate will use JDBC)';
        } else {
            document.getElementById('dsUrl').placeholder = 'jdbc:mysql://localhost:3306/mydb';
        }
    } else if (type === 'MONGODB') {
        jdbcFields.style.display = 'block';
        document.getElementById('dsUrl').required = true;
        document.getElementById('dsUrl').placeholder = 'mongodb://localhost:27017';
        document.getElementById('dsDriver').parentElement.style.display = 'none';
        testBtn.style.display = 'inline-block';
    } else if (type === 'REST_API') {
        jdbcFields.style.display = 'block';
        document.getElementById('dsUrl').required = true;
        document.getElementById('dsUrl').placeholder = 'https://api.example.com/data';
        document.getElementById('dsDriver').parentElement.style.display = 'none';
        configurationGroup.style.display = 'block';
        document.getElementById('dsConfiguration').placeholder = '$.data[*] or /root/item';
        configHelpText.innerHTML = 'JSONPath for JSON APIs (e.g., $.data[*]) or XPath for XML APIs (e.g., /root/item)';
        testBtn.style.display = 'inline-block';
    } else if (type === 'CSV' || type === 'XML' || type === 'JSON') {
        fileFields.style.display = 'block';
        testBtn.style.display = 'inline-block';

        // Update file help text based on type
        fileHelpText.textContent = `Upload a ${type} file for this datasource`;

        if (type === 'XML' || type === 'JSON') {
            configurationGroup.style.display = 'block';
            if (type === 'XML') {
                document.getElementById('dsConfiguration').placeholder = '/root/items/item';
                configHelpText.innerHTML = 'XPath expression to select nodes (e.g., /root/items/item)';
            } else {
                document.getElementById('dsConfiguration').placeholder = '$.data.items[*]';
                configHelpText.innerHTML = 'JSONPath expression to select data (e.g., $.data.items[*])';
            }
        }
    } else if (type === 'EMPTY') {
        emptyCollectionInfo.style.display = 'block';
        emptyCollectionText.textContent = 'Empty datasource - used for reports that don\'t require external data or use subreports/scriptlets.';
        testBtn.style.display = 'none';
    } else if (type === 'COLLECTION') {
        emptyCollectionInfo.style.display = 'block';
        emptyCollectionText.textContent = 'Collection datasource - JavaBeans, POJOs, EJBs, or Java collections (List, Set, etc.) passed programmatically.';
        testBtn.style.display = 'none';
    }

    // Show driver field for JDBC and Hibernate
    if (type === 'JDBC' || type === 'HIBERNATE') {
        document.getElementById('dsDriver').parentElement.style.display = 'block';
    }
}

function openDatasourceModal(datasourceId = null) {
    const modal = document.getElementById('datasourceModal');
    const form = document.getElementById('datasourceForm');
    const title = document.getElementById('modalTitle');
    const passwordField = document.getElementById('dsPassword');

    form.reset();
    document.getElementById('datasourceMessage').innerHTML = '';

    if (datasourceId) {
        title.textContent = 'Edit Datasource';
        // Load datasource data (password will not be returned from API)
        fetch('/api/datasources/' + datasourceId)
        .then(response => response.json())
        .then(ds => {
            document.getElementById('datasourceId').value = ds.id;
            document.getElementById('dsName').value = ds.name;
            document.getElementById('dsType').value = ds.type || 'JDBC';
            document.getElementById('dsUrl').value = ds.url || '';
            document.getElementById('dsUsername').value = ds.username || '';
            document.getElementById('dsDriver').value = ds.driverClassName || '';
            document.getElementById('dsConfiguration').value = ds.configuration || '';

            // Handle file path for file-based datasources
            if (ds.filePath) {
                // Create a note showing current file
                const fileNote = document.createElement('small');
                fileNote.style.color = '#667eea';
                fileNote.style.display = 'block';
                fileNote.style.marginTop = '5px';
                fileNote.textContent = `Current file: ${ds.filePath.split('/').pop()}`;
                fileNote.id = 'currentFileNote';

                // Remove any existing note
                const existingNote = document.getElementById('currentFileNote');
                if (existingNote) existingNote.remove();

                // Add note after file input
                const fileInput = document.getElementById('dsFile');
                fileInput.parentElement.insertBefore(fileNote, fileInput.nextSibling);
            }

            // Password is not set - user must enter it again if they want to change it
            passwordField.placeholder = "Leave blank to keep current password";
            passwordField.required = false;
            passwordField.value = ''; // Ensure password is empty for security

            toggleDatasourceFields();

            // Show modal only after data is loaded
            modal.style.display = 'block';
        })
        .catch(error => {
            console.error('Error loading datasource:', error);
            alert('Failed to load datasource data');
        });
    } else {
        title.textContent = 'Add Datasource';
        document.getElementById('datasourceId').value = '';
        document.getElementById('dsType').value = 'JDBC';
        passwordField.placeholder = "";
        passwordField.required = true;
        toggleDatasourceFields();

        // Show modal immediately for new datasource
        modal.style.display = 'block';
    }
}

function closeDatasourceModal() {
    document.getElementById('datasourceModal').style.display = 'none';
}

function editDatasource(id) {
    openDatasourceModal(id);
}

function deleteDatasource(id) {
    if (!confirm('Are you sure you want to delete this datasource?')) return;

    fetch('/api/datasources/' + id, {
        method: 'DELETE',
        headers: getHeadersWithCSRF()
    })
    .then(response => response.json())
    .then(data => {
        if (data.status === 'success') {
            showMessage(data.message, 'success');
            loadDatasources();
        } else {
            showMessage(data.message, 'error');
        }
    })
    .catch(error => {
        showMessage('Failed to delete datasource: ' + error, 'error');
    });
}

document.getElementById('datasourceForm').onsubmit = async function(e) {
    e.preventDefault();

    const id = document.getElementById('datasourceId').value;
    const type = document.getElementById('dsType').value;
    const fileInput = document.getElementById('dsFile');

    // Handle file upload for CSV/XML/JSON types
    if ((type === 'CSV' || type === 'XML' || type === 'JSON') && fileInput.files.length > 0) {
        const formData = new FormData();
        formData.append('file', fileInput.files[0]);

        try {
            const uploadResponse = await fetch('/api/datasources/upload-file', {
                method: 'POST',
                headers: getHeadersWithCSRF(),
                body: formData
            });

            if (!uploadResponse.ok) {
                throw new Error('File upload failed');
            }

            const uploadData = await uploadResponse.json();
            if (uploadData.status !== 'success') {
                showDatasourceMessage('File upload failed: ' + uploadData.message, 'error');
                return;
            }

            // Continue with datasource creation using uploaded file path
            await saveDatasourceWithFilePath(id, type, uploadData.filePath);
        } catch (error) {
            showDatasourceMessage('Failed to upload file: ' + error, 'error');
            return;
        }
    } else {
        // Save datasource without file upload
        await saveDatasourceWithFilePath(id, type, null);
    }
};

async function saveDatasourceWithFilePath(id, type, filePath) {
    const formData = {
        type: type,
        name: document.getElementById('dsName').value
    };

    // Add type-specific fields
    if (type === 'JDBC' || type === 'HIBERNATE') {
        formData.url = document.getElementById('dsUrl').value;
        formData.username = document.getElementById('dsUsername').value;
        formData.password = document.getElementById('dsPassword').value;
        formData.driverClassName = document.getElementById('dsDriver').value;
    } else if (type === 'MONGODB' || type === 'REST_API') {
        formData.url = document.getElementById('dsUrl').value;
        formData.username = document.getElementById('dsUsername').value;
        formData.password = document.getElementById('dsPassword').value;
        const config = document.getElementById('dsConfiguration').value;
        if (config) {
            formData.configuration = config;
        }
    } else if (type === 'CSV' || type === 'XML' || type === 'JSON') {
        if (filePath) {
            formData.filePath = filePath;
        }
        const config = document.getElementById('dsConfiguration').value;
        if (config) {
            formData.configuration = config;
        }
    }
    // EMPTY and COLLECTION types need no additional fields

    const url = id ? '/api/datasources/' + id : '/api/datasources';
    const method = id ? 'PUT' : 'POST';

    try {
        const response = await fetch(url, {
            method: method,
            headers: getHeadersWithCSRF({
                'Content-Type': 'application/json'
            }),
            body: JSON.stringify(formData)
        });

        const data = await response.json();
        if (data.status === 'success') {
            showDatasourceMessage(data.message, 'success');
            setTimeout(() => {
                closeDatasourceModal();
                loadDatasources();
            }, 1500);
        } else {
            showDatasourceMessage(data.message, 'error');
        }
    } catch (error) {
        showDatasourceMessage('Failed to save datasource: ' + error, 'error');
    }
}

function testDatasourceConnection() {
    const id = document.getElementById('datasourceId').value;
    const type = document.getElementById('dsType').value;
    const formData = {
        type: type,
        name: document.getElementById('dsName').value || 'test'
    };

    if (type === 'JDBC' || type === 'HIBERNATE') {
        formData.url = document.getElementById('dsUrl').value;
        formData.username = document.getElementById('dsUsername').value;
        formData.password = document.getElementById('dsPassword').value;
        formData.driverClassName = document.getElementById('dsDriver').value;
    } else if (type === 'MONGODB' || type === 'REST_API') {
        formData.url = document.getElementById('dsUrl').value;
        formData.username = document.getElementById('dsUsername').value;
        formData.password = document.getElementById('dsPassword').value;
        formData.configuration = document.getElementById('dsConfiguration').value;
    } else if (type === 'CSV' || type === 'XML' || type === 'JSON') {
        // For file types, test requires file upload first
        showDatasourceMessage('For file-based datasources, save the datasource first to test it.', 'info');
        return;
    }

    // Include ID for existing datasources so backend can retrieve stored password if needed
    if (id) {
        formData.id = parseInt(id);
    }

    fetch('/api/datasources/test', {
        method: 'POST',
        headers: getHeadersWithCSRF({
            'Content-Type': 'application/json'
        }),
        body: JSON.stringify(formData)
    })
    .then(response => response.json())
    .then(data => {
        if (data.status === 'success') {
            showDatasourceMessage('✓ ' + data.message, 'success');
        } else {
            showDatasourceMessage('✗ ' + data.message, 'error');
        }
    })
    .catch(error => {
        showDatasourceMessage('Test failed: ' + error, 'error');
    });
}

function showMessage(text, type, targetElement = null) {
    const preferredTarget = targetElement || lastActionElement;

    if (preferredTarget && showInlineActionMessage(preferredTarget, text, type)) {
        return;
    }

    const msg = document.getElementById('message');
    if (!msg) return;

    msg.textContent = text;
    msg.className = type;
    msg.style.display = 'block';
    setTimeout(() => msg.style.display = 'none', 5000);
}

function showInlineActionMessage(targetElement, text, type) {
    if (!targetElement) return false;

    let container = targetElement.closest('.report-item');
    if (!container) container = targetElement.closest('form');
    if (!container) container = targetElement.closest('.card');
    if (!container) container = targetElement.parentElement;
    if (!container) return false;

    let msg = container.querySelector('.inline-action-message');
    if (!msg) {
        msg = document.createElement('div');
        msg.className = 'inline-action-message';
        container.appendChild(msg);
    }

    msg.textContent = text;
    msg.className = `inline-action-message ${type}`;
    msg.style.display = 'block';

    if (msg.hideTimeout) {
        clearTimeout(msg.hideTimeout);
    }
    msg.hideTimeout = setTimeout(() => {
        msg.style.display = 'none';
    }, 5000);

    return true;
}

function showGenerateMessage(text, type) {
    const msg = document.getElementById('generateMessage');
    if (!msg) return; // Element doesn't exist on all pages
    msg.textContent = text;
    msg.className = type;
    msg.style.display = 'block';
    setTimeout(() => msg.style.display = 'none', 5000);
}

function showDatasourceMessage(text, type) {
    const msg = document.getElementById('datasourceMessage');
    msg.textContent = text;
    msg.className = type;
    msg.style.display = 'block';
}

// Loading overlay utilities
function showLoading(message = 'Processing...') {
    const overlay = document.getElementById('loadingOverlay');
    const msgElement = document.getElementById('loadingMessage');
    if (msgElement) msgElement.textContent = message;
    if (overlay) overlay.style.display = 'flex';
}

function hideLoading() {
    const overlay = document.getElementById('loadingOverlay');
    if (overlay) overlay.style.display = 'none';
}

function showConfirmationModal(message, onConfirm) {
    const modal = document.getElementById('confirmationModal');
    const messageElement = document.getElementById('confirmationMessage');
    const okBtn = document.getElementById('confirmationOkBtn');
    const cancelBtn = document.getElementById('confirmationCancelBtn');
    
    messageElement.textContent = message;
    modal.style.display = 'block';
    
    // Remove old event listeners by cloning nodes
    const newOkBtn = okBtn.cloneNode(true);
    const newCancelBtn = cancelBtn.cloneNode(true);
    okBtn.parentNode.replaceChild(newOkBtn, okBtn);
    cancelBtn.parentNode.replaceChild(newCancelBtn, cancelBtn);
    
    // Add new event listeners
    document.getElementById('confirmationOkBtn').onclick = () => {
        modal.style.display = 'none';
        onConfirm();
    };
    
    document.getElementById('confirmationCancelBtn').onclick = () => {
        modal.style.display = 'none';
    };
}
