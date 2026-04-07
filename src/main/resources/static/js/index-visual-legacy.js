// ========== Visual Builder Functions ==========

        function clearElement(element) {
            if (element) {
                element.replaceChildren();
            }
        }

        function createLegacyEmptyState(message, iconText) {
            const wrapper = document.createElement('div');
            wrapper.style.textAlign = 'center';
            wrapper.style.padding = '40px';
            wrapper.style.color = '#999';

            const icon = document.createElement('p');
            icon.style.fontSize = '24px';
            icon.textContent = iconText || '📄';

            const text = document.createElement('p');
            text.textContent = message;

            wrapper.appendChild(icon);
            wrapper.appendChild(text);
            return wrapper;
        }

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
            const coverSection = document.getElementById('coverBuilderSection');
            const formBtn = document.getElementById('formBuilderBtn');
            const visualBtn = document.getElementById('visualBuilderBtn');
            const coverBtn = document.getElementById('coverBuilderBtn');

            if (mode === 'form') {
                formSection.style.display = 'block';
                visualSection.style.display = 'none';
                if (coverSection) {
                    coverSection.style.display = 'none';
                }
                formBtn.style.background = '#667eea';
                visualBtn.style.background = '#6c757d';
                if (coverBtn) {
                    coverBtn.style.background = '#6c757d';
                }
            } else if (mode === 'cover') {
                formSection.style.display = 'none';
                visualSection.style.display = 'none';
                if (coverSection) {
                    coverSection.style.display = 'block';
                }
                formBtn.style.background = '#6c757d';
                visualBtn.style.background = '#6c757d';
                if (coverBtn) {
                    coverBtn.style.background = '#667eea';
                }

                if (typeof vbToggleCoverOptions === 'function') {
                    vbToggleCoverOptions();
                }
                if (typeof vbRenderCoverLivePreview === 'function') {
                    vbRenderCoverLivePreview();
                }
            } else {
                formSection.style.display = 'none';
                visualSection.style.display = 'block';
                if (coverSection) {
                    coverSection.style.display = 'none';
                }
                formBtn.style.background = '#6c757d';
                visualBtn.style.background = '#667eea';
                if (coverBtn) {
                    coverBtn.style.background = '#6c757d';
                }
                // Initialize visual builder
                setTimeout(() => VB.init(), 100);
            }
        }

        function loadVisualBuilderDatasources() {
            fetch('/api/datasources')
                .then(response => response.json())
                .then(datasources => {
                    const select = document.getElementById('visualDatasource');
                    const placeholder = document.createElement('option');
                    placeholder.value = '';
                    placeholder.textContent = '-- Select datasource --';
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

        function loadVisualBuilderTables() {
            const datasourceSelect = document.getElementById('visualDatasource');
            const datasourceId = datasourceSelect.value;
            const tableSelect = document.getElementById('visualTable');

            const placeholder = document.createElement('option');
            placeholder.value = '';
            placeholder.textContent = '-- Select table --';
            tableSelect.replaceChildren(placeholder);
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

            clearElement(container);
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
            clearElement(canvas);

            const currentBand = document.getElementById('currentBand').value;
            document.getElementById('bandIndicator').textContent = currentBand.charAt(0).toUpperCase() + currentBand.slice(1);

            // Filter elements by current band
            const bandElements = visualBuilder.elements.filter(el => el.band === currentBand);

            if (bandElements.length === 0) {
                const emptyState = createLegacyEmptyState(
                    'Drag elements from the toolbox to start designing',
                    '📄'
                );
                const bandInfo = document.createElement('p');
                bandInfo.style.fontSize = '12px';
                bandInfo.style.marginTop = '10px';
                bandInfo.textContent = `Current Band: ${currentBand.charAt(0).toUpperCase() + currentBand.slice(1)}`;
                emptyState.appendChild(bandInfo);
                canvas.appendChild(emptyState);
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
                clearElement(panel);
                const empty = document.createElement('p');
                empty.style.color = '#999';
                empty.style.fontSize = '13px';
                empty.textContent = 'Select an element to edit its properties';
                panel.appendChild(empty);
                return;
            }

            const element = visualBuilder.elements.find(el => el.id === visualBuilder.selectedElement);
            if (!element) return;

            clearElement(panel);

            const header = document.createElement('div');
            header.style.marginBottom = '15px';

            const typeLabel = document.createElement('strong');
            typeLabel.style.color = '#667eea';
            typeLabel.textContent = element.type.toUpperCase();

            const deleteButton = document.createElement('button');
            deleteButton.type = 'button';
            deleteButton.textContent = '🗑️ Delete';
            deleteButton.style.float = 'right';
            deleteButton.style.background = '#dc3545';
            deleteButton.style.color = 'white';
            deleteButton.style.border = 'none';
            deleteButton.style.padding = '4px 8px';
            deleteButton.style.borderRadius = '4px';
            deleteButton.style.cursor = 'pointer';
            deleteButton.style.fontSize = '11px';
            deleteButton.addEventListener('click', deleteSelectedElement);

            header.appendChild(typeLabel);
            header.appendChild(deleteButton);
            panel.appendChild(header);

            const createGroup = (labelText, control) => {
                const group = document.createElement('div');
                group.className = 'form-group';
                const label = document.createElement('label');
                label.style.fontSize = '12px';
                label.textContent = labelText;
                group.appendChild(label);
                group.appendChild(control);
                return group;
            };

            const createInput = (value, type, property) => {
                const input = document.createElement('input');
                input.type = type;
                input.value = value;
                input.style.width = '100%';
                input.style.padding = '5px';
                input.style.fontSize = '12px';
                input.addEventListener('change', () => updateElementProperty(property, type === 'checkbox' ? input.checked : input.value));
                return input;
            };

            panel.appendChild(createGroup('Position X:', createInput(element.x, 'number', 'x')));
            panel.appendChild(createGroup('Position Y:', createInput(element.y, 'number', 'y')));
            panel.appendChild(createGroup('Width:', createInput(element.width, 'number', 'width')));
            panel.appendChild(createGroup('Height:', createInput(element.height, 'number', 'height')));

            if (element.type === 'text') {
                const textArea = document.createElement('textarea');
                textArea.style.width = '100%';
                textArea.style.padding = '5px';
                textArea.style.fontSize = '12px';
                textArea.style.minHeight = '60px';
                textArea.value = element.text;
                textArea.addEventListener('change', () => updateElementProperty('text', textArea.value));
                panel.appendChild(createGroup('Text:', textArea));

                const fontSelect = document.createElement('select');
                fontSelect.style.width = '100%';
                fontSelect.style.padding = '5px';
                fontSelect.style.fontSize = '12px';
                ['Arial', 'Times New Roman', 'Courier', 'Helvetica'].forEach(font => {
                    const option = document.createElement('option');
                    option.textContent = font;
                    if (element.fontName === font) option.selected = true;
                    fontSelect.appendChild(option);
                });
                fontSelect.addEventListener('change', () => updateElementProperty('fontName', fontSelect.value));
                panel.appendChild(createGroup('Font:', fontSelect));

                panel.appendChild(createGroup('Font Size:', createInput(element.fontSize, 'number', 'fontSize')));

                const boldGroup = document.createElement('div');
                boldGroup.className = 'form-group';
                const boldLabel = document.createElement('label');
                boldLabel.style.fontSize = '12px';
                boldLabel.style.display = 'flex';
                boldLabel.style.alignItems = 'center';
                const boldCheck = document.createElement('input');
                boldCheck.type = 'checkbox';
                boldCheck.checked = !!element.bold;
                boldCheck.style.marginRight = '5px';
                boldCheck.addEventListener('change', () => updateElementProperty('bold', boldCheck.checked));
                boldLabel.appendChild(boldCheck);
                boldLabel.appendChild(document.createTextNode('Bold'));
                boldGroup.appendChild(boldLabel);
                panel.appendChild(boldGroup);

                const italicGroup = document.createElement('div');
                italicGroup.className = 'form-group';
                const italicLabel = document.createElement('label');
                italicLabel.style.fontSize = '12px';
                italicLabel.style.display = 'flex';
                italicLabel.style.alignItems = 'center';
                const italicCheck = document.createElement('input');
                italicCheck.type = 'checkbox';
                italicCheck.checked = !!element.italic;
                italicCheck.style.marginRight = '5px';
                italicCheck.addEventListener('change', () => updateElementProperty('italic', italicCheck.checked));
                italicLabel.appendChild(italicCheck);
                italicLabel.appendChild(document.createTextNode('Italic'));
                italicGroup.appendChild(italicLabel);
                panel.appendChild(italicGroup);

                const alignmentSelect = document.createElement('select');
                alignmentSelect.style.width = '100%';
                alignmentSelect.style.padding = '5px';
                alignmentSelect.style.fontSize = '12px';
                ['Left', 'Center', 'Right'].forEach(alignment => {
                    const option = document.createElement('option');
                    option.textContent = alignment;
                    if (element.alignment === alignment) option.selected = true;
                    alignmentSelect.appendChild(option);
                });
                alignmentSelect.addEventListener('change', () => updateElementProperty('alignment', alignmentSelect.value));
                panel.appendChild(createGroup('Alignment:', alignmentSelect));

                const colorInput = document.createElement('input');
                colorInput.type = 'color';
                colorInput.value = element.color;
                colorInput.style.width = '100%';
                colorInput.style.height = '35px';
                colorInput.addEventListener('change', () => updateElementProperty('color', colorInput.value));
                panel.appendChild(createGroup('Color:', colorInput));
            } else if (element.type === 'field') {
                const fieldNameInput = createInput(element.fieldName || '', 'text', 'fieldName');
                panel.appendChild(createGroup('Field Name:', fieldNameInput));

                const fieldTypeSelect = document.createElement('select');
                fieldTypeSelect.style.width = '100%';
                fieldTypeSelect.style.padding = '5px';
                fieldTypeSelect.style.fontSize = '12px';
                ['String', 'Integer', 'Long', 'Double', 'BigDecimal', 'Date', 'Boolean'].forEach(typeName => {
                    const option = document.createElement('option');
                    option.textContent = typeName;
                    if (element.fieldType === typeName) option.selected = true;
                    fieldTypeSelect.appendChild(option);
                });
                fieldTypeSelect.addEventListener('change', () => updateElementProperty('fieldType', fieldTypeSelect.value));
                panel.appendChild(createGroup('Field Type:', fieldTypeSelect));

                const fontSelect = document.createElement('select');
                fontSelect.style.width = '100%';
                fontSelect.style.padding = '5px';
                fontSelect.style.fontSize = '12px';
                ['Arial', 'Times New Roman', 'Courier', 'Helvetica'].forEach(font => {
                    const option = document.createElement('option');
                    option.textContent = font;
                    if (element.fontName === font) option.selected = true;
                    fontSelect.appendChild(option);
                });
                fontSelect.addEventListener('change', () => updateElementProperty('fontName', fontSelect.value));
                panel.appendChild(createGroup('Font:', fontSelect));

                panel.appendChild(createGroup('Font Size:', createInput(element.fontSize, 'number', 'fontSize')));

                const alignmentSelect = document.createElement('select');
                alignmentSelect.style.width = '100%';
                alignmentSelect.style.padding = '5px';
                alignmentSelect.style.fontSize = '12px';
                ['Left', 'Center', 'Right'].forEach(alignment => {
                    const option = document.createElement('option');
                    option.textContent = alignment;
                    if (element.alignment === alignment) option.selected = true;
                    alignmentSelect.appendChild(option);
                });
                alignmentSelect.addEventListener('change', () => updateElementProperty('alignment', alignmentSelect.value));
                panel.appendChild(createGroup('Alignment:', alignmentSelect));
            } else if (element.type === 'image') {
                const imageGroup = document.createElement('div');
                imageGroup.className = 'form-group';

                const label = document.createElement('label');
                label.style.fontSize = '12px';
                label.textContent = 'Image:';

                const button = document.createElement('button');
                button.type = 'button';
                button.className = 'btn';
                button.style.width = '100%';
                button.style.padding = '8px';
                button.style.fontSize = '12px';
                button.style.marginTop = '5px';
                button.textContent = 'Select Image';
                button.addEventListener('click', selectImageForElement);

                imageGroup.appendChild(label);
                imageGroup.appendChild(button);

                if (element.imagePath) {
                    const pathInfo = document.createElement('p');
                    pathInfo.style.fontSize = '11px';
                    pathInfo.style.color = '#666';
                    pathInfo.style.marginTop = '5px';
                    pathInfo.textContent = element.imagePath;
                    imageGroup.appendChild(pathInfo);
                }

                panel.appendChild(imageGroup);
            }
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
