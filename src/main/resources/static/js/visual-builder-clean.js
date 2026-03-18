// ========== CLEAN VISUAL BUILDER ==========

const VB = {
    canvas: null,
    elements: [],
    selectedId: null,
    nextId: 1,
    orientation: 'portrait',
    dragState: null,
    datasourceId: null,
    availableTables: [],
    tableColumns: {},
    tableColumnDetails: {},
    initialized: false,

    init() {
        if (this.initialized) {
            this.loadDatasources();
            this.applyOrientation();
            this.render();
            return;
        }

        this.canvas = document.getElementById('vbCanvas');
        if (!this.canvas) {
            console.error('Canvas not found!');
            return;
        }

        // Hint password-manager overlays to ignore the visual canvas subtree.
        this.canvas.setAttribute('data-bwignore', 'true');
        this.canvas.setAttribute('data-1p-ignore', 'true');
        this.canvas.setAttribute('data-lpignore', 'true');

        const studioSection = document.getElementById('visualBuilderSection');
        if (studioSection) {
            studioSection.setAttribute('data-bwignore', 'true');
            studioSection.setAttribute('data-1p-ignore', 'true');
            studioSection.setAttribute('data-lpignore', 'true');
        }

        this.setupCanvasDragDrop();
        this.loadDatasources();
        this.applyOrientation();
        this.render();
        this.initialized = true;
    },

    getCanvasLayout() {
        const isLandscape = this.orientation === 'landscape';
        const totalHeight = isLandscape ? 700 : 960;
        const headerHeight = Math.round(totalHeight * 0.18);
        const footerHeight = Math.round(totalHeight * 0.16);
        const bodyHeight = totalHeight - headerHeight - footerHeight;

        return {
            totalHeight,
            headerHeight,
            bodyHeight,
            footerHeight,
            bodyTop: headerHeight,
            footerTop: headerHeight + bodyHeight
        };
    },

    getDefaultYForBand(band) {
        const layout = this.getCanvasLayout();
        if (band === 'title' || band === 'pageHeader') return 20;
        if (band === 'footer' || band === 'pageFooter') return layout.footerTop + 20;
        return layout.bodyTop + 20;
    },

    resolveBandFromY(y) {
        const layout = this.getCanvasLayout();
        if (y < layout.bodyTop) return 'pageHeader';
        if (y >= layout.footerTop) return 'pageFooter';
        return 'detail';
    },

    normalizeElementPosition(element) {
        if (!this.canvas || !element) return;

        const layout = this.getCanvasLayout();
        const maxX = Math.max(0, this.canvas.clientWidth - element.width - 10);
        const maxY = Math.max(0, layout.totalHeight - element.height);

        element.x = Math.min(maxX, Math.max(0, element.x));
        element.y = Math.min(maxY, Math.max(0, element.y));
        element.band = this.resolveBandFromY(element.y);
    },

    applyOrientation() {
        if (!this.canvas) return;

        const layout = this.getCanvasLayout();
        this.canvas.classList.toggle('vb-landscape', this.orientation === 'landscape');
        this.canvas.classList.toggle('vb-portrait', this.orientation !== 'landscape');
        this.canvas.style.minHeight = `${layout.totalHeight}px`;
        this.updateCanvasStatus();
    },

    updateCanvasStatus() {
        const label = document.getElementById('vbCanvasStatus');
        if (!label) return;
        const orientationLabel = this.orientation === 'landscape' ? 'Landscape' : 'Portrait';
        const nextStatus = `Orientation: ${orientationLabel} · Zones: Header / Body / Footer`;
        if (label.textContent !== nextStatus) {
            label.textContent = nextStatus;
        }
    },

    loadDatasources(preferredDatasourceId = null) {
        const select = document.getElementById('vbDatasourceSelect');
        if (!select) return;

        const desiredSelection = preferredDatasourceId != null
            ? String(preferredDatasourceId)
            : (select.value || (this.datasourceId != null ? String(this.datasourceId) : ''));

        fetch('/api/datasources')
            .then(response => response.json())
            .then(datasources => {
                select.innerHTML = '<option value="">Select Datasource</option>';

                const jdbcDatasources = datasources.filter(ds => ds.type === 'JDBC');
                jdbcDatasources.forEach(ds => {
                    const option = document.createElement('option');
                    option.value = ds.id;
                    option.textContent = `${ds.name} (${ds.type})`;
                    select.appendChild(option);
                });

                const hasDesiredSelection = desiredSelection
                    && jdbcDatasources.some(ds => String(ds.id) === desiredSelection);

                if (hasDesiredSelection) {
                    select.value = desiredSelection;
                    this.datasourceId = parseInt(desiredSelection, 10);
                    vbLoadTables();
                } else {
                    select.value = '';
                    this.datasourceId = null;
                    this.availableTables = [];

                    const tablesList = document.getElementById('vbTablesList');
                    if (tablesList) {
                        tablesList.innerHTML = '<div class="vb-table-list-empty">Select a datasource to load tables</div>';
                    }
                }
            })
            .catch(error => {
                console.error('Error loading datasources:', error);
            });
    },

    setupCanvasDragDrop() {
        if (!this.canvas) return;

        this.canvas.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.stopPropagation();
            e.dataTransfer.dropEffect = 'copy';
            this.canvas.classList.add('vb-drag-active');
        }, false);

        this.canvas.addEventListener('dragleave', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.canvas.classList.remove('vb-drag-active');
        }, false);

        this.canvas.addEventListener('drop', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.canvas.classList.remove('vb-drag-active');

            const elementType = e.dataTransfer.getData('elementType');
            const tableName = e.dataTransfer.getData('tableName');
            if (!elementType) {
                console.error('No elementType in drop data');
                return;
            }

            const rect = this.canvas.getBoundingClientRect();
            const scrollLeft = this.canvas.scrollLeft || 0;
            const scrollTop = this.canvas.scrollTop || 0;

            const x = Math.max(10, e.clientX - rect.left + scrollLeft - 60);
            const y = Math.max(10, e.clientY - rect.top + scrollTop - 12);

            if (elementType === 'dbTable' && tableName) {
                this.addElementAtPosition(elementType, x, y, { tableName });
            } else {
                this.addElementAtPosition(elementType, x, y);
            }
        }, false);
    },

    addElementAtPosition(type, x, y) {
        const id = this.nextId++;
        const targetBand = this.resolveBandFromY(y);

        const baseElement = {
            id,
            type,
            band: targetBand,
            x,
            y,
            width: 120,
            height: 25,
            text: this.getDefaultText(type),
            fontSize: 12,
            fontFamily: 'DejaVu Sans',
            color: '#111111',
            bold: false,
            italic: false,
            underline: false,
            hAlign: 'left',
            vAlign: 'middle',
            borderColor: '#7f90a3',
            borderWidth: 1,
            backgroundColor: '#ffffff',
            backgroundTransparent: true
        };

        if (type === 'line') {
            baseElement.height = 2;
            baseElement.borderWidth = 2;
            baseElement.backgroundTransparent = false;
            baseElement.backgroundColor = '#111111';
        }

        if (type === 'rectangle') {
            baseElement.width = 160;
            baseElement.height = 70;
            baseElement.backgroundTransparent = true;
        }

        if (type === 'logo') {
            baseElement.width = 150;
            baseElement.height = 60;
            baseElement.text = '';
        }

        const element = baseElement;
        this.normalizeElementPosition(element);

        this.elements.push(element);
        this.render();
        this.select(id);
    },

    addElement(type) {
        const id = this.nextId++;
        const x = 50 + (id % 3) * 40;
        const y = this.getDefaultYForBand('detail') + Math.floor(id / 3) * 30;

        this.addElementAtPosition(type, x, y);
    },

    getDefaultText(type) {
        const texts = {
            text: 'Text Label',
            label: 'Label',
            field: 'Field Name',
            line: '',
            rectangle: '',
            pageNumber: 'Page',
            date: 'Date',
            logo: ' ',
            table: ' '
        };
        return texts[type] || type;
    },

    getJustifyContent(hAlign) {
        if (hAlign === 'center') return 'center';
        if (hAlign === 'right') return 'flex-end';
        return 'flex-start';
    },

    getAlignItems(vAlign) {
        if (vAlign === 'top') return 'flex-start';
        if (vAlign === 'bottom') return 'flex-end';
        return 'center';
    },

    select(id) {
        this.selectedId = id;
        this.render();
        this.updateProperties();
    },

    delete() {
        if (!this.selectedId) return;
        this.elements = this.elements.filter((el) => el.id !== this.selectedId);
        this.selectedId = null;
        this.render();
        this.updateProperties();
    },

    render() {
        if (!this.canvas) {
            console.error('Canvas not initialized');
            return;
        }

        const placeholder = document.getElementById('vbPlaceholder');

        const elementDivs = this.canvas.querySelectorAll('.vb-element');
        elementDivs.forEach((div) => div.remove());

        const oldGuides = this.canvas.querySelectorAll('.vb-band-guide');
        oldGuides.forEach((guide) => guide.remove());
        this.renderBandGuides();

        if (this.elements.length === 0) {
            if (placeholder) placeholder.style.display = 'block';
            return;
        }

        if (placeholder) placeholder.style.display = 'none';

        this.elements.forEach((el) => {
            const div = document.createElement('div');
            div.className = 'vb-element' + (el.id === this.selectedId ? ' selected' : '');
            div.setAttribute('data-band', el.band || 'detail');
            div.style.left = el.x + 'px';
            div.style.top = el.y + 'px';
            div.style.width = el.width + 'px';
            div.style.height = el.height + 'px';
            div.style.fontSize = (el.fontSize || 12) + 'px';
            div.style.fontFamily = el.fontFamily || 'DejaVu Sans';
            div.style.fontWeight = el.bold ? '700' : '400';
            div.style.fontStyle = el.italic ? 'italic' : 'normal';
            div.style.textDecoration = el.underline ? 'underline' : 'none';
            div.style.color = el.color || '#111111';
            div.style.borderColor = el.borderColor || '#7f90a3';
            div.style.borderWidth = (el.borderWidth != null ? el.borderWidth : 1) + 'px';
            div.style.borderStyle = 'solid';
            div.style.background = el.backgroundTransparent ? 'transparent' : (el.backgroundColor || '#ffffff');
            div.style.display = 'flex';
            div.style.justifyContent = this.getJustifyContent(el.hAlign);
            div.style.alignItems = this.getAlignItems(el.vAlign);
            div.setAttribute('data-element-id', el.id);

            if (el.type === 'line') {
                const lineThickness = Math.max(1, el.borderWidth || 2);
                div.style.height = lineThickness + 'px';
                div.style.background = el.color || '#111111';
                div.style.border = 'none';
                div.style.padding = '0';
            } else if (el.type === 'rectangle') {
                div.style.justifyContent = 'center';
                div.style.alignItems = 'center';
            } else if (el.type === 'logo') {
                div.style.borderStyle = 'dashed';
                div.style.background = el.backgroundTransparent ? '#f4f7fb' : (el.backgroundColor || '#f4f7fb');
                if (el.imageData) {
                    div.innerHTML = `<img src="${el.imageData}" alt="Logo" style="width:100%;height:100%;object-fit:contain;pointer-events:none;">`;
                } else {
                    div.textContent = 'Logo';
                }
            } else if (el.type === 'table') {
                div.style.background = '#f9f9f9';
                div.style.border = '1px solid #ddd';
                div.textContent = 'Table';
                div.style.display = 'flex';
                div.style.alignItems = 'center';
                div.style.justifyContent = 'center';
            } else if (el.type === 'dbTable') {
                div.style.background = '#f8f9fa';
                div.style.border = `${Math.max(1, el.borderWidth || 1)}px solid ${el.borderColor || '#667eea'}`;
                div.style.overflow = 'hidden';
                div.style.padding = '5px';
                div.style.fontSize = (el.fontSize || 10) + 'px';
                div.style.display = 'block';
                
                // Create a mini preview of the table
                let tableHTML = `<div style="font-weight: bold; color: #43566d; margin-bottom: 3px;">${vbEscapeHtml(el.tableName || 'Table')}</div>`;
                if (el.selectedColumns && el.selectedColumns.length > 0) {
                    tableHTML += '<div style="font-size: 8px; color: #666;">';
                    el.selectedColumns.slice(0, 5).forEach(col => {
                        tableHTML += `<div style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${vbEscapeHtml(col)}</div>`;
                    });
                    if (el.selectedColumns.length > 5) {
                        tableHTML += '<div>...</div>';
                    }
                    tableHTML += '</div>';
                } else {
                    tableHTML += '<div style="font-size: 8px; color: #999;">No columns selected</div>';
                }
                div.innerHTML = tableHTML;
            } else {
                div.textContent = el.text;
            }

            div.onclick = (e) => {
                e.stopPropagation();
                this.select(el.id);
            };
            div.onmousedown = (e) => this.startDrag(e, el.id);

            this.canvas.appendChild(div);
        });
    },

    renderBandGuides() {
        if (!this.canvas) return;

        const layout = this.getCanvasLayout();
        const zones = [
            { key: 'title', label: 'Header', top: 0, height: layout.headerHeight },
            { key: 'detail', label: 'Body', top: layout.bodyTop, height: layout.bodyHeight },
            { key: 'footer', label: 'Footer', top: layout.footerTop, height: layout.footerHeight }
        ];

        zones.forEach((zone) => {
            const guide = document.createElement('div');
            guide.className = `vb-band-guide vb-band-${zone.key}`;
            guide.style.top = `${zone.top}px`;
            guide.style.height = `${zone.height}px`;
            guide.innerHTML = `<span class="vb-band-guide-label">${zone.label}</span>`;
            this.canvas.appendChild(guide);
        });
    },

    startDrag(e, elementId) {
        const element = this.elements.find((el) => el.id === elementId);
        if (!element) return;

        this.dragState = {
            elementId,
            startX: e.clientX,
            startY: e.clientY,
            originalX: element.x,
            originalY: element.y
        };

        document.onmousemove = (ev) => this.continueDrag(ev);
        document.onmouseup = () => this.endDrag();
    },

    continueDrag(e) {
        if (!VB.dragState) return;

        const element = VB.elements.find((el) => el.id === VB.dragState.elementId);
        if (!element) return;

        const deltaX = e.clientX - VB.dragState.startX;
        const deltaY = e.clientY - VB.dragState.startY;

        element.x = VB.dragState.originalX + deltaX;
        element.y = VB.dragState.originalY + deltaY;
        VB.normalizeElementPosition(element);

        // During drag, update only the moved node to avoid full canvas DOM churn.
        // This keeps drag smooth and reduces extension mutation side effects.
        const elementNode = VB.canvas?.querySelector(`.vb-element[data-element-id="${element.id}"]`);
        if (elementNode) {
            elementNode.style.left = element.x + 'px';
            elementNode.style.top = element.y + 'px';
            elementNode.setAttribute('data-band', element.band || 'detail');
        } else {
            VB.render();
        }
    },

    endDrag() {
        VB.dragState = null;
        document.onmousemove = null;
        document.onmouseup = null;
        VB.updateProperties(true);
    },

    updateProperties(preserveScroll = false) {
        const panel = document.getElementById('vbPropsPanel');
        if (!panel) return;

        const previousPanelScroll = preserveScroll ? panel.scrollTop : 0;
        const previousColumnScroll = preserveScroll
            ? (document.getElementById('vbColumnSelector')?.scrollTop || 0)
            : 0;

        if (!this.selectedId) {
            panel.innerHTML = '<div class="vb-props-empty">Select an element on the canvas to edit its properties.</div>';
            return;
        }

        const el = this.elements.find((item) => item.id === this.selectedId);
        if (!el) return;

        const textCapable = ['text', 'label', 'field', 'pageNumber', 'date'].includes(el.type);

        let html = `
            <div class="vb-prop-group">
                <div class="vb-prop-group-title">Element</div>
                <div class="vb-prop-field">
                    <label>Type</label>
                    <input type="text" value="${el.type}" disabled>
                </div>
                <div class="vb-prop-field">
                    <label>ID</label>
                    <input type="text" value="${el.id}" disabled>
                </div>
            </div>
        `;

        if (textCapable) {
            html += `
                <div class="vb-prop-group">
                    <div class="vb-prop-group-title">Content</div>
                    <div class="vb-prop-field">
                        <label>Text</label>
                        <input type="text" value="${el.text || ''}" onchange="VB.updateElement('text', this.value)">
                    </div>
                </div>
            `;
        }

        if (el.type === 'logo') {
            html += `
                <div class="vb-prop-group">
                    <div class="vb-prop-group-title">Image</div>
                    <button type="button" class="vb-toolbox-btn" onclick="document.getElementById('vbLogoInput').click()">Upload / Replace Image</button>
                </div>
            `;
        }

        if (el.type === 'dbTable') {
            html += `
                <div class="vb-prop-group">
                    <div class="vb-prop-group-title">Data Table</div>
                    <div class="vb-prop-field">
                        <label>Table Name</label>
                        <input type="text" value="${el.tableName || ''}" disabled>
                    </div>
                    <label class="vb-prop-check"><input type="checkbox" ${el.showHeaders ? 'checked' : ''} onchange="VB.updateElement('showHeaders', this.checked)">Show Headers</label>
                    <label class="vb-prop-check"><input type="checkbox" ${el.headerBold ? 'checked' : ''} onchange="VB.updateElement('headerBold', this.checked)">Bold Headers</label>
                    <label class="vb-prop-check"><input type="checkbox" ${el.alternateRows ? 'checked' : ''} onchange="VB.updateElement('alternateRows', this.checked)">Alternate Rows</label>
                    <div class="vb-prop-field">
                        <label>Columns to Display</label>
                        <div id="vbColumnSelector" style="max-height: 150px; overflow-y: auto; border: 1px solid #d2dbe6; border-radius: 4px; padding: 5px; font-size: 11px; background: #fff;">
                            ${el.columns ? el.columns.map((col) => `
                                <label class="vb-prop-check" style="margin-bottom: 4px;">
                                    <input type="checkbox" ${el.selectedColumns && el.selectedColumns.includes(col) ? 'checked' : ''}
                                           onchange="vbToggleColumn('${vbEscapeJsString(col)}', this.checked)">
                                    <span>${vbEscapeHtml(col)}</span>
                                </label>
                            `).join('') : '<div style="color:#8a96a3;">No columns found</div>'}
                        </div>
                    </div>
                </div>
            `;
        }

        html += `
            <div class="vb-prop-group">
                <div class="vb-prop-group-title">Geometry</div>
                <div class="vb-prop-row">
                    <div class="vb-prop-field">
                        <label>X</label>
                        <input type="number" value="${el.x}" min="0" onchange="VB.updateElement('x', this.value)">
                    </div>
                    <div class="vb-prop-field">
                        <label>Y</label>
                        <input type="number" value="${el.y}" min="0" onchange="VB.updateElement('y', this.value)">
                    </div>
                </div>
                <div class="vb-prop-row">
                    <div class="vb-prop-field">
                        <label>Width</label>
                        <input type="number" value="${el.width}" min="1" onchange="VB.updateElement('width', this.value)">
                    </div>
                    <div class="vb-prop-field">
                        <label>Height</label>
                        <input type="number" value="${el.height}" min="1" onchange="VB.updateElement('height', this.value)">
                    </div>
                </div>
            </div>
        `;

        if (el.type !== 'line') {
            html += `
                <div class="vb-prop-group">
                    <div class="vb-prop-group-title">Typography</div>
                    <div class="vb-prop-row">
                        <div class="vb-prop-field">
                            <label>Font Family</label>
                            <select onchange="VB.updateElement('fontFamily', this.value)">
                                <option value="DejaVu Sans" ${el.fontFamily === 'DejaVu Sans' ? 'selected' : ''}>DejaVu Sans</option>
                                <option value="SansSerif" ${el.fontFamily === 'SansSerif' ? 'selected' : ''}>Sans Serif</option>
                                <option value="Serif" ${el.fontFamily === 'Serif' ? 'selected' : ''}>Serif</option>
                                <option value="Monospaced" ${el.fontFamily === 'Monospaced' ? 'selected' : ''}>Monospaced</option>
                            </select>
                        </div>
                        <div class="vb-prop-field">
                            <label>Font Size</label>
                            <input type="number" value="${el.fontSize || 12}" min="6" max="72" onchange="VB.updateElement('fontSize', this.value)">
                        </div>
                    </div>
                    <div class="vb-prop-row">
                        <div class="vb-prop-field">
                            <label>Horizontal Align</label>
                            <select onchange="VB.updateElement('hAlign', this.value)">
                                <option value="left" ${el.hAlign === 'left' ? 'selected' : ''}>Left</option>
                                <option value="center" ${el.hAlign === 'center' ? 'selected' : ''}>Center</option>
                                <option value="right" ${el.hAlign === 'right' ? 'selected' : ''}>Right</option>
                            </select>
                        </div>
                        <div class="vb-prop-field">
                            <label>Vertical Align</label>
                            <select onchange="VB.updateElement('vAlign', this.value)">
                                <option value="top" ${el.vAlign === 'top' ? 'selected' : ''}>Top</option>
                                <option value="middle" ${el.vAlign === 'middle' ? 'selected' : ''}>Middle</option>
                                <option value="bottom" ${el.vAlign === 'bottom' ? 'selected' : ''}>Bottom</option>
                            </select>
                        </div>
                    </div>
                    <label class="vb-prop-check"><input type="checkbox" ${el.bold ? 'checked' : ''} onchange="VB.updateElement('bold', this.checked)">Bold</label>
                    <label class="vb-prop-check"><input type="checkbox" ${el.italic ? 'checked' : ''} onchange="VB.updateElement('italic', this.checked)">Italic</label>
                    <label class="vb-prop-check"><input type="checkbox" ${el.underline ? 'checked' : ''} onchange="VB.updateElement('underline', this.checked)">Underline</label>
                </div>
            `;
        }

        html += `
            <div class="vb-prop-group">
                <div class="vb-prop-group-title">Appearance</div>
                <div class="vb-prop-row">
                    <div class="vb-prop-field">
                        <label>${el.type === 'line' ? 'Line Color' : 'Text Color'}</label>
                        <input type="color" value="${el.color || '#111111'}" onchange="VB.updateElement('color', this.value)">
                    </div>
                    <div class="vb-prop-field">
                        <label>${el.type === 'line' ? 'Thickness' : 'Border Width'}</label>
                        <input type="number" value="${el.borderWidth != null ? el.borderWidth : 1}" min="1" max="12" onchange="VB.updateElement('borderWidth', this.value)">
                    </div>
                </div>
                ${el.type !== 'line' ? `
                    <div class="vb-prop-row">
                        <div class="vb-prop-field">
                            <label>Border Color</label>
                            <input type="color" value="${el.borderColor || '#7f90a3'}" onchange="VB.updateElement('borderColor', this.value)">
                        </div>
                        <div class="vb-prop-field">
                            <label>Fill Color</label>
                            <input type="color" value="${el.backgroundColor || '#ffffff'}" onchange="VB.updateElement('backgroundColor', this.value)">
                        </div>
                    </div>
                    <label class="vb-prop-check"><input type="checkbox" ${el.backgroundTransparent ? 'checked' : ''} onchange="VB.updateElement('backgroundTransparent', this.checked)">Transparent Fill</label>
                ` : ''}
            </div>

            <button type="button" class="vb-prop-danger" onclick="VB.delete()">Delete Element</button>
        `;

        panel.innerHTML = html;

        if (preserveScroll) {
            panel.scrollTop = previousPanelScroll;
            const nextColumnSelector = document.getElementById('vbColumnSelector');
            if (nextColumnSelector) {
                nextColumnSelector.scrollTop = previousColumnScroll;
            }
        }
    },

    updateElement(property, value) {
        const el = this.elements.find((item) => item.id === this.selectedId);
        if (!el) return;

        if (property === 'fontSize' || property === 'width' || property === 'height' || property === 'numColumns' || property === 'rows' || property === 'x' || property === 'y' || property === 'borderWidth') {
            const parsed = parseInt(value, 10);
            if (!Number.isNaN(parsed)) {
                el[property] = parsed;
            }
        } else {
            el[property] = value;
        }

        this.normalizeElementPosition(el);

        this.render();
        this.updateProperties(true);
    }
};

function vbEscapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function vbEscapeJsString(value) {
    return String(value ?? '')
        .replace(/\\/g, '\\\\')
        .replace(/'/g, "\\'");
}

function vbReadJsonResponse(response, defaultMessage) {
    return response.text().then((text) => {
        let data = {};
        if (text) {
            try {
                data = JSON.parse(text);
            } catch (error) {
                data = { message: text };
            }
        }

        if (!response.ok || data.success === false) {
            throw new Error(data.message || defaultMessage);
        }

        return data;
    });
}

function vbNormalizeColumnNames(columns) {
    if (!Array.isArray(columns)) {
        return [];
    }

    return columns
        .map((column) => (typeof column === 'string' ? column : column?.name))
        .filter((columnName) => Boolean(columnName));
}

function vbGetCsrfHeaders() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    const header = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');
    if (token && header) {
        return { [header]: token };
    }
    return {};
}

function vbOpenResultModal(title, message) {
    const modal = document.getElementById('vbResultModal');
    const titleEl = document.getElementById('vbResultModalTitle');
    const messageEl = document.getElementById('vbResultModalMessage');

    if (!modal || !titleEl || !messageEl) {
        if (typeof showMessage === 'function') {
            showMessage(message, 'success');
        }
        return;
    }

    titleEl.textContent = title;
    messageEl.textContent = message;
    modal.style.display = 'block';
}

function vbCloseResultModal() {
    const modal = document.getElementById('vbResultModal');
    if (modal) {
        modal.style.display = 'none';
    }
}

function vbStartDrag(event, elementType) {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData('elementType', elementType);
    event.dataTransfer.setData('text/plain', elementType);
    event.target.style.opacity = '0.5';
    event.target.style.transform = 'scale(0.95)';
}

function vbEndDrag(event) {
    event.target.style.opacity = '1';
    event.target.style.transform = 'scale(1)';
}

function vbAddElement(type) {
    VB.addElement(type);
}

function vbChangeBand() {
    // Backward compatibility no-op. Bands are now inferred from Y position
    // inside Header / Body / Footer zones.
    VB.render();
}

function vbChangeOrientation() {
    const select = document.getElementById('vbOrientationSelect');
    if (!select) return;

    VB.orientation = select.value === 'landscape' ? 'landscape' : 'portrait';
    VB.applyOrientation();
    VB.elements.forEach((el) => VB.normalizeElementPosition(el));
    VB.render();
}

function vbToggleCoverOptions() {
    const enabled = document.getElementById('vbEnableCoverPage')?.checked === true;
    const coverEditor = document.getElementById('vbCoverEditor');

    if (coverEditor) {
        coverEditor.style.display = enabled ? '' : 'none';
    }
}

function vbSetInputValue(id, value) {
    const input = document.getElementById(id);
    if (!input) return;
    input.value = value;
}

function vbSetInputChecked(id, checked) {
    const input = document.getElementById(id);
    if (!input) return;
    input.checked = checked === true;
}

function vbCleanReportNameForCover(value) {
    const raw = String(value || '').trim();
    if (!raw) return 'Report';
    return raw.replace(/\.jrxml$/i, '') || 'Report';
}

function vbApplyCoverPreset() {
    const presetSelect = document.getElementById('vbCoverPreset');
    const preset = presetSelect?.value || 'custom';
    if (preset === 'custom') {
        return;
    }

    const reportName = vbCleanReportNameForCover(document.getElementById('vbReportName')?.value);
    const presets = {
        corporate: {
            title: `${reportName}`,
            subtitle: 'Confidential Business Report',
            author: 'Prepared by Business Intelligence Department',
            alignment: 'Left',
            titleSize: 34,
            subtitleSize: 15,
            showDate: true,
            datePattern: 'MMMM yyyy'
        },
        minimal: {
            title: `${reportName}`,
            subtitle: 'Summary and key figures',
            author: '',
            alignment: 'Center',
            titleSize: 28,
            subtitleSize: 14,
            showDate: true,
            datePattern: 'dd/MM/yyyy'
        },
        executive: {
            title: `${reportName}`,
            subtitle: 'Executive Performance Overview',
            author: 'Executive Office',
            alignment: 'Center',
            titleSize: 40,
            subtitleSize: 18,
            showDate: true,
            datePattern: 'MMMM dd, yyyy'
        }
    };

    const selectedPreset = presets[preset];
    if (!selectedPreset) {
        return;
    }

    const coverEnabledCheckbox = document.getElementById('vbEnableCoverPage');
    if (coverEnabledCheckbox && !coverEnabledCheckbox.checked) {
        coverEnabledCheckbox.checked = true;
        vbToggleCoverOptions();
    }

    vbSetInputValue('vbCoverTitle', selectedPreset.title);
    vbSetInputValue('vbCoverSubtitle', selectedPreset.subtitle);
    vbSetInputValue('vbCoverAuthor', selectedPreset.author);
    vbSetInputValue('vbCoverAlignment', selectedPreset.alignment);
    vbSetInputValue('vbCoverTitleSize', String(selectedPreset.titleSize));
    vbSetInputValue('vbCoverSubtitleSize', String(selectedPreset.subtitleSize));
    vbSetInputChecked('vbCoverShowDate', selectedPreset.showDate);
    vbSetInputValue('vbCoverDatePattern', selectedPreset.datePattern);
}

function vbUploadCoverLogo(event) {
    const file = event?.target?.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (loadEvent) => {
        const dataUrl = String(loadEvent?.target?.result || '');
        if (!dataUrl) {
            if (typeof showMessage === 'function') {
                showMessage('Unable to read selected image.', 'error');
            }
            return;
        }

        const hiddenInput = document.getElementById('vbCoverLogoData');
        const preview = document.getElementById('vbCoverLogoPreview');
        const status = document.getElementById('vbCoverLogoStatus');

        if (hiddenInput) {
            hiddenInput.value = dataUrl;
        }
        if (preview) {
            preview.style.display = '';
            preview.innerHTML = `<img src="${dataUrl}" alt="Cover logo preview">`;
        }
        if (status) {
            status.textContent = `Logo selected: ${file.name}`;
        }
    };
    reader.readAsDataURL(file);
}

function vbRemoveCoverLogo() {
    const hiddenInput = document.getElementById('vbCoverLogoData');
    const preview = document.getElementById('vbCoverLogoPreview');
    const status = document.getElementById('vbCoverLogoStatus');
    const fileInput = document.getElementById('vbCoverLogoInput');

    if (hiddenInput) {
        hiddenInput.value = '';
    }
    if (preview) {
        preview.style.display = 'none';
        preview.innerHTML = '';
    }
    if (status) {
        status.textContent = 'No logo selected';
    }
    if (fileInput) {
        fileInput.value = '';
    }
}

function vbReadIntegerInput(id, fallbackValue, minValue, maxValue) {
    const raw = document.getElementById(id)?.value;
    const parsed = Number.parseInt(raw, 10);
    if (Number.isNaN(parsed)) {
        return fallbackValue;
    }
    return Math.max(minValue, Math.min(maxValue, parsed));
}

function vbClear() {
    const clearCanvas = () => {
        VB.elements = [];
        VB.selectedId = null;
        VB.render();
        VB.updateProperties();
    };

    if (typeof showConfirmationModal === 'function') {
        showConfirmationModal('Clear all elements from the canvas?', clearCanvas);
        return;
    }

    clearCanvas();
}

function vbGenerate() {
    const reportNameInput = document.getElementById('vbReportName');
    const reportName = (reportNameInput?.value || 'Report').trim();
    const coverPageEnabled = document.getElementById('vbEnableCoverPage')?.checked === true;

    if (VB.elements.length === 0 && !coverPageEnabled) {
        if (typeof showMessage === 'function') {
            showMessage('Add elements to the canvas or enable a cover page first.', 'error');
        }
        return;
    }

    const isLandscape = VB.orientation === 'landscape';
    const pageSettings = {
        width: isLandscape ? 842 : 595,
        height: isLandscape ? 595 : 842,
        leftMargin: 20,
        rightMargin: 20,
        topMargin: 20,
        bottomMargin: 20,
        orientation: isLandscape ? 'Landscape' : 'Portrait'
    };

    const reportOptions = {
        headerFirstPageOnly: document.getElementById('vbHeaderFirstPageOnly')?.checked === true,
        coverPageEnabled,
        coverTitle: (document.getElementById('vbCoverTitle')?.value || '').trim(),
        coverSubtitle: (document.getElementById('vbCoverSubtitle')?.value || '').trim(),
        coverAuthor: (document.getElementById('vbCoverAuthor')?.value || '').trim(),
        coverDateEnabled: document.getElementById('vbCoverShowDate')?.checked === true,
        coverDatePattern: (document.getElementById('vbCoverDatePattern')?.value || 'dd/MM/yyyy').trim(),
        coverAlignment: (document.getElementById('vbCoverAlignment')?.value || 'Center').trim(),
        coverTitleSize: vbReadIntegerInput('vbCoverTitleSize', 30, 12, 72),
        coverSubtitleSize: vbReadIntegerInput('vbCoverSubtitleSize', 16, 10, 48),
        coverLogoData: (document.getElementById('vbCoverLogoData')?.value || '').trim()
    };

    const designData = {
        reportName,
        elements: VB.elements,
        pageSettings,
        reportOptions
    };

    showLoading('Saving JRXML template...');

    fetch('/api/builder/visual/generate', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            ...vbGetCsrfHeaders()
        },
        body: JSON.stringify(designData)
    })
        .then((response) => vbReadJsonResponse(response, 'Unable to generate JRXML template'))
        .then((data) => {
            hideLoading();

            if (typeof loadJrxmlTemplates === 'function') {
                loadJrxmlTemplates();
            }

            const generatedName = data.reportName || (reportName.endsWith('.jrxml') ? reportName : `${reportName}.jrxml`);
            vbOpenResultModal('Template Saved', `${generatedName} is now available in "Available JRXML Templates".`);
        })
        .catch((error) => {
            hideLoading();
            if (typeof showMessage === 'function') {
                showMessage(error.message || 'Error generating report template', 'error');
            }
        });
}

document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        VB.init();
        vbToggleCoverOptions();
    }, 200);
});

window.addEventListener('datasource:changed', (event) => {
    const changedDatasourceId = event?.detail?.datasourceId ?? null;
    VB.loadDatasources(changedDatasourceId);
});

function vbTest() {
    VB.addElement('text');
}

function vbAddLogo() {
    const input = document.getElementById('vbLogoInput');
    input.click();
}

function vbUploadLogo(event) {
    const file = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (e) => {
        const id = VB.nextId++;
        const y = VB.getDefaultYForBand('detail');
        const logoData = {
            id,
            type: 'logo',
            band: 'detail',
            x: 50,
            y,
            width: 150,
            height: 60,
            text: '',
            fontSize: 12,
            fontFamily: 'DejaVu Sans',
            color: '#111111',
            borderColor: '#7f90a3',
            borderWidth: 1,
            backgroundColor: '#ffffff',
            backgroundTransparent: true,
            hAlign: 'left',
            vAlign: 'middle',
            bold: false,
            italic: false,
            underline: false,
            imageData: e.target.result
        };

        VB.normalizeElementPosition(logoData);

        VB.elements.push(logoData);
        VB.render();
        VB.select(id);
    };
    reader.readAsDataURL(file);
}

function vbAddTable(numColumns = 3) {
    if (!VB.datasourceId) {
        if (typeof showMessage === 'function') {
            showMessage('Please select a datasource first', 'error');
        }
        return;
    }

    const parsedColumns = parseInt(numColumns, 10);
    if (Number.isNaN(parsedColumns) || parsedColumns < 2 || parsedColumns > 10) {
        if (typeof showMessage === 'function') {
            showMessage('Please enter a valid number between 2 and 10', 'error');
        }
        return;
    }

    numColumns = parsedColumns;
    const id = VB.nextId++;
    const y = VB.getDefaultYForBand('detail');

    const tableData = {
        id,
        type: 'table',
        band: 'detail',
        x: 50,
        y,
        width: Math.min(500, 50 * numColumns),
        height: 150,
        text: '',
        fontSize: 12,
        fontFamily: 'DejaVu Sans',
        color: '#111111',
        borderColor: '#7f90a3',
        borderWidth: 1,
        backgroundColor: '#ffffff',
        backgroundTransparent: true,
        hAlign: 'left',
        vAlign: 'middle',
        bold: false,
        italic: false,
        underline: false,
        numColumns,
        rows: 5,
        tableName: null,
        columns: []
    };

    VB.normalizeElementPosition(tableData);

    VB.elements.push(tableData);
    VB.render();
    VB.select(id);
}

// Load tables from selected datasource
function vbLoadTables() {
    const datasourceId = document.getElementById('vbDatasourceSelect').value;
    const tablesList = document.getElementById('vbTablesList');
    
    if (!datasourceId) {
        tablesList.innerHTML = '<div class="vb-table-list-empty">Select a datasource first</div>';
        VB.datasourceId = null;
        VB.availableTables = [];
        return;
    }
    
    VB.datasourceId = parseInt(datasourceId);
    tablesList.innerHTML = '<div class="vb-table-list-empty">Loading tables...</div>';
    
    fetch(`/api/builder/datasources/${datasourceId}/tables`)
        .then(response => vbReadJsonResponse(response, 'Unable to load tables from the selected datasource'))
        .then(data => {
            if (data.success && data.tables && data.tables.length > 0) {
                VB.availableTables = data.tables;

                tablesList.innerHTML = '';
                data.tables.forEach(table => {
                    const button = document.createElement('button');
                    button.type = 'button';
                    button.className = 'vb-toolbox-btn';
                    button.draggable = true;
                    button.title = 'Drag to canvas or click to add';
                    button.textContent = table;
                    button.addEventListener('dragstart', (event) => vbStartTableDrag(event, table));
                    button.addEventListener('dragend', vbEndDrag);
                    button.addEventListener('click', () => vbAddTableFromDB(table));
                    tablesList.appendChild(button);
                });
            } else {
                tablesList.innerHTML = '<div class="vb-table-list-empty">No tables found</div>';
                VB.availableTables = [];
            }
        })
        .catch(error => {
            console.error('Error loading tables:', error);
            tablesList.innerHTML = `<div class="vb-table-list-empty" style="color:#c0392b;">${vbEscapeHtml(error.message || 'Error loading tables')}</div>`;
            VB.availableTables = [];
        });
}

// Start dragging a table
function vbStartTableDrag(event, tableName) {
    event.dataTransfer.effectAllowed = 'copy';
    event.dataTransfer.setData('elementType', 'dbTable');
    event.dataTransfer.setData('tableName', tableName);
    event.target.style.opacity = '0.5';
}

// Add table from database
function vbAddTableFromDB(tableName, dropPosition = null) {
    if (!VB.datasourceId) {
        if (typeof showMessage === 'function') {
            showMessage('Please select a datasource first', 'error');
        }
        return;
    }
    
    showLoading('Loading table columns...');
    
    // Load columns for this table
    fetch(`/api/builder/datasources/${VB.datasourceId}/tables/${encodeURIComponent(tableName)}/columns`)
        .then(response => vbReadJsonResponse(response, 'Unable to load columns for the selected table'))
        .then(data => {
            hideLoading();
            
            if (data.success && data.columns && data.columns.length > 0) {
                const columnNames = vbNormalizeColumnNames(data.columns);
                VB.tableColumns[tableName] = columnNames;
                VB.tableColumnDetails[tableName] = data.columns;
                
                const id = VB.nextId++;
                const columnWidth = 120;
                const totalWidth = Math.min(600, columnWidth * columnNames.length);
                const hasDropPosition = dropPosition
                    && Number.isFinite(dropPosition.x)
                    && Number.isFinite(dropPosition.y);

                const x = hasDropPosition ? dropPosition.x : 50;
                const y = hasDropPosition ? dropPosition.y : VB.getDefaultYForBand('detail');
                
                const tableData = {
                    id,
                    type: 'dbTable',
                    band: 'detail',
                    x,
                    y,
                    width: totalWidth,
                    height: 200,
                    text: tableName,
                    fontSize: 10,
                    fontFamily: 'DejaVu Sans',
                    color: '#111111',
                    borderColor: '#667eea',
                    borderWidth: 1,
                    backgroundColor: '#ffffff',
                    backgroundTransparent: true,
                    hAlign: 'left',
                    vAlign: 'middle',
                    bold: false,
                    italic: false,
                    underline: false,
                    tableName: tableName,
                    columns: columnNames.slice(0, 10),
                    columnDetails: data.columns,
                    selectedColumns: columnNames.slice(0, 10),
                    showHeaders: true,
                    headerBold: true,
                    alternateRows: true
                };

                VB.normalizeElementPosition(tableData);
                
                VB.elements.push(tableData);
                VB.render();
                VB.select(id);
            } else {
                if (typeof showMessage === 'function') {
                    showMessage('No columns found for this table', 'error');
                }
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading columns:', error);
            if (typeof showMessage === 'function') {
                showMessage('Error loading table columns: ' + (error.message || 'Unknown error'), 'error');
            }
        });
}

// Update the addElementAtPosition to handle dbTable drops
VB.addElementAtPositionOriginal = VB.addElementAtPosition;
VB.addElementAtPosition = function(type, x, y, data = {}) {
    if (type === 'dbTable' && data.tableName) {
        vbAddTableFromDB(data.tableName, { x, y });
    } else {
        VB.addElementAtPositionOriginal(type, x, y);
    }
};

// Toggle column selection for database tables
function vbToggleColumn(columnName, checked) {
    if (!VB.selectedId) return;
    
    const el = VB.elements.find(item => item.id === VB.selectedId);
    if (!el || el.type !== 'dbTable') return;
    
    if (!el.selectedColumns) {
        el.selectedColumns = [];
    }
    
    if (checked) {
        // Add column if not already selected
        if (!el.selectedColumns.includes(columnName)) {
            el.selectedColumns.push(columnName);
        }
    } else {
        // Remove column
        el.selectedColumns = el.selectedColumns.filter(col => col !== columnName);
    }
    
    // Adjust table width based on number of selected columns
    const columnWidth = 100;
    el.width = Math.max(200, columnWidth * el.selectedColumns.length);
    
    VB.render();
    VB.updateProperties(true);
}
