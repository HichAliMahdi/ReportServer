// ========== Template Manager Functions ==========

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
                        list.innerHTML = '';
                        data.templates.forEach(template => {
                            const div = document.createElement('div');
                            div.className = 'template-item';
                            div.innerHTML = `
                                <h4>${template.name || 'Unnamed Template'}</h4>
                                <p style="font-size: 12px; color: #666; margin: 5px 0;">${template.description || 'No description'}</p>
                                <div class="template-actions">
                                    <button class="btn" onclick="loadTemplate('${template.fileName}')" style="flex: 1; padding: 6px; font-size: 12px;">Load</button>
                                    <button class="btn" onclick="deleteTemplate('${template.fileName}')" style="flex: 1; padding: 6px; font-size: 12px; background: #dc3545;">Delete</button>
                                </div>
                            `;
                            list.appendChild(div);
                        });
                    } else {
                        list.innerHTML = '<p style="color: #999; text-align: center; padding: 40px;">No templates saved yet</p>';
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
                method: 'DELETE'
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
                headers: {
                    'Content-Type': 'application/json'
                },
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
