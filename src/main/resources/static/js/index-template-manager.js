// ========== Template Manager Functions ==========

// Get CSRF tokens from meta tags
const tmpMgrCsrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
const tmpMgrCsrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

function getTemplateMgrHeaders(additionalHeaders = {}) {
    const headers = { ...additionalHeaders };
    if (tmpMgrCsrfToken && tmpMgrCsrfHeader) {
        headers[tmpMgrCsrfHeader] = tmpMgrCsrfToken;
    }
    return headers;
}

        function openTemplateManager() {
            document.getElementById('templateManagerModal').style.display = 'block';
            loadTemplates();
        }

        function closeTemplateManager() {
            document.getElementById('templateManagerModal').style.display = 'none';
        }

        function loadTemplates() {
            fetch('/api/builder/templates')
                .then(response => response.json())
                .then(data => {
                    const list = document.getElementById('templateList');

                    if (data.templates && data.templates.length > 0) {
                        list.replaceChildren();
                        data.templates.forEach(template => {
                            const div = document.createElement('div');
                            div.className = 'template-item';

                            const title = document.createElement('h4');
                            title.textContent = template.name || 'Unnamed Template';

                            const description = document.createElement('p');
                            description.style.fontSize = '12px';
                            description.style.color = '#666';
                            description.style.margin = '5px 0';
                            description.textContent = template.description || 'No description';

                            const actions = document.createElement('div');
                            actions.className = 'template-actions';

                            const loadButton = document.createElement('button');
                            loadButton.className = 'btn';
                            loadButton.type = 'button';
                            loadButton.style.flex = '1';
                            loadButton.style.padding = '6px';
                            loadButton.style.fontSize = '12px';
                            loadButton.textContent = 'Load';
                            loadButton.addEventListener('click', () => loadTemplate(template.fileName));

                            const deleteButton = document.createElement('button');
                            deleteButton.className = 'btn';
                            deleteButton.type = 'button';
                            deleteButton.style.flex = '1';
                            deleteButton.style.padding = '6px';
                            deleteButton.style.fontSize = '12px';
                            deleteButton.style.background = '#dc3545';
                            deleteButton.textContent = 'Delete';
                            deleteButton.addEventListener('click', () => deleteTemplate(template.fileName));

                            actions.appendChild(loadButton);
                            actions.appendChild(deleteButton);
                            div.appendChild(title);
                            div.appendChild(description);
                            div.appendChild(actions);
                            list.appendChild(div);
                        });
                    } else {
                        list.replaceChildren();
                        const empty = document.createElement('p');
                        empty.style.color = '#999';
                        empty.style.textAlign = 'center';
                        empty.style.padding = '40px';
                        empty.textContent = 'No templates saved yet';
                        list.appendChild(empty);
                    }
                })
                .catch(error => {
                    console.error('Error loading templates:', error);
                });
        }

        function loadTemplate(fileName) {
            fetch('/api/builder/templates/' + fileName)
                .then(response => response.json())
                .then(data => {
                    visualBuilder.elements = data.elements || [];
                    visualBuilder.pageSettings = data.pageSettings || visualBuilder.pageSettings;
                    visualBuilder.nextId = Math.max(...visualBuilder.elements.map(el => el.id), 0) + 1;

                    document.getElementById('visualReportName').value = data.name || '';

                    // Update page settings form
                    document.getElementById('pageWidth').value = visualBuilder.pageSettings.width;
                    document.getElementById('pageHeight').value = visualBuilder.pageSettings.height;
                    document.getElementById('marginTop').value = visualBuilder.pageSettings.topMargin;
                    document.getElementById('marginBottom').value = visualBuilder.pageSettings.bottomMargin;
                    document.getElementById('marginLeft').value = visualBuilder.pageSettings.leftMargin;
                    document.getElementById('marginRight').value = visualBuilder.pageSettings.rightMargin;
                    document.getElementById('pageOrientation').value = visualBuilder.pageSettings.orientation;

                    renderCanvas();
                    closeTemplateManager();
                    showMessage('Template loaded successfully', 'success');
                })
                .catch(error => {
                    showMessage('Error loading template: ' + error, 'error');
                });
        }

        function deleteTemplate(fileName) {
            if (!confirm('Are you sure you want to delete this template?')) return;

            fetch('/api/builder/templates/' + fileName, {
                method: 'DELETE',
                headers: getTemplateMgrHeaders()
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showMessage('Template deleted successfully', 'success');
                    loadTemplates();
                } else {
                    showMessage(data.message || 'Failed to delete template', 'error');
                }
            })
            .catch(error => {
                showMessage('Error deleting template: ' + error, 'error');
            });
        }

        function saveAsTemplate() {
            const name = prompt('Enter template name:');
            if (!name) return;

            const description = prompt('Enter template description (optional):');

            const templateData = {
                name: name,
                description: description || '',
                elements: visualBuilder.elements,
                pageSettings: visualBuilder.pageSettings
            };

            fetch('/api/builder/templates/save', {
                method: 'POST',
                headers: getTemplateMgrHeaders({
                    'Content-Type': 'application/json'
                }),
                body: JSON.stringify(templateData)
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showMessage('Template saved successfully', 'success');
                } else {
                    showMessage(data.message || 'Failed to save template', 'error');
                }
            })
            .catch(error => {
                showMessage('Error saving template: ' + error, 'error');
            });
        }
