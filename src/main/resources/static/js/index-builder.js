// ========== Report Builder Functions ==========

        function clearElement(element) {
            if (element) {
                element.replaceChildren();
            }
        }

        function createEmptyMessage(text) {
            const paragraph = document.createElement('p');
            paragraph.style.color = '#999';
            paragraph.style.textAlign = 'center';
            paragraph.style.margin = '20px 0';
            paragraph.textContent = text;
            return paragraph;
        }

        function loadBuilderDatasources() {
            fetch('/api/datasources')
                .then(response => response.json())
                .then(datasources => {
                    const select = document.getElementById('builderDatasource');
                    const placeholder = document.createElement('option');
                    placeholder.value = '';
                    placeholder.textContent = '-- Select a datasource --';
                    select.replaceChildren(placeholder);
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
                clearElement(container);
                container.appendChild(createEmptyMessage('No parameters added yet'));
                return;
            }

            clearElement(container);
            const table = document.createElement('table');
            table.style.width = '100%';
            table.style.borderCollapse = 'collapse';

            const thead = document.createElement('thead');
            const headerRow = document.createElement('tr');
            headerRow.style.background = '#f8f9fa';
            headerRow.style.borderBottom = '2px solid #dee2e6';
            ['Name', 'Type', 'Default Value', 'Actions'].forEach((title, index) => {
                const th = document.createElement('th');
                th.style.padding = '8px';
                th.style.textAlign = index === 3 ? 'center' : 'left';
                th.textContent = title;
                headerRow.appendChild(th);
            });
            thead.appendChild(headerRow);

            const tbody = document.createElement('tbody');
            reportParameters.forEach((parameter, index) => {
                const row = document.createElement('tr');
                row.style.borderBottom = '1px solid #dee2e6';

                const nameCell = document.createElement('td');
                nameCell.style.padding = '8px';
                const strong = document.createElement('strong');
                strong.textContent = `$P{${parameter.name}}`;
                nameCell.appendChild(strong);

                const typeCell = document.createElement('td');
                typeCell.style.padding = '8px';
                typeCell.style.fontSize = '12px';
                typeCell.textContent = parameter.javaClass.split('.').pop();

                const defaultCell = document.createElement('td');
                defaultCell.style.padding = '8px';
                defaultCell.style.fontFamily = 'monospace';
                defaultCell.style.fontSize = '12px';
                if (parameter.defaultValueExpression) {
                    defaultCell.textContent = parameter.defaultValueExpression;
                } else {
                    const em = document.createElement('em');
                    em.textContent = 'none';
                    defaultCell.appendChild(em);
                }

                const actionsCell = document.createElement('td');
                actionsCell.style.padding = '8px';
                actionsCell.style.textAlign = 'center';

                const editButton = document.createElement('button');
                editButton.type = 'button';
                editButton.textContent = '✏️ Edit';
                editButton.style.padding = '4px 8px';
                editButton.style.marginRight = '5px';
                editButton.style.cursor = 'pointer';
                editButton.style.background = '#ffc107';
                editButton.style.border = 'none';
                editButton.style.borderRadius = '4px';
                editButton.addEventListener('click', () => openParameterModal(index));

                const deleteButton = document.createElement('button');
                deleteButton.type = 'button';
                deleteButton.textContent = '🗑️ Delete';
                deleteButton.style.padding = '4px 8px';
                deleteButton.style.cursor = 'pointer';
                deleteButton.style.background = '#dc3545';
                deleteButton.style.color = 'white';
                deleteButton.style.border = 'none';
                deleteButton.style.borderRadius = '4px';
                deleteButton.addEventListener('click', () => deleteParameter(index));

                actionsCell.appendChild(editButton);
                actionsCell.appendChild(deleteButton);

                row.appendChild(nameCell);
                row.appendChild(typeCell);
                row.appendChild(defaultCell);
                row.appendChild(actionsCell);
                tbody.appendChild(row);
            });
            table.appendChild(thead);
            table.appendChild(tbody);
            container.appendChild(table);
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
                clearElement(container);
                container.appendChild(createEmptyMessage('No variables added yet'));
                return;
            }

            clearElement(container);
            const table = document.createElement('table');
            table.style.width = '100%';
            table.style.borderCollapse = 'collapse';

            const thead = document.createElement('thead');
            const headerRow = document.createElement('tr');
            headerRow.style.background = '#f8f9fa';
            headerRow.style.borderBottom = '2px solid #dee2e6';
            ['Name', 'Type', 'Calculation', 'Expression', 'Actions'].forEach((title, index) => {
                const th = document.createElement('th');
                th.style.padding = '8px';
                th.style.textAlign = index === 4 ? 'center' : 'left';
                th.textContent = title;
                headerRow.appendChild(th);
            });
            thead.appendChild(headerRow);

            const tbody = document.createElement('tbody');
            reportVariables.forEach((variable, index) => {
                const row = document.createElement('tr');
                row.style.borderBottom = '1px solid #dee2e6';

                const nameCell = document.createElement('td');
                nameCell.style.padding = '8px';
                const strong = document.createElement('strong');
                strong.textContent = `$V{${variable.name}}`;
                nameCell.appendChild(strong);

                const typeCell = document.createElement('td');
                typeCell.style.padding = '8px';
                typeCell.style.fontSize = '12px';
                typeCell.textContent = variable.javaClass.split('.').pop();

                const calcCell = document.createElement('td');
                calcCell.style.padding = '8px';
                calcCell.textContent = variable.calculation;

                const expressionCell = document.createElement('td');
                expressionCell.style.padding = '8px';
                expressionCell.style.fontFamily = 'monospace';
                expressionCell.style.fontSize = '12px';
                expressionCell.textContent = variable.expression;

                const actionsCell = document.createElement('td');
                actionsCell.style.padding = '8px';
                actionsCell.style.textAlign = 'center';

                const editButton = document.createElement('button');
                editButton.type = 'button';
                editButton.textContent = '✏️ Edit';
                editButton.style.padding = '4px 8px';
                editButton.style.marginRight = '5px';
                editButton.style.cursor = 'pointer';
                editButton.style.background = '#ffc107';
                editButton.style.border = 'none';
                editButton.style.borderRadius = '4px';
                editButton.addEventListener('click', () => openVariableModal(index));

                const deleteButton = document.createElement('button');
                deleteButton.type = 'button';
                deleteButton.textContent = '🗑️ Delete';
                deleteButton.style.padding = '4px 8px';
                deleteButton.style.cursor = 'pointer';
                deleteButton.style.background = '#dc3545';
                deleteButton.style.color = 'white';
                deleteButton.style.border = 'none';
                deleteButton.style.borderRadius = '4px';
                deleteButton.addEventListener('click', () => deleteVariable(index));

                actionsCell.appendChild(editButton);
                actionsCell.appendChild(deleteButton);

                row.appendChild(nameCell);
                row.appendChild(typeCell);
                row.appendChild(calcCell);
                row.appendChild(expressionCell);
                row.appendChild(actionsCell);
                tbody.appendChild(row);
            });
            table.appendChild(thead);
            table.appendChild(tbody);
            container.appendChild(table);
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
                clearElement(container);
                container.appendChild(createEmptyMessage('No datasets added yet'));
                return;
            }

            clearElement(container);
            const wrapper = document.createElement('div');
            wrapper.style.display = 'flex';
            wrapper.style.flexDirection = 'column';
            wrapper.style.gap = '10px';
            reportDatasets.forEach((dataset, index) => {
                const card = document.createElement('div');
                card.style.border = '1px solid #ddd';
                card.style.borderRadius = '6px';
                card.style.padding = '12px';
                card.style.background = '#f8f9fa';

                const header = document.createElement('div');
                header.style.display = 'flex';
                header.style.justifyContent = 'space-between';
                header.style.alignItems = 'start';
                header.style.marginBottom = '8px';

                const title = document.createElement('div');
                const strong = document.createElement('strong');
                strong.style.fontSize = '14px';
                strong.textContent = `Dataset: ${dataset.name}`;
                title.appendChild(strong);

                const actions = document.createElement('div');
                const editButton = document.createElement('button');
                editButton.type = 'button';
                editButton.textContent = '✏️ Edit';
                editButton.style.padding = '4px 8px';
                editButton.style.marginRight = '5px';
                editButton.style.cursor = 'pointer';
                editButton.style.background = '#ffc107';
                editButton.style.border = 'none';
                editButton.style.borderRadius = '4px';
                editButton.addEventListener('click', () => openDatasetModal(index));

                const deleteButton = document.createElement('button');
                deleteButton.type = 'button';
                deleteButton.textContent = '🗑️ Delete';
                deleteButton.style.padding = '4px 8px';
                deleteButton.style.cursor = 'pointer';
                deleteButton.style.background = '#dc3545';
                deleteButton.style.color = 'white';
                deleteButton.style.border = 'none';
                deleteButton.style.borderRadius = '4px';
                deleteButton.addEventListener('click', () => deleteDataset(index));

                actions.appendChild(editButton);
                actions.appendChild(deleteButton);

                header.appendChild(title);
                header.appendChild(actions);

                const query = document.createElement('div');
                query.style.fontFamily = 'monospace';
                query.style.fontSize = '12px';
                query.style.background = 'white';
                query.style.padding = '8px';
                query.style.borderRadius = '4px';
                query.style.marginBottom = '8px';
                query.style.maxHeight = '100px';
                query.style.overflowY = 'auto';
                query.textContent = dataset.query;

                const fields = document.createElement('div');
                fields.style.color = '#666';
                fields.style.fontSize = '12px';
                fields.textContent = `Fields: ${dataset.fields}`;

                card.appendChild(header);
                card.appendChild(query);
                card.appendChild(fields);
                wrapper.appendChild(card);
            });

            container.appendChild(wrapper);
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
                    const placeholder = document.createElement('option');
                    placeholder.value = '';
                    placeholder.textContent = '-- Use same as main report --';
                    datasourceSelect.replaceChildren(placeholder);
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
                clearElement(container);
                container.appendChild(createEmptyMessage('No subreports added yet - Note: Upload subreport JRXML files first'));
                return;
            }

            clearElement(container);
            const wrapper = document.createElement('div');
            wrapper.style.display = 'flex';
            wrapper.style.flexDirection = 'column';
            wrapper.style.gap = '10px';
            reportSubreports.forEach((subreport, index) => {
                const card = document.createElement('div');
                card.style.border = '1px solid #ddd';
                card.style.borderRadius = '6px';
                card.style.padding = '12px';
                card.style.background = '#f8f9fa';

                const header = document.createElement('div');
                header.style.display = 'flex';
                header.style.justifyContent = 'space-between';
                header.style.alignItems = 'start';
                header.style.marginBottom = '8px';

                const title = document.createElement('div');
                const strong = document.createElement('strong');
                strong.style.fontSize = '14px';
                strong.textContent = subreport.name;
                title.appendChild(strong);

                const actions = document.createElement('div');
                const editButton = document.createElement('button');
                editButton.type = 'button';
                editButton.textContent = '✏️ Edit';
                editButton.style.padding = '4px 8px';
                editButton.style.marginRight = '5px';
                editButton.style.cursor = 'pointer';
                editButton.style.background = '#ffc107';
                editButton.style.border = 'none';
                editButton.style.borderRadius = '4px';
                editButton.addEventListener('click', () => openSubreportModal(index));

                const deleteButton = document.createElement('button');
                deleteButton.type = 'button';
                deleteButton.textContent = '🗑️ Delete';
                deleteButton.style.padding = '4px 8px';
                deleteButton.style.cursor = 'pointer';
                deleteButton.style.background = '#dc3545';
                deleteButton.style.color = 'white';
                deleteButton.style.border = 'none';
                deleteButton.style.borderRadius = '4px';
                deleteButton.addEventListener('click', () => deleteSubreport(index));

                actions.appendChild(editButton);
                actions.appendChild(deleteButton);

                header.appendChild(title);
                header.appendChild(actions);

                const fileLine = document.createElement('div');
                fileLine.style.color = '#666';
                fileLine.style.fontSize = '13px';
                fileLine.textContent = `📄 File: ${subreport.file}`;

                const datasourceLine = document.createElement('div');
                datasourceLine.style.color = '#666';
                datasourceLine.style.fontSize = '13px';
                datasourceLine.textContent = `📊 Datasource: ${subreport.datasourceId || 'Same as main report'}`;

                const sizeLine = document.createElement('div');
                sizeLine.style.color = '#666';
                sizeLine.style.fontSize = '13px';
                sizeLine.textContent = `📐 Size: ${subreport.width}x${subreport.height} at (${subreport.x}, ${subreport.y})`;

                card.appendChild(header);
                card.appendChild(fileLine);
                card.appendChild(datasourceLine);
                card.appendChild(sizeLine);

                if (subreport.parameters) {
                    const paramsLine = document.createElement('div');
                    paramsLine.style.color = '#666';
                    paramsLine.style.fontSize = '13px';
                    paramsLine.textContent = `🔧 Parameters: ${subreport.parameters}`;
                    card.appendChild(paramsLine);
                }

                wrapper.appendChild(card);
            });

            container.appendChild(wrapper);
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
                        const placeholder = document.createElement('option');
                        placeholder.value = '';
                        placeholder.textContent = '-- Select a table --';
                        select.replaceChildren(placeholder);
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
                        columnsContainer.replaceChildren();
                        data.columns.forEach(column => {
                            const div = document.createElement('div');
                            div.style.marginBottom = '8px';
                            const label = document.createElement('label');
                            label.style.display = 'flex';
                            label.style.alignItems = 'center';
                            label.style.cursor = 'pointer';

                            const checkbox = document.createElement('input');
                            checkbox.type = 'checkbox';
                            checkbox.name = 'builderColumns';
                            checkbox.value = column.name;
                            checkbox.style.marginRight = '8px';
                            checkbox.checked = true;

                            const nameSpan = document.createElement('span');
                            nameSpan.style.fontWeight = '500';
                            nameSpan.textContent = column.name;

                            const typeSpan = document.createElement('span');
                            typeSpan.style.color = '#999';
                            typeSpan.style.marginLeft = '8px';
                            typeSpan.style.fontSize = '12px';
                            typeSpan.textContent = `(${column.type})`;

                            label.appendChild(checkbox);
                            label.appendChild(nameSpan);
                            label.appendChild(typeSpan);

                            div.appendChild(label);
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

        function buildBuilderGenerationFormData() {
            const reportName = document.getElementById('builderReportName').value.trim();
            const datasourceId = document.getElementById('builderDatasource').value;
            const tableName = document.getElementById('builderTable').value;
            const reportFormat = document.getElementById('builderOutputFormat')?.value || 'pdf';
            const normalizedReportName = reportName.endsWith('.jrxml') ? reportName : `${reportName}.jrxml`;

            const checkboxes = document.querySelectorAll('input[name="builderColumns"]:checked');
            const columns = Array.from(checkboxes).map(cb => cb.value);
            if (columns.length === 0) {
                showBuilderMessage('Please select at least one column', 'error');
                return null;
            }

            const formData = new URLSearchParams();
            formData.append('reportName', reportName);
            formData.append('tableName', tableName);
            formData.append('datasourceId', datasourceId);
            formData.append('reportFormat', reportFormat);
            columns.forEach(col => formData.append('columns', col));

            if (reportParameters.length > 0) {
                formData.append('parametersJson', JSON.stringify(reportParameters));
            }
            if (reportVariables.length > 0) {
                formData.append('variablesJson', JSON.stringify(reportVariables));
            }

            if (typeof vbBuildSharedCoverOptions === 'function') {
                const formCoverOptions = vbBuildSharedCoverOptions('form');
                if (formCoverOptions && formCoverOptions.coverPageEnabled === true) {
                    if (!formCoverOptions.coverTitle && (formCoverOptions.coverIncludeReportName !== false)) {
                        formCoverOptions.coverTitle = normalizedReportName.replace(/\.jrxml$/i, '');
                    }
                    formData.append('reportOptionsJson', JSON.stringify(formCoverOptions));
                }
            }

            return formData;
        }

        function resetBuilderFormAfterGeneration() {
            document.getElementById('builderForm').reset();
            document.getElementById('builderTableGroup').style.display = 'none';
            document.getElementById('builderColumnsGroup').style.display = 'none';
            document.getElementById('builderParametersGroup').style.display = 'none';
            document.getElementById('builderVariablesGroup').style.display = 'none';
            document.getElementById('builderDatasetsGroup').style.display = 'none';
            document.getElementById('builderSubreportsGroup').style.display = 'none';

            reportParameters = [];
            reportVariables = [];
            displayParameters();
            displayVariables();

            if (typeof vbHandleCoverTemplateSelectionChange === 'function') {
                vbHandleCoverTemplateSelectionChange('form');
            }
        }

        function submitBuilderGeneration(mode, event) {
            if (event && typeof event.preventDefault === 'function') {
                event.preventDefault();
            }

            const endpointByMode = {
                'jrxml': '/api/builder/generate',
                'jrxml-and-report': '/api/builder/generate-and-report',
                'report-only': '/api/builder/generate-report-only'
            };

            const loadingMessageByMode = {
                'jrxml': 'Generating JRXML template...',
                'jrxml-and-report': 'Generating JRXML template and report...',
                'report-only': 'Generating report (without saving JRXML template)...'
            };

            const endpoint = endpointByMode[mode] || endpointByMode.jrxml;
            const formData = buildBuilderGenerationFormData();
            if (!formData) {
                return;
            }

            showBuilderMessage(loadingMessageByMode[mode] || 'Processing...', 'info');

            fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                    [csrfHeader]: csrfToken
                },
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (!data.success) {
                    showBuilderMessage('✗ ' + (data.message || 'Operation failed'), 'error');
                    return;
                }

                showBuilderMessage('✓ ' + data.message, 'success');

                if (typeof loadReports === 'function' && mode !== 'report-only') {
                    loadReports();
                }
                if (typeof loadGeneratedReports === 'function' && mode !== 'jrxml') {
                    loadGeneratedReports(0);
                }
                if (typeof switchReportsSubTab === 'function' && mode !== 'jrxml') {
                    switchReportsSubTab('available-reports');
                }

                if (mode !== 'report-only') {
                    resetBuilderFormAfterGeneration();
                }
            })
            .catch(error => {
                showBuilderMessage('Error generating report: ' + error, 'error');
            });
        }

        function generateBuilderReport(event) {
            submitBuilderGeneration('jrxml', event);
        }

        function generateBuilderAndReport(event) {
            submitBuilderGeneration('jrxml-and-report', event);
        }

        function generateBuilderReportOnly(event) {
            submitBuilderGeneration('report-only', event);
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
                container.replaceChildren();

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

                // Real-time JRXML validation (debounced, 1 s after last keystroke)
                let validateTimer = null;
                monacoEditor.onDidChangeModelContent(() => {
                    const panel = document.getElementById('jrxmlValidationPanel');
                    if (panel) panel.textContent = '⏳ Validating…';
                    clearTimeout(validateTimer);
                    validateTimer = setTimeout(validateJrxmlInEditor, 1000);
                });

                // Run initial validation
                setTimeout(validateJrxmlInEditor, 500);
            });
        }

        function validateJrxmlInEditor() {
            if (!monacoEditor) return;
            const panel = document.getElementById('jrxmlValidationPanel');
            if (!panel) return;

            const editorCsrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
            const editorCsrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
            const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
            if (editorCsrfToken && editorCsrfHeader) {
                headers[editorCsrfHeader] = editorCsrfToken;
            }

            const formData = new URLSearchParams();
            formData.append('content', monacoEditor.getValue());

            fetch('/api/jrxml/validate', { method: 'POST', headers, body: formData })
            .then(r => r.json())
            .then(result => {
                if (result.valid && (!result.issues || result.issues.length === 0)) {
                    panel.replaceChildren();
                    const span = document.createElement('span');
                    span.style.color = '#4caf50';
                    span.textContent = '✅ JRXML is valid';
                    panel.appendChild(span);
                } else if (result.issues && result.issues.length > 0) {
                    panel.replaceChildren();
                    result.issues.forEach(iss => {
                        const span = document.createElement('span');
                        span.style.color = '#f48771';
                        span.style.marginRight = '12px';
                        span.textContent = `⚠️ ${iss}`;
                        panel.appendChild(span);
                    });
                } else {
                    panel.replaceChildren();
                    const span = document.createElement('span');
                    span.style.color = '#f48771';
                    span.textContent = '❌ JRXML has issues';
                    panel.appendChild(span);
                }
            })
            .catch(() => {
                panel.replaceChildren();
                const span = document.createElement('span');
                span.style.color = '#fd7e14';
                span.textContent = '⚠️ Validation unavailable';
                panel.appendChild(span);
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
