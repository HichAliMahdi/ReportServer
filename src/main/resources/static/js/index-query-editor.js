// ========== Query Editor Functions ==========

// Get CSRF tokens from meta tags
const queryEditorCsrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
const queryEditorCsrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

function getQueryEditorHeaders(additionalHeaders = {}) {
    const headers = { ...additionalHeaders };
    if (queryEditorCsrfToken && queryEditorCsrfHeader) {
        headers[queryEditorCsrfHeader] = queryEditorCsrfToken;
    }
    return headers;
}

        function openQueryEditor() {
            const queryTextarea = document.getElementById('customSqlQuery');
            if (visualBuilder.customQuery) {
                queryTextarea.value = visualBuilder.customQuery;
            } else if (visualBuilder.tableName) {
                queryTextarea.value = `SELECT * FROM ${visualBuilder.tableName}`;
            } else {
                queryTextarea.value = '';
            }

            document.getElementById('queryEditorModal').style.display = 'block';
        }

        function closeQueryEditor() {
            document.getElementById('queryEditorModal').style.display = 'none';
        }

        function applyCustomQuery() {
            const query = document.getElementById('customSqlQuery').value.trim();
            visualBuilder.customQuery = query;
            closeQueryEditor();
            showMessage('Custom query applied', 'success');
        }

        // ========== Generate Report from Visual Design ==========

        function generateFromVisualDesign() {
            const reportName = document.getElementById('visualReportName').value.trim();

            if (!reportName) {
                showMessage('Please enter a report name', 'error');
                document.getElementById('visualReportName').focus();
                return;
            }

            if (!visualBuilder.datasourceId) {
                showMessage('Please select a datasource first', 'error');
                return;
            }

            if (visualBuilder.elements.length === 0) {
                showMessage('Please add at least one element to your report', 'error');
                return;
            }

            // Build SQL query
            let sqlQuery = visualBuilder.customQuery;
            if (!sqlQuery && visualBuilder.tableName) {
                sqlQuery = `SELECT * FROM ${visualBuilder.tableName}`;
            }

            if (!sqlQuery && visualBuilder.elements.some(el => el.type === 'field')) {
                showMessage('Please select a table or provide a custom query for database fields', 'error');
                return;
            }

            const designData = {
                reportName: reportName,
                elements: visualBuilder.elements,
                pageSettings: visualBuilder.pageSettings,
                datasourceId: visualBuilder.datasourceId,
                sqlQuery: sqlQuery,
                fields: visualBuilder.availableFields
            };

            showLoading('Generating visual report...');

            fetch('/api/builder/visual/generate', {
                method: 'POST',
                headers: getQueryEditorHeaders({
                    'Content-Type': 'application/json'
                }),
                body: JSON.stringify(designData)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(data => {
                        throw new Error(data.message || 'Generation failed');
                    });
                }
                return response.json();
            })
            .then(data => {
                hideLoading();
                if (data.success) {
                    showMessage('✓ ' + data.message, 'success');
                    // Reload reports list
                    if (typeof loadReports === 'function') {
                        setTimeout(loadReports, 1000);
                    }
                } else {
                    showMessage('✗ ' + data.message, 'error');
                }
            })
            .catch(error => {
                hideLoading();
                showMessage('✗ Error generating report: ' + error.message, 'error');
            });
        }
