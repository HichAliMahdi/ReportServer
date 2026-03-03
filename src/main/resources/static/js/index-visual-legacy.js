// ========== Visual Builder Functions ==========

        let visualBuilder = {
            elements: [],
            selectedElement: null,
            nextId: 1,
            currentBand: 'detail',
            datasourceId: null,
            tableName: '',
            availableFields: [],
            customQuery: '',
            pageSettings: {
                width: 595,
                height: 842,
                leftMargin: 20,
                rightMargin: 20,
                topMargin: 20,
                bottomMargin: 20,
                orientation: 'Portrait'
            }
        };

        function switchBuilderMode(mode) {
            const formSection = document.getElementById('formBuilderSection');
            const visualSection = document.getElementById('visualBuilderSection');
            const formBtn = document.getElementById('formBuilderBtn');
            const visualBtn = document.getElementById('visualBuilderBtn');

            if (mode === 'form') {
                formSection.style.display = 'block';
                visualSection.style.display = 'none';
                formBtn.style.background = '#667eea';
                visualBtn.style.background = '#6c757d';
            } else {
                formSection.style.display = 'none';
                visualSection.style.display = 'block';
                formBtn.style.background = '#6c757d';
                visualBtn.style.background = '#667eea';
                // Initialize visual builder
                setTimeout(() => VB.init(), 100);
            }
        }

        function loadVisualBuilderDatasources() {
            fetch('/api/datasources')
                .then(response => response.json())
                .then(datasources => {
                    const select = document.getElementById('visualDatasource');
                    select.innerHTML = '<option value="">-- Select datasource --</option>';
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

        function loadVisualBuilderTables() {
            const datasourceSelect = document.getElementById('visualDatasource');
            const datasourceId = datasourceSelect.value;
            const tableSelect = document.getElementById('visualTable');

            tableSelect.innerHTML = '<option value="">-- Select table --</option>';
            document.getElementById('visualFieldsList').style.display = 'none';
            visualBuilder.datasourceId = datasourceId ? parseInt(datasourceId) : null;
            visualBuilder.availableFields = [];

            if (!datasourceId) return;

            fetch(`/api/builder/datasources/${datasourceId}/tables`)
                .then(response => response.json())
                .then(data => {
                    if (data.success && data.tables) {
                        data.tables.forEach(table => {
                            const option = document.createElement('option');
                            option.value = table;
                            option.textContent = table;
                            tableSelect.appendChild(option);
                        });
                    }
                })
                .catch(error => {
                    console.error('Error loading tables:', error);
                    showMessage('Error loading tables: ' + error, 'error');
                });
        }

        function loadVisualBuilderColumns() {
            const tableSelect = document.getElementById('visualTable');
            const tableName = tableSelect.value;
            const datasourceId = visualBuilder.datasourceId;

            visualBuilder.tableName = tableName;
            visualBuilder.availableFields = [];
            document.getElementById('visualFieldsList').style.display = 'none';

            if (!datasourceId || !tableName) return;

            fetch(`/api/builder/datasources/${datasourceId}/tables/${tableName}/columns`)
                .then(response => response.json())
                .then(data => {
                    if (data.success && data.columns) {
                        visualBuilder.availableFields = data.columns;
                        displayVisualBuilderFields();
                    }
                })
                .catch(error => {
                    console.error('Error loading columns:', error);
                    showMessage('Error loading columns: ' + error, 'error');
                });
        }

        function displayVisualBuilderFields() {
            const container = document.getElementById('visualFieldsContainer');
            const fieldsList = document.getElementById('visualFieldsList');

            if (visualBuilder.availableFields.length === 0) {
                fieldsList.style.display = 'none';
                return;
            }

            container.innerHTML = '';
            visualBuilder.availableFields.forEach(field => {
                const badge = document.createElement('span');
                badge.style.cssText = 'padding: 5px 10px; background: #667eea; color: white; border-radius: 4px; font-size: 12px; cursor: pointer;';
                badge.textContent = field.name;
                badge.title = `${field.type} - Drag to canvas or click to add`;
                badge.draggable = true;
                badge.onclick = () => addFieldElement(field);
                badge.ondragstart = (e) => {
                    e.dataTransfer.effectAllowed = 'copy';
                    e.dataTransfer.setData('elementType', 'field');
                    e.dataTransfer.setData('fieldName', field.name);
                    e.dataTransfer.setData('fieldType', field.type);
                };
                container.appendChild(badge);
            });

            fieldsList.style.display = 'block';
        }

        function addFieldElement(field) {
            const band = document.getElementById('currentBand').value;
            const element = {
                id: visualBuilder.nextId++,
                type: 'field',
                band: band,
                x: 50 + (visualBuilder.elements.filter(e => e.band === band).length * 20),
                y: 50 + (visualBuilder.elements.filter(e => e.band === band).length * 20),
                width: 100,
                height: 20,
                text: '',
                fieldName: field.name,
                fieldType: field.type,
                fontName: 'Arial',
                fontSize: 10,
                bold: false,
                italic: false,
                alignment: 'Left',
                color: '#000000'
            };

            visualBuilder.elements.push(element);
            renderCanvas();
            selectElement(element.id);
            showMessage(`Field "${field.name}" added to canvas`, 'success');
        }

        function addElement(type) {
            const band = document.getElementById('currentBand').value;

            let element = {
                id: visualBuilder.nextId++,
                type: type,
                band: band,
                x: 50,
                y: 50,
                width: 100,
                height: 30,
                fontName: 'Arial',
                fontSize: 12,
                bold: false,
                italic: false,
                alignment: 'Left',
                color: '#000000'
            };

            switch(type) {
                case 'text':
                    element.text = 'Text Label';
                    break;
                case 'line':
                    element.width = 200;
                    element.height = 2;
                    break;
                case 'rectangle':
                    element.width = 150;
                    element.height = 100;
                    break;
                case 'image':
                    element.imagePath = '';
                    element.text = '';
                    break;
                case 'pageNumber':
                    element.text = 'Page ';
                    element.expression = '$V{PAGE_NUMBER}';
                    break;
                case 'currentDate':
                    element.text = '';
                    element.expression = 'new java.util.Date()';
                    element.pattern = 'dd/MM/yyyy';
                    break;
                case 'field':
                    element.fieldName = 'fieldName';
                    element.fieldType = 'String';
                    element.height = 20;
                    element.fontSize = 10;
                    break;
                default:
                    element.text = type.toUpperCase();
            }

            visualBuilder.elements.push(element);
            renderCanvas();
            selectElement(element.id);
        }

        function renderCanvas() {
            const canvas = document.getElementById('visualCanvas');
            canvas.innerHTML = '';

            const currentBand = document.getElementById('currentBand').value;
            document.getElementById('bandIndicator').textContent = currentBand.charAt(0).toUpperCase() + currentBand.slice(1);

            // Filter elements by current band
            const bandElements = visualBuilder.elements.filter(el => el.band === currentBand);

            if (bandElements.length === 0) {
                canvas.innerHTML = `
                    <div style="text-align: center; padding: 40px; color: #999;">
                        <p style="font-size: 24px;">📄</p>
                        <p>Drag elements from the toolbox to start designing</p>
                        <p style="font-size: 12px; margin-top: 10px;">Current Band: ${currentBand.charAt(0).toUpperCase() + currentBand.slice(1)}</p>
                    </div>
                `;
                return;
            }

            bandElements.forEach(element => {
                const div = document.createElement('div');
                div.className = 'canvas-element';
                if (visualBuilder.selectedElement === element.id) {
                    div.classList.add('selected');
                }
                div.style.left = element.x + 'px';
                div.style.top = element.y + 'px';
                div.style.width = element.width + 'px';
                div.style.height = element.height + 'px';

                // Content based on type
                if (element.type === 'text') {
                    div.textContent = element.text;
                    div.style.fontFamily = element.fontName;
                    div.style.fontSize = element.fontSize + 'px';
                    div.style.fontWeight = element.bold ? 'bold' : 'normal';
                    div.style.fontStyle = element.italic ? 'italic' : 'normal';
                    div.style.textAlign = element.alignment.toLowerCase();
                    div.style.color = element.color;
                } else if (element.type === 'field') {
                    div.textContent = '$F{' + (element.fieldName || 'fieldName') + '}';
                    div.style.fontFamily = element.fontName;
                    div.style.fontSize = element.fontSize + 'px';
                    div.style.fontWeight = element.bold ? 'bold' : 'normal';
                    div.style.fontStyle = element.italic ? 'italic' : 'normal';
                    div.style.textAlign = element.alignment.toLowerCase();
                    div.style.color = element.color;
                    div.style.background = 'rgba(40, 167, 69, 0.1)';
                } else if (element.type === 'pageNumber') {
                    div.textContent = element.text + '$V{PAGE_NUMBER}';
                    div.style.fontFamily = element.fontName;
                    div.style.fontSize = element.fontSize + 'px';
                    div.style.textAlign = element.alignment.toLowerCase();
                    div.style.background = 'rgba(23, 162, 184, 0.1)';
                } else if (element.type === 'currentDate') {
                    const now = new Date();
                    div.textContent = now.toLocaleDateString();
                    div.style.fontFamily = element.fontName;
                    div.style.fontSize = element.fontSize + 'px';
                    div.style.textAlign = element.alignment.toLowerCase();
                    div.style.background = 'rgba(255, 193, 7, 0.1)';
                } else if (element.type === 'image') {
                    if (element.imagePath) {
                        const img = document.createElement('img');
                        img.src = '/' + element.imagePath;
                        img.style.width = '100%';
                        img.style.height = '100%';
                        img.style.objectFit = 'contain';
                        div.appendChild(img);
                    } else {
                        div.textContent = '🖼️ Image';
                        div.style.display = 'flex';
                        div.style.alignItems = 'center';
                        div.style.justifyContent = 'center';
                    }
                } else if (element.type === 'line') {
                    div.style.background = '#000';
                } else if (element.type === 'rectangle') {
                    div.style.background = 'transparent';
                    div.style.border = '2px solid #000';
                } else {
                    div.textContent = element.type.toUpperCase();
                }

                div.onclick = (e) => {
                    e.stopPropagation();
                    selectElement(element.id);
                };

                div.onmousedown = (e) => startDrag(e, element.id);

                canvas.appendChild(div);
            });
        }

        let dragState = null;

        function startDrag(e, elementId) {
            e.stopPropagation();
            const element = visualBuilder.elements.find(el => el.id === elementId);
            if (!element) return;

            dragState = {
                elementId: elementId,
                startX: e.clientX,
                startY: e.clientY,
                originalX: element.x,
                originalY: element.y
            };

            document.onmousemove = continueDrag;
            document.onmouseup = endDrag;
        }

        function continueDrag(e) {
            if (!dragState) return;

            const deltaX = e.clientX - dragState.startX;
            const deltaY = e.clientY - dragState.startY;

            const element = visualBuilder.elements.find(el => el.id === dragState.elementId);
            if (element) {
                element.x = Math.max(0, dragState.originalX + deltaX);
                element.y = Math.max(0, dragState.originalY + deltaY);
                renderCanvas();
            }
        }

        function endDrag() {
            dragState = null;
            document.onmousemove = null;
            document.onmouseup = null;
        }

        function selectElement(elementId) {
            visualBuilder.selectedElement = elementId;
            renderCanvas();
            updatePropertiesPanel();
        }

        function updatePropertiesPanel() {
            const panel = document.getElementById('propertiesPanel');

            if (!visualBuilder.selectedElement) {
                panel.innerHTML = '<p style="color: #999; font-size: 13px;">Select an element to edit its properties</p>';
                return;
            }

            const element = visualBuilder.elements.find(el => el.id === visualBuilder.selectedElement);
            if (!element) return;

            let html = `
                <div style="margin-bottom: 15px;">
                    <strong style="color: #667eea;">${element.type.toUpperCase()}</strong>
                    <button onclick="deleteSelectedElement()" style="float: right; background: #dc3545; color: white; border: none; padding: 4px 8px; border-radius: 4px; cursor: pointer; font-size: 11px;">🗑️ Delete</button>
                </div>

                <div class="form-group">
                    <label style="font-size: 12px;">Position X:</label>
                    <input type="number" value="${element.x}" onchange="updateElementProperty('x', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                </div>

                <div class="form-group">
                    <label style="font-size: 12px;">Position Y:</label>
                    <input type="number" value="${element.y}" onchange="updateElementProperty('y', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                </div>

                <div class="form-group">
                    <label style="font-size: 12px;">Width:</label>
                    <input type="number" value="${element.width}" onchange="updateElementProperty('width', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                </div>

                <div class="form-group">
                    <label style="font-size: 12px;">Height:</label>
                    <input type="number" value="${element.height}" onchange="updateElementProperty('height', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                </div>
            `;

            if (element.type === 'text') {
                html += `
                    <div class="form-group">
                        <label style="font-size: 12px;">Text:</label>
                        <textarea onchange="updateElementProperty('text', this.value)" style="width: 100%; padding: 5px; font-size: 12px; min-height: 60px;">${element.text}</textarea>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Font:</label>
                        <select onchange="updateElementProperty('fontName', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                            <option ${element.fontName === 'Arial' ? 'selected' : ''}>Arial</option>
                            <option ${element.fontName === 'Times New Roman' ? 'selected' : ''}>Times New Roman</option>
                            <option ${element.fontName === 'Courier' ? 'selected' : ''}>Courier</option>
                            <option ${element.fontName === 'Helvetica' ? 'selected' : ''}>Helvetica</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Font Size:</label>
                        <input type="number" value="${element.fontSize}" onchange="updateElementProperty('fontSize', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px; display: flex; align-items: center;">
                            <input type="checkbox" ${element.bold ? 'checked' : ''} onchange="updateElementProperty('bold', this.checked)" style="margin-right: 5px;">
                            Bold
                        </label>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px; display: flex; align-items: center;">
                            <input type="checkbox" ${element.italic ? 'checked' : ''} onchange="updateElementProperty('italic', this.checked)" style="margin-right: 5px;">
                            Italic
                        </label>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Alignment:</label>
                        <select onchange="updateElementProperty('alignment', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                            <option ${element.alignment === 'Left' ? 'selected' : ''}>Left</option>
                            <option ${element.alignment === 'Center' ? 'selected' : ''}>Center</option>
                            <option ${element.alignment === 'Right' ? 'selected' : ''}>Right</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Color:</label>
                        <input type="color" value="${element.color}" onchange="updateElementProperty('color', this.value)" style="width: 100%; height: 35px;">
                    </div>
                `;
            } else if (element.type === 'field') {
                html += `
                    <div class="form-group">
                        <label style="font-size: 12px;">Field Name:</label>
                        <input type="text" value="${element.fieldName || ''}" onchange="updateElementProperty('fieldName', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Field Type:</label>
                        <select onchange="updateElementProperty('fieldType', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                            <option ${element.fieldType === 'String' ? 'selected' : ''}>String</option>
                            <option ${element.fieldType === 'Integer' ? 'selected' : ''}>Integer</option>
                            <option ${element.fieldType === 'Long' ? 'selected' : ''}>Long</option>
                            <option ${element.fieldType === 'Double' ? 'selected' : ''}>Double</option>
                            <option ${element.fieldType === 'BigDecimal' ? 'selected' : ''}>BigDecimal</option>
                            <option ${element.fieldType === 'Date' ? 'selected' : ''}>Date</option>
                            <option ${element.fieldType === 'Boolean' ? 'selected' : ''}>Boolean</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Font:</label>
                        <select onchange="updateElementProperty('fontName', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                            <option ${element.fontName === 'Arial' ? 'selected' : ''}>Arial</option>
                            <option ${element.fontName === 'Times New Roman' ? 'selected' : ''}>Times New Roman</option>
                            <option ${element.fontName === 'Courier' ? 'selected' : ''}>Courier</option>
                            <option ${element.fontName === 'Helvetica' ? 'selected' : ''}>Helvetica</option>
                        </select>
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Font Size:</label>
                        <input type="number" value="${element.fontSize}" onchange="updateElementProperty('fontSize', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                    </div>

                    <div class="form-group">
                        <label style="font-size: 12px;">Alignment:</label>
                        <select onchange="updateElementProperty('alignment', this.value)" style="width: 100%; padding: 5px; font-size: 12px;">
                            <option ${element.alignment === 'Left' ? 'selected' : ''}>Left</option>
                            <option ${element.alignment === 'Center' ? 'selected' : ''}>Center</option>
                            <option ${element.alignment === 'Right' ? 'selected' : ''}>Right</option>
                        </select>
                    </div>
                `;
            } else if (element.type === 'image') {
                html += `
                    <div class="form-group">
                        <label style="font-size: 12px;">Image:</label>
                        <button onclick="selectImageForElement()" class="btn" style="width: 100%; padding: 8px; font-size: 12px; margin-top: 5px;">Select Image</button>
                        ${element.imagePath ? `<p style="font-size: 11px; color: #666; margin-top: 5px;">${element.imagePath}</p>` : ''}
                    </div>
                `;
            }

            panel.innerHTML = html;
        }

        function updateElementProperty(property, value) {
            const element = visualBuilder.elements.find(el => el.id === visualBuilder.selectedElement);
            if (!element) return;

            // Convert to appropriate type
            if (property === 'x' || property === 'y' || property === 'width' || property === 'height' || property === 'fontSize') {
                value = parseInt(value);
            }

            element[property] = value;
            renderCanvas();
        }

        function deleteSelectedElement() {
            if (!visualBuilder.selectedElement) return;

            visualBuilder.elements = visualBuilder.elements.filter(el => el.id !== visualBuilder.selectedElement);
            visualBuilder.selectedElement = null;
            renderCanvas();
            updatePropertiesPanel();
        }

        function handleToolboxDrag(e, elementType) {
            e.dataTransfer.effectAllowed = 'copy';
            e.dataTransfer.setData('text/plain', elementType); // Use standard MIME type
            e.dataTransfer.setData('elementType', elementType);
            // Add a visual feedback
            e.target.style.opacity = '0.5';
        }

        function handleToolboxDragEnd(e) {
            // Reset opacity after drag ends
            e.target.style.opacity = '1';
        }

        function handleDragOver(e) {
            e.preventDefault();
            e.dataTransfer.dropEffect = 'copy';
            return false; // Ensure drop is allowed
        }

        function handleDrop(e) {
            e.preventDefault();
            e.stopPropagation();

            // Try both data formats
            let elementType = e.dataTransfer.getData('elementType');
            if (!elementType) {
                elementType = e.dataTransfer.getData('text/plain');
            }

            if (!elementType) {
                console.error('No element type found in drop event');
                return;
            }

            console.log('Dropping element:', elementType);

            // Get canvas position
            const canvas = document.getElementById('visualCanvas');
            const rect = canvas.getBoundingClientRect();
            const x = e.clientX - rect.left;
            const y = e.clientY - rect.top;

            // Special handling for field elements with specific field data
            if (elementType === 'field') {
                const fieldName = e.dataTransfer.getData('fieldName');
                const fieldType = e.dataTransfer.getData('fieldType');
                if (fieldName && fieldType) {
                    addFieldElementAtPosition(fieldName, fieldType, Math.max(0, x - 50), Math.max(0, y - 10));
                    return;
                }
            }

            // Add element at drop position
            addElementAtPosition(elementType, Math.max(0, x - 50), Math.max(0, y - 15));
        }

        function addFieldElementAtPosition(fieldName, fieldType, x, y) {
            const band = document.getElementById('currentBand').value;
            const element = {
                id: visualBuilder.nextId++,
                type: 'field',
                band: band,
                x: x,
                y: y,
                width: 100,
                height: 20,
                text: '',
                fieldName: fieldName,
                fieldType: fieldType,
                fontName: 'Arial',
                fontSize: 10,
                bold: false,
                italic: false,
                alignment: 'Left',
                color: '#000000'
            };

            visualBuilder.elements.push(element);
            renderCanvas();
            selectElement(element.id);
        }

        function addElementAtPosition(type, x, y) {
            const band = document.getElementById('currentBand').value;

            let element = {
                id: visualBuilder.nextId++,
                type: type,
                band: band,
                x: x,
                y: y,
                width: 100,
                height: 30,
                fontName: 'Arial',
                fontSize: 12,
                bold: false,
                italic: false,
                alignment: 'Left',
                color: '#000000'
            };

            switch(type) {
                case 'text':
                    element.text = 'Text Label';
                    break;
                case 'line':
                    element.width = 200;
                    element.height = 2;
                    break;
                case 'rectangle':
                    element.width = 150;
                    element.height = 100;
                    break;
                case 'image':
                    element.imagePath = '';
                    element.text = '';
                    break;
                case 'pageNumber':
                    element.text = 'Page ';
                    element.expression = '$V{PAGE_NUMBER}';
                    break;
                case 'currentDate':
                    element.text = '';
                    element.expression = 'new java.util.Date()';
                    element.pattern = 'dd/MM/yyyy';
                    break;
                case 'field':
                    element.fieldName = 'fieldName';
                    element.fieldType = 'String';
                    element.height = 20;
                    element.fontSize = 10;
                    break;
                default:
                    element.text = type.toUpperCase();
            }

            visualBuilder.elements.push(element);
            renderCanvas();
            selectElement(element.id);
        }

        // Change band listener
        document.addEventListener('DOMContentLoaded', function() {
            const bandSelect = document.getElementById('currentBand');
            if (bandSelect) {
                bandSelect.addEventListener('change', function() {
                    visualBuilder.currentBand = this.value;
                    visualBuilder.selectedElement = null;
                    renderCanvas();
                    updatePropertiesPanel();
                });
            }
        });
