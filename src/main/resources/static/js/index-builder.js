// ========== Report Builder Functions ==========

        function loadBuilderDatasources() {
            fetch('/api/datasources')
                .then(response => response.json())
                .then(datasources => {
                    const select = document.getElementById('builderDatasource');
                    select.innerHTML = '<option value="">-- Select a datasource --</option>';
                    datasources.forEach(ds => {
                        const option = document.createElement('option');
                        option.value = ds.id;
                        option.textContent = ds.name;
                        select.appendChild(option);
                    });
                })
                .catch(error => {
                    console.error('Error loading datasources:', error);
                });
        }

        // Parameter Management
        let reportParameters = [];

        function openParameterModal(editIndex = null) {
            const modal = document.getElementById('parameterModal');
            const title = document.getElementById('parameterModalTitle');
            const form = document.getElementById('parameterForm');

            form.reset();
            document.getElementById('parameterEditIndex').value = '';

            if (editIndex !== null) {
                title.textContent = 'Edit Parameter';
                document.getElementById('parameterEditIndex').value = editIndex;

                const parameter = reportParameters[editIndex];
                document.getElementById('paramName').value = parameter.name;
                document.getElementById('paramClass').value = parameter.javaClass;
                document.getElementById('paramDefaultValue').value = parameter.defaultValueExpression || '';
                document.getElementById('paramDescription').value = parameter.description || '';
            } else {
                title.textContent = 'Add Parameter';
            }

            modal.style.display = 'block';
        }

        function closeParameterModal() {
            document.getElementById('parameterModal').style.display = 'none';
        }

        function saveParameter(event) {
            event.preventDefault();

            const editIndex = document.getElementById('parameterEditIndex').value;
            const parameter = {
                name: document.getElementById('paramName').value.trim(),
                javaClass: document.getElementById('paramClass').value,
                defaultValueExpression: document.getElementById('paramDefaultValue').value.trim(),
                description: document.getElementById('paramDescription').value.trim()
            };

            // Validate parameter name uniqueness (except when editing the same parameter)
            const nameExists = reportParameters.some((p, index) => 
                p.name === parameter.name && index != editIndex
            );

            if (nameExists) {
                alert('A parameter with this name already exists. Please use a unique name.');
                return;
            }

            if (editIndex !== '') {
                reportParameters[parseInt(editIndex)] = parameter;
            } else {
                reportParameters.push(parameter);
            }

            displayParameters();
            closeParameterModal();
        }

        function displayParameters() {
            const container = document.getElementById('parametersList');

            if (reportParameters.length === 0) {
                container.innerHTML = '<p style="color: #999; text-align: center; margin: 20px 0;">No parameters added yet</p>';
                return;
            }

            let html = '<table style="width: 100%; border-collapse: collapse;">';
            html += '<thead><tr style="background: #f8f9fa; border-bottom: 2px solid #dee2e6;">';
            html += '<th style="padding: 8px; text-align: left;">Name</th>';
            html += '<th style="padding: 8px; text-align: left;">Type</th>';
            html += '<th style="padding: 8px; text-align: left;">Default Value</th>';
            html += '<th style="padding: 8px; text-align: center;">Actions</th>';
            html += '</tr></thead><tbody>';

            reportParameters.forEach((parameter, index) => {
                html += '<tr style="border-bottom: 1px solid #dee2e6;">';
                html += `<td style="padding: 8px;"><strong>$P{${parameter.name}}</strong></td>`;
                html += `<td style="padding: 8px; font-size: 12px;">${parameter.javaClass.split('.').pop()}</td>`;
                html += `<td style="padding: 8px; font-family: monospace; font-size: 12px;">${parameter.defaultValueExpression || '<em>none</em>'}</td>`;
                html += `<td style="padding: 8px; text-align: center;">`;
                html += `<button type="button" onclick="openParameterModal(${index})" style="padding: 4px 8px; margin-right: 5px; cursor: pointer; background: #ffc107; border: none; border-radius: 4px;">✏️ Edit</button>`;
                html += `<button type="button" onclick="deleteParameter(${index})" style="padding: 4px 8px; cursor: pointer; background: #dc3545; color: white; border: none; border-radius: 4px;">🗑️ Delete</button>`;
                html += `</td></tr>`;
            });

            html += '</tbody></table>';
            container.innerHTML = html;
        }

        function deleteParameter(index) {
            if (confirm('Are you sure you want to delete this parameter?')) {
                reportParameters.splice(index, 1);
                displayParameters();
            }
        }

        // Variable Management
        let reportVariables = [];

        function openVariableModal(editIndex = null) {
            const modal = document.getElementById('variableModal');
            const title = document.getElementById('variableModalTitle');
            const form = document.getElementById('variableForm');

            form.reset();
            document.getElementById('variableEditIndex').value = '';

            if (editIndex !== null) {
                title.textContent = 'Edit Variable';
                document.getElementById('variableEditIndex').value = editIndex;

                const variable = reportVariables[editIndex];
                document.getElementById('varName').value = variable.name;
                document.getElementById('varClass').value = variable.javaClass;
                document.getElementById('varCalculation').value = variable.calculation;
                document.getElementById('varExpression').value = variable.expression;
                document.getElementById('varInitialValue').value = variable.initialValue || '';
                document.getElementById('varResetType').value = variable.resetType;
                document.getElementById('varResetGroup').value = variable.resetGroup || '';
                document.getElementById('varIncrementType').value = variable.incrementType;
                document.getElementById('varIncrementGroup').value = variable.incrementGroup || '';

                updateVariableFormFields();
            } else {
                title.textContent = 'Add Variable';
            }

            modal.style.display = 'block';
        }

        function closeVariableModal() {
            document.getElementById('variableModal').style.display = 'none';
        }

        function updateVariableFormFields() {
            const resetType = document.getElementById('varResetType').value;
            const incrementType = document.getElementById('varIncrementType').value;

            document.getElementById('varResetGroupDiv').style.display = 
                resetType === 'Group' ? 'block' : 'none';
            document.getElementById('varIncrementGroupDiv').style.display = 
                incrementType === 'Group' ? 'block' : 'none';
        }

        function saveVariable(event) {
            event.preventDefault();

            const editIndex = document.getElementById('variableEditIndex').value;
            const variable = {
                name: document.getElementById('varName').value.trim(),
                javaClass: document.getElementById('varClass').value,
                calculation: document.getElementById('varCalculation').value,
                expression: document.getElementById('varExpression').value.trim(),
                initialValue: document.getElementById('varInitialValue').value.trim(),
                resetType: document.getElementById('varResetType').value,
                resetGroup: document.getElementById('varResetGroup').value.trim(),
                incrementType: document.getElementById('varIncrementType').value,
                incrementGroup: document.getElementById('varIncrementGroup').value.trim()
            };

            // Validate variable name uniqueness (except when editing the same variable)
            const nameExists = reportVariables.some((v, index) => 
                v.name === variable.name && index != editIndex
            );

            if (nameExists) {
                alert('A variable with this name already exists. Please use a unique name.');
                return;
            }

            if (editIndex !== '') {
                reportVariables[parseInt(editIndex)] = variable;
            } else {
                reportVariables.push(variable);
            }

            displayVariables();
            closeVariableModal();
        }

        function displayVariables() {
            const container = document.getElementById('variablesList');

            if (reportVariables.length === 0) {
                container.innerHTML = '<p style="color: #999; text-align: center; margin: 20px 0;">No variables added yet</p>';
                return;
            }

            let html = '<table style="width: 100%; border-collapse: collapse;">';
            html += '<thead><tr style="background: #f8f9fa; border-bottom: 2px solid #dee2e6;">';
            html += '<th style="padding: 8px; text-align: left;">Name</th>';
            html += '<th style="padding: 8px; text-align: left;">Type</th>';
            html += '<th style="padding: 8px; text-align: left;">Calculation</th>';
            html += '<th style="padding: 8px; text-align: left;">Expression</th>';
            html += '<th style="padding: 8px; text-align: center;">Actions</th>';
            html += '</tr></thead><tbody>';

            reportVariables.forEach((variable, index) => {
                html += '<tr style="border-bottom: 1px solid #dee2e6;">';
                html += `<td style="padding: 8px;"><strong>$V{${variable.name}}</strong></td>`;
                html += `<td style="padding: 8px; font-size: 12px;">${variable.javaClass.split('.').pop()}</td>`;
                html += `<td style="padding: 8px;">${variable.calculation}</td>`;
                html += `<td style="padding: 8px; font-family: monospace; font-size: 12px;">${variable.expression}</td>`;
                html += `<td style="padding: 8px; text-align: center;">`;
                html += `<button type="button" onclick="openVariableModal(${index})" style="padding: 4px 8px; margin-right: 5px; cursor: pointer; background: #ffc107; border: none; border-radius: 4px;">✏️ Edit</button>`;
                html += `<button type="button" onclick="deleteVariable(${index})" style="padding: 4px 8px; cursor: pointer; background: #dc3545; color: white; border: none; border-radius: 4px;">🗑️ Delete</button>`;
                html += `</td></tr>`;
            });

            html += '</tbody></table>';
            container.innerHTML = html;
        }

        function deleteVariable(index) {
            if (confirm('Are you sure you want to delete this variable?')) {
                reportVariables.splice(index, 1);
                displayVariables();
            }
        }

        // Dataset Management
        let reportDatasets = [];

        function openDatasetModal(editIndex = null) {
            const modal = document.getElementById('datasetModal');
            const title = document.getElementById('datasetModalTitle');
            const form = document.getElementById('datasetForm');

            form.reset();
            document.getElementById('datasetEditIndex').value = '';

            if (editIndex !== null) {
                title.textContent = 'Edit Dataset';
                document.getElementById('datasetEditIndex').value = editIndex;
                const dataset = reportDatasets[editIndex];
                document.getElementById('datasetName').value = dataset.name;
                document.getElementById('datasetQuery').value = dataset.query;
                document.getElementById('datasetFields').value = dataset.fields;
            } else {
                title.textContent = 'Add Dataset';
            }

            modal.style.display = 'block';
        }

        function closeDatasetModal() {
            document.getElementById('datasetModal').style.display = 'none';
        }

        function saveDataset(event) {
            event.preventDefault();

            const editIndex = document.getElementById('datasetEditIndex').value;
            const dataset = {
                name: document.getElementById('datasetName').value.trim(),
                query: document.getElementById('datasetQuery').value.trim(),
                fields: document.getElementById('datasetFields').value.trim()
            };

            // Validate dataset name uniqueness
            const nameExists = reportDatasets.some((d, index) => 
                d.name === dataset.name && index != editIndex
            );

            if (nameExists) {
                alert('A dataset with this name already exists. Please use a unique name.');
                return;
            }

            if (editIndex !== '') {
                reportDatasets[parseInt(editIndex)] = dataset;
            } else {
                reportDatasets.push(dataset);
            }

            displayDatasets();
            closeDatasetModal();
        }

        function displayDatasets() {
            const container = document.getElementById('datasetsList');

            if (reportDatasets.length === 0) {
                container.innerHTML = '<p style="color: #999; text-align: center; margin: 20px 0;">No datasets added yet</p>';
                return;
            }

            let html = '<div style="display: flex; flex-direction: column; gap: 10px;">';

            reportDatasets.forEach((dataset, index) => {
                html += '<div style="border: 1px solid #ddd; border-radius: 6px; padding: 12px; background: #f8f9fa;">';
                html += `<div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">`;
                html += `<div><strong style="font-size: 14px;">Dataset: ${dataset.name}</strong></div>`;
                html += `<div>`;
                html += `<button type="button" onclick="openDatasetModal(${index})" style="padding: 4px 8px; margin-right: 5px; cursor: pointer; background: #ffc107; border: none; border-radius: 4px;">✏️ Edit</button>`;
                html += `<button type="button" onclick="deleteDataset(${index})" style="padding: 4px 8px; cursor: pointer; background: #dc3545; color: white; border: none; border-radius: 4px;">🗑️ Delete</button>`;
                html += `</div></div>`;
                html += `<div style="font-family: monospace; font-size: 12px; background: white; padding: 8px; border-radius: 4px; margin-bottom: 8px; max-height: 100px; overflow-y: auto;">${dataset.query}</div>`;
                html += `<div style="color: #666; font-size: 12px;">Fields: ${dataset.fields}</div>`;
                html += '</div>';
            });

            html += '</div>';
            container.innerHTML = html;
        }

        function deleteDataset(index) {
            if (confirm('Are you sure you want to delete this dataset?')) {
                reportDatasets.splice(index, 1);
                displayDatasets();
            }
        }

        // Subreport Management
        let reportSubreports = [];

        function openSubreportModal(editIndex = null) {
            const modal = document.getElementById('subreportModal');
            const title = document.getElementById('subreportModalTitle');
            const form = document.getElementById('subreportForm');

            // Load datasources into dropdown
            const datasourceSelect = document.getElementById('subreportDatasource');
            fetch('/api/datasources')
                .then(response => response.json())
                .then(datasources => {
                    datasourceSelect.innerHTML = '<option value="">-- Use same as main report --</option>';
                    datasources.forEach(ds => {
                        const option = document.createElement('option');
                        option.value = ds.id;
                        option.textContent = ds.name;
                        datasourceSelect.appendChild(option);
                    });
                });

            form.reset();
            document.getElementById('subreportEditIndex').value = '';

            if (editIndex !== null) {
                title.textContent = 'Edit Subreport';
                document.getElementById('subreportEditIndex').value = editIndex;
                const subreport = reportSubreports[editIndex];
                document.getElementById('subreportName').value = subreport.name;
                document.getElementById('subreportFile').value = subreport.file;
                document.getElementById('subreportDatasource').value = subreport.datasourceId || '';
                document.getElementById('subreportParameters').value = subreport.parameters || '';
                document.getElementById('subreportX').value = subreport.x;
                document.getElementById('subreportY').value = subreport.y;
                document.getElementById('subreportWidth').value = subreport.width;
                document.getElementById('subreportHeight').value = subreport.height;
            } else {
                title.textContent = 'Add Subreport';
            }

            modal.style.display = 'block';
        }

        function closeSubreportModal() {
            document.getElementById('subreportModal').style.display = 'none';
        }

        function saveSubreport(event) {
            event.preventDefault();

            const editIndex = document.getElementById('subreportEditIndex').value;
            const subreport = {
                name: document.getElementById('subreportName').value.trim(),
                file: document.getElementById('subreportFile').value.trim(),
                datasourceId: document.getElementById('subreportDatasource').value,
                parameters: document.getElementById('subreportParameters').value.trim(),
                x: parseInt(document.getElementById('subreportX').value),
                y: parseInt(document.getElementById('subreportY').value),
                width: parseInt(document.getElementById('subreportWidth').value),
                height: parseInt(document.getElementById('subreportHeight').value)
            };

            if (editIndex !== '') {
                reportSubreports[parseInt(editIndex)] = subreport;
            } else {
                reportSubreports.push(subreport);
            }

            displaySubreports();
            closeSubreportModal();
        }

        function displaySubreports() {
            const container = document.getElementById('subreportsList');

            if (reportSubreports.length === 0) {
                container.innerHTML = '<p style="color: #999; text-align: center; margin: 20px 0;">No subreports added yet - Note: Upload subreport JRXML files first</p>';
                return;
            }

            let html = '<div style="display: flex; flex-direction: column; gap: 10px;">';

            reportSubreports.forEach((subreport, index) => {
                html += '<div style="border: 1px solid #ddd; border-radius: 6px; padding: 12px; background: #f8f9fa;">';
                html += `<div style="display: flex; justify-content: space-between; align-items: start; margin-bottom: 8px;">`;
                html += `<div><strong style="font-size: 14px;">${subreport.name}</strong></div>`;
                html += `<div>`;
                html += `<button type="button" onclick="openSubreportModal(${index})" style="padding: 4px 8px; margin-right: 5px; cursor: pointer; background: #ffc107; border: none; border-radius: 4px;">✏️ Edit</button>`;
                html += `<button type="button" onclick="deleteSubreport(${index})" style="padding: 4px 8px; cursor: pointer; background: #dc3545; color: white; border: none; border-radius: 4px;">🗑️ Delete</button>`;
                html += `</div></div>`;
                html += `<div style="color: #666; font-size: 13px;">📄 File: ${subreport.file}</div>`;
                html += `<div style="color: #666; font-size: 13px;">📊 Datasource: ${subreport.datasourceId || 'Same as main report'}</div>`;
                html += `<div style="color: #666; font-size: 13px;">📐 Size: ${subreport.width}x${subreport.height} at (${subreport.x}, ${subreport.y})</div>`;
                if (subreport.parameters) {
                    html += `<div style="color: #666; font-size: 13px;">🔧 Parameters: ${subreport.parameters}</div>`;
                }
                html += '</div>';
            });

            html += '</div>';
            container.innerHTML = html;
        }

        function deleteSubreport(index) {
            if (confirm('Are you sure you want to delete this subreport?')) {
                reportSubreports.splice(index, 1);
                displaySubreports();
            }
        }

        // Builder Tables & Columns
        function loadBuilderTables() {
            const datasourceId = document.getElementById('builderDatasource').value;
            const tableGroup = document.getElementById('builderTableGroup');
            const columnsGroup = document.getElementById('builderColumnsGroup');

            if (!datasourceId) {
                tableGroup.style.display = 'none';
                columnsGroup.style.display = 'none';
                return;
            }

            fetch(`/api/builder/datasources/${datasourceId}/tables`)
                .then(response => response.json())
                .then(data => {
                    if (data.success && data.tables) {
                        const select = document.getElementById('builderTable');
                        select.innerHTML = '<option value="">-- Select a table --</option>';
                        data.tables.forEach(table => {
                            const option = document.createElement('option');
                            option.value = table;
                            option.textContent = table;
                            select.appendChild(option);
                        });
                        tableGroup.style.display = 'block';
                        columnsGroup.style.display = 'none';
                    } else {
                        showBuilderMessage('Failed to load tables: ' + (data.message || 'Unknown error'), 'error');
                    }
                })
                .catch(error => {
                    showBuilderMessage('Error loading tables: ' + error, 'error');
                });
        }

        function loadBuilderColumns() {
            const datasourceId = document.getElementById('builderDatasource').value;
            const tableName = document.getElementById('builderTable').value;
            const columnsGroup = document.getElementById('builderColumnsGroup');
            const columnsContainer = document.getElementById('builderColumnsContainer');
            const parametersGroup = document.getElementById('builderParametersGroup');
            const variablesGroup = document.getElementById('builderVariablesGroup');
            const datasetsGroup = document.getElementById('builderDatasetsGroup');
            const subreportsGroup = document.getElementById('builderSubreportsGroup');

            if (!tableName) {
                columnsGroup.style.display = 'none';
                parametersGroup.style.display = 'none';
                variablesGroup.style.display = 'none';
                datasetsGroup.style.display = 'none';
                subreportsGroup.style.display = 'none';
                return;
            }

            fetch(`/api/builder/datasources/${datasourceId}/tables/${tableName}/columns`)
                .then(response => response.json())
                .then(data => {
                    if (data.success && data.columns) {
                        columnsContainer.innerHTML = '';
                        data.columns.forEach(column => {
                            const div = document.createElement('div');
                            div.style.marginBottom = '8px';
                            div.innerHTML = `
                                <label style="display: flex; align-items: center; cursor: pointer;">
                                    <input type="checkbox" name="builderColumns" value="${column.name}" 
                                           style="margin-right: 8px;" checked>
                                    <span style="font-weight: 500;">${column.name}</span>
                                    <span style="color: #999; margin-left: 8px; font-size: 12px;">(${column.type})</span>
                                </label>
                            `;
                            columnsContainer.appendChild(div);
                        });
                        columnsGroup.style.display = 'block';
                        parametersGroup.style.display = 'block';
                        variablesGroup.style.display = 'block';
                        datasetsGroup.style.display = 'block';
                        subreportsGroup.style.display = 'block';
                    } else {
                        showBuilderMessage('Failed to load columns: ' + (data.message || 'Unknown error'), 'error');
                    }
                })
                .catch(error => {
                    showBuilderMessage('Error loading columns: ' + error, 'error');
                });
        }

        function selectAllColumns() {
            const checkboxes = document.querySelectorAll('input[name="builderColumns"]');
            checkboxes.forEach(cb => cb.checked = true);
        }

        function unselectAllColumns() {
            const checkboxes = document.querySelectorAll('input[name="builderColumns"]');
            checkboxes.forEach(cb => cb.checked = false);
        }

        function generateBuilderReport(event) {
            event.preventDefault();

            const reportName = document.getElementById('builderReportName').value.trim();
            const datasourceId = document.getElementById('builderDatasource').value;
            const tableName = document.getElementById('builderTable').value;

            // Get selected columns
            const checkboxes = document.querySelectorAll('input[name="builderColumns"]:checked');
            const columns = Array.from(checkboxes).map(cb => cb.value);

            if (columns.length === 0) {
                showBuilderMessage('Please select at least one column', 'error');
                return;
            }

            // Show loading message
            showBuilderMessage('Generating JRXML file...', 'info');

            // Build form data
            const formData = new URLSearchParams();
            formData.append('reportName', reportName);
            formData.append('tableName', tableName);
            formData.append('datasourceId', datasourceId);
            columns.forEach(col => formData.append('columns', col));

            // Add parameters as JSON if any exist
            if (reportParameters.length > 0) {
                formData.append('parametersJson', JSON.stringify(reportParameters));
            }

            // Add variables as JSON if any exist
            if (reportVariables.length > 0) {
                formData.append('variablesJson', JSON.stringify(reportVariables));
            }

            fetch('/api/builder/generate', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeader]: csrfToken
                },
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showBuilderMessage('✓ ' + data.message + ' - Check Available Reports section to download', 'success');

                    // Reload reports list
                    loadReports();
                    // Reset form
                    document.getElementById('builderForm').reset();
                    document.getElementById('builderTableGroup').style.display = 'none';
                    document.getElementById('builderColumnsGroup').style.display = 'none';
                    document.getElementById('builderParametersGroup').style.display = 'none';
                    document.getElementById('builderVariablesGroup').style.display = 'none';
                    // Clear parameters and variables
                    reportParameters = [];
                    reportVariables = [];
                    displayParameters();
                    displayVariables();
                } else {
                    showBuilderMessage('✗ ' + data.message, 'error');
                }
            })
            .catch(error => {
                showBuilderMessage('Error generating report: ' + error, 'error');
            });
        }

        function showBuilderMessage(text, type) {
            const msg = document.getElementById('builderMessage');
            msg.textContent = text;
            msg.style.padding = '10px';
            msg.style.borderRadius = '6px';
            msg.style.marginTop = '15px';

            if (type === 'success') {
                msg.style.background = '#d4edda';
                msg.style.color = '#155724';
                msg.style.border = '1px solid #c3e6cb';
            } else if (type === 'error') {
                msg.style.background = '#f8d7da';
                msg.style.color = '#721c24';
                msg.style.border = '1px solid #f5c6cb';
            } else if (type === 'info') {
                msg.style.background = '#d1ecf1';
                msg.style.color = '#0c5460';
                msg.style.border = '1px solid #bee5eb';
            }

            if (type !== 'info') {
                setTimeout(() => { msg.textContent = ''; msg.style.padding = '0'; }, 5000);
            }
        }

        // Monaco Editor for JRXML Editing
        let monacoEditor = null;
        let currentEditingFile = null;

        function openJrxmlEditor(fileName) {
            currentEditingFile = fileName;
            document.getElementById('jrxmlEditorTitle').textContent = 'Edit: ' + fileName;
            document.getElementById('jrxmlEditorModal').style.display = 'block';
            document.getElementById('editorMessage').textContent = '';

            // Load file content
            fetch(`/api/jrxml/load/${encodeURIComponent(fileName)}`)
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        initializeMonacoEditor(data.content);
                    } else {
                        showEditorMessage('Failed to load file: ' + data.message, 'error');
                    }
                })
                .catch(error => {
                    showEditorMessage('Error loading file: ' + error, 'error');
                });
        }

        function initializeMonacoEditor(content) {
            require.config({ paths: { 'vs': 'https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.45.0/min/vs' }});

            require(['vs/editor/editor.main'], function() {
                const container = document.getElementById('editorContainer');
                container.innerHTML = '';

                monacoEditor = monaco.editor.create(container, {
                    value: content,
                    language: 'xml',
                    theme: 'vs-dark',
                    automaticLayout: true,
                    minimap: { enabled: true },
                    fontSize: 14,
                    lineNumbers: 'on',
                    roundedSelection: false,
                    scrollBeyondLastLine: false,
                    readOnly: false,
                    wordWrap: 'on'
                });
            });
        }

        function closeJrxmlEditor() {
            document.getElementById('jrxmlEditorModal').style.display = 'none';
            if (monacoEditor) {
                monacoEditor.dispose();
                monacoEditor = null;
            }
            currentEditingFile = null;
        }

        function saveJrxmlFile() {
            if (!monacoEditor || !currentEditingFile) {
                showEditorMessage('No file to save', 'error');
                return;
            }

            const content = monacoEditor.getValue();
            const formData = new URLSearchParams();
            formData.append('fileName', currentEditingFile);
            formData.append('content', content);

            showEditorMessage('Saving file...', 'info');

            fetch('/api/jrxml/save', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeader]: csrfToken
                },
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showEditorMessage('✓ File saved successfully!', 'success');
                    setTimeout(() => {
                        closeJrxmlEditor();
                        loadReports();
                    }, 1500);
                } else {
                    showEditorMessage('✗ Save failed: ' + data.message, 'error');
                }
            })
            .catch(error => {
                showEditorMessage('Error saving file: ' + error, 'error');
            });
        }

        function downloadEditedJrxml() {
            if (!monacoEditor || !currentEditingFile) {
                showEditorMessage('No file to download', 'error');
                return;
            }

            const content = monacoEditor.getValue();
            const blob = new Blob([content], { type: 'application/xml' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = currentEditingFile;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);
            URL.revokeObjectURL(url);

            showEditorMessage('✓ File downloaded!', 'success');
        }

        function showEditorMessage(text, type) {
            const msg = document.getElementById('editorMessage');
            msg.textContent = text;
            msg.style.padding = '10px';
            msg.style.borderRadius = '6px';
            msg.style.marginTop = '15px';

            if (type === 'success') {
                msg.style.background = '#d4edda';
                msg.style.color = '#155724';
                msg.style.border = '1px solid #c3e6cb';
            } else if (type === 'error') {
                msg.style.background = '#f8d7da';
                msg.style.color = '#721c24';
                msg.style.border = '1px solid #f5c6cb';
            } else if (type === 'info') {
                msg.style.background = '#d1ecf1';
                msg.style.color = '#0c5460';
                msg.style.border = '1px solid #bee5eb';
            }
        }
