// ========== Query Tester Functions ==========

        // Store current query results for export
        let currentQueryResults = null;

        function openQueryTesterModal(datasourceId, datasourceName) {
            document.getElementById('queryTesterDatasourceId').value = datasourceId;
            document.getElementById('queryTesterDatasourceName').value = datasourceName;
            document.getElementById('queryTesterDatasourceDisplay').textContent = datasourceName;
            document.getElementById('queryTesterTitle').textContent = 'Test Query - ' + datasourceName;

            // Reset the form
            document.getElementById('modalSqlQuery').value = '';
            document.getElementById('modalMaxRows').value = '1000';
            document.getElementById('modalQueryResults').style.display = 'none';
            document.getElementById('modalQueryError').style.display = 'none';
            document.getElementById('modalQueryTruncatedWarning').style.display = 'none';

            document.getElementById('queryTesterModal').style.display = 'block';
        }

        function closeQueryTesterModal() {
            document.getElementById('queryTesterModal').style.display = 'none';
        }

        function executeQueryFromModal() {
            const datasourceId = document.getElementById('queryTesterDatasourceId').value;
            const query = document.getElementById('modalSqlQuery').value.trim();
            const maxRows = parseInt(document.getElementById('modalMaxRows').value) || 1000;

            // Validation
            if (!datasourceId) {
                showModalQueryError('Datasource not selected');
                return;
            }
            if (!query) {
                showModalQueryError('Please enter a SQL query');
                return;
            }

            // Hide previous results/errors/warnings
            document.getElementById('modalQueryResults').style.display = 'none';
            document.getElementById('modalQueryError').style.display = 'none';
            document.getElementById('modalQueryTruncatedWarning').style.display = 'none';

            // Show loading message
            showModalQueryError('Executing query...');
            document.getElementById('modalQueryError').style.background = '#d1ecf1';
            document.getElementById('modalQueryError').style.color = '#0c5460';
            document.getElementById('modalQueryError').style.borderColor = '#bee5eb';
            document.getElementById('modalQueryError').style.display = 'block';

            // Execute query
            fetch('/api/datasources/' + datasourceId + '/query', {
                method: 'POST',
                headers: getQueryTesterHeaders({
                    'Content-Type': 'application/json'
                }),
                body: JSON.stringify({ query: query, maxRows: String(maxRows) })
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(data => {
                        throw new Error(data.message || 'Query execution failed');
                    });
                }
                return response.json();
            })
            .then(data => {
                // Hide loading message
                document.getElementById('modalQueryError').style.display = 'none';

                // Display results
                displayModalQueryResults(data);
            })
            .catch(error => {
                showModalQueryError(error.message || 'Failed to execute query');
            });
        }

        function displayModalQueryResults(data) {
            // Store results for export
            currentQueryResults = data;

            const resultsDiv = document.getElementById('modalQueryResults');
            const metaDiv = document.getElementById('modalQueryMeta');
            const thead = document.getElementById('modalQueryResultsHead');
            const tbody = document.getElementById('modalQueryResultsBody');
            const truncatedWarning = document.getElementById('modalQueryTruncatedWarning');

            // Clear previous results
            thead.innerHTML = '';
            tbody.innerHTML = '';

            // Display metadata
            metaDiv.textContent = `${data.rowCount} rows returned in ${data.executionTime}ms`;

            // Show truncation warning if applicable
            if (data.truncated) {
                document.getElementById('modalTruncatedMaxRows').textContent = data.maxRows;
                truncatedWarning.style.display = 'block';
            } else {
                truncatedWarning.style.display = 'none';
            }

            if (data.rowCount === 0) {
                tbody.innerHTML = '<tr><td colspan="100" style="text-align: center; color: #999;">No results returned</td></tr>';
                resultsDiv.style.display = 'block';
                return;
            }

            // Build table header
            const headerRow = document.createElement('tr');
            data.columns.forEach(column => {
                const th = document.createElement('th');
                th.textContent = column.label || column.name;
                th.title = column.type;
                headerRow.appendChild(th);
            });
            thead.appendChild(headerRow);

            // Build table rows
            data.rows.forEach(row => {
                const tr = document.createElement('tr');
                data.columns.forEach(column => {
                    const td = document.createElement('td');
                    const value = row[column.name];
                    td.textContent = value !== null && value !== undefined ? String(value) : 'NULL';
                    if (value === null || value === undefined) {
                        td.style.color = '#999';
                        td.style.fontStyle = 'italic';
                    }
                    tr.appendChild(td);
                });
                tbody.appendChild(tr);
            });

            resultsDiv.style.display = 'block';
        }

        function showModalQueryError(message) {
            const errorDiv = document.getElementById('modalQueryError');
            errorDiv.textContent = message;
            errorDiv.style.background = '#f8d7da';
            errorDiv.style.color = '#721c24';
            errorDiv.style.borderColor = '#f5c6cb';
            errorDiv.style.display = 'block';
            document.getElementById('modalQueryResults').style.display = 'none';
        }

        function exportQueryResultsToCSV() {
            if (!currentQueryResults || !currentQueryResults.rows || currentQueryResults.rows.length === 0) {
                alert('No data to export');
                return;
            }

            const data = currentQueryResults;
            const columns = data.columns;
            const rows = data.rows;

            // Build CSV content
            let csvContent = '';

            // Add header row
            csvContent += columns.map(col => {
                const value = col.label || col.name;
                // Escape quotes and wrap in quotes if contains comma, quote, or newline
                return value.includes(',') || value.includes('"') || value.includes('\n') 
                    ? '"' + value.replace(/"/g, '""') + '"'
                    : value;
            }).join(',') + '\n';

            // Add data rows
            rows.forEach(row => {
                const rowValues = columns.map(col => {
                    const value = row[col.name];
                    if (value === null || value === undefined) {
                        return '';
                    }
                    const strValue = String(value);
                    // Escape quotes and wrap in quotes if contains comma, quote, or newline
                    return strValue.includes(',') || strValue.includes('"') || strValue.includes('\n')
                        ? '"' + strValue.replace(/"/g, '""') + '"'
                        : strValue;
                });
                csvContent += rowValues.join(',') + '\n';
            });

            // Create blob and download
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement('a');
            const url = URL.createObjectURL(blob);

            const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
            link.setAttribute('href', url);
            link.setAttribute('download', `query_results_${timestamp}.csv`);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }

        // Drag and drop support
        const uploadArea = document.querySelector('.upload-area');

        uploadArea.addEventListener('dragover', (e) => {
            e.preventDefault();
            uploadArea.style.background = '#e8ebff';
        });

        uploadArea.addEventListener('dragleave', () => {
            uploadArea.style.background = '#f8f9ff';
        });

        uploadArea.addEventListener('drop', (e) => {
            e.preventDefault();
            uploadArea.style.background = '#f8f9ff';
            const fileInput = document.getElementById('fileInput');
            fileInput.files = e.dataTransfer.files;
            uploadFile();
        });

        // Close modal when clicking outside
        window.onclick = function(event) {
            const modal = document.getElementById('datasourceModal');
            if (event.target === modal) {
                closeDatasourceModal();
            }
        };
