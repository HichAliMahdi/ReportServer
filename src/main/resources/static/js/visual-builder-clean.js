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
    vbRenderCoverLivePreview();
}

function vbToggleCoverOptions() {
    const enabled = document.getElementById('vbEnableCoverPage')?.checked === true;
    const coverEditor = document.getElementById('vbCoverEditor');

    if (coverEditor) {
        coverEditor.style.display = enabled ? '' : 'none';
    }
}

function vbIsCoverEnabled() {
    return document.getElementById('vbEnableCoverPage')?.checked === true;
}

function vbCoverAppliesToTarget(target) {
    const select = vbGetCoverTemplateSelect(target);
    return !!(select && String(select.value || '').trim());
}

function vbGetCoverTemplateSelectId(target) {
    return target === 'form' ? 'fbCoverTemplateSelect' : 'vbVisualCoverTemplateSelect';
}

function vbGetCoverTemplateEditButtonId(target) {
    return target === 'form' ? 'fbEditCoverTemplateBtn' : 'vbVisualEditCoverTemplateBtn';
}

function vbGetCoverTemplateSelect(target) {
    return document.getElementById(vbGetCoverTemplateSelectId(target));
}

function vbGetTargetReportName(target = 'visual') {
    if (target === 'form') {
        return (document.getElementById('builderReportName')?.value || '').trim();
    }
    return (document.getElementById('vbReportName')?.value || '').trim();
}

function vbResolveCoverReportNameFallback(target = 'visual') {
    const targetName = vbGetTargetReportName(target);
    if (targetName) {
        return targetName;
    }

    const visualName = (document.getElementById('vbReportName')?.value || '').trim();
    if (visualName) {
        return visualName;
    }

    return (document.getElementById('builderReportName')?.value || '').trim();
}

function vbResolveCoverTemplateConfig(templateValue, target = 'visual') {
    const value = String(templateValue || '').trim();
    if (!value) {
        return null;
    }

    const reportName = vbResolveCoverReportNameFallback(target);
    const builtInPresets = vbGetBuiltInCoverPresets(reportName);
    const customPresets = vbLoadCoverPresetsFromStorage();

    if (value.startsWith('builtin:')) {
        const presetKey = value.substring('builtin:'.length);
        return builtInPresets[presetKey] ? { ...builtInPresets[presetKey] } : null;
    }

    if (value.startsWith('saved:')) {
        const presetName = value.substring('saved:'.length);
        return customPresets[presetName] ? { ...customPresets[presetName] } : null;
    }

    if (builtInPresets[value]) {
        return { ...builtInPresets[value] };
    }

    return null;
}

function vbHandleCoverTemplateSelectionChange(target) {
    const button = document.getElementById(vbGetCoverTemplateEditButtonId(target));
    const select = vbGetCoverTemplateSelect(target);

    if (button) {
        button.style.display = (select && String(select.value || '').trim()) ? '' : 'none';
    }
}

function vbRefreshBuilderCoverTemplateSelectors(preferredVisual = null, preferredForm = null) {
    const customPresets = vbLoadCoverPresetsFromStorage();
    const customPresetNames = Object.keys(customPresets).sort((a, b) => a.localeCompare(b));

    const populate = (target, preferredValue) => {
        const select = vbGetCoverTemplateSelect(target);
        if (!select) {
            return;
        }

        const desiredValue = preferredValue !== null ? preferredValue : (select.value || '');
        select.innerHTML = `
            <option value="">-- No Cover Page --</option>
            <option value="builtin:corporate">Corporate</option>
            <option value="builtin:minimal">Minimal</option>
            <option value="builtin:executive">Executive</option>
        `;

        customPresetNames.forEach((name) => {
            const option = document.createElement('option');
            option.value = `saved:${name}`;
            option.textContent = `My Template: ${name}`;
            select.appendChild(option);
        });

        const hasDesiredValue = Array.from(select.options).some((option) => option.value === desiredValue);
        select.value = hasDesiredValue ? desiredValue : '';

        vbHandleCoverTemplateSelectionChange(target);
    };

    populate('visual', preferredVisual);
    populate('form', preferredForm);
}

function vbEditSelectedCoverTemplate(target) {
    const select = vbGetCoverTemplateSelect(target);
    const templateValue = String(select?.value || '').trim();
    if (!templateValue) {
        if (typeof showMessage === 'function') {
            showMessage('Select a cover template first.', 'error');
        }
        return;
    }

    const config = vbResolveCoverTemplateConfig(templateValue, target);
    if (!config) {
        if (typeof showMessage === 'function') {
            showMessage('Unable to load selected cover template.', 'error');
        }
        return;
    }

    if (typeof switchBuilderMode === 'function') {
        switchBuilderMode('cover');
    }

    const enableCoverCheckbox = document.getElementById('vbEnableCoverPage');
    if (enableCoverCheckbox && !enableCoverCheckbox.checked) {
        enableCoverCheckbox.checked = true;
    }
    vbToggleCoverOptions();

    const presetValue = templateValue.startsWith('builtin:')
        ? templateValue.substring('builtin:'.length)
        : templateValue;
    vbRefreshCoverPresetOptions(presetValue);
    vbApplyCoverPresetConfig(config);

    if (templateValue.startsWith('saved:')) {
        vbSetInputValue('vbCoverPresetName', templateValue.substring('saved:'.length));
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

const VB_COVER_PRESET_STORAGE_KEY = 'reportserver.vb.coverPresets.v1';
const VB_SUPPORTED_COVER_FILE_MIME_TYPES = new Set([
    'application/pdf',
    'image/png',
    'image/jpeg',
    'image/jpg',
    'image/webp',
    'image/gif',
    'image/bmp'
]);
const VB_SUPPORTED_COVER_FILE_EXTENSIONS = ['.pdf', '.png', '.jpg', '.jpeg', '.webp', '.gif', '.bmp'];

function vbResolveCoverThemePalette(theme) {
    const normalized = String(theme || '').trim().toLowerCase();
    switch (normalized) {
        case 'forest':
            return {
                titleColor: '#1F4A38',
                subtitleColor: '#2E6A52',
                metaColor: '#4C7564',
                accentColor: '#2F8F6D',
                backgroundColor: '#EAF7F1'
            };
        case 'sunrise':
            return {
                titleColor: '#6A3A1B',
                subtitleColor: '#9A5728',
                metaColor: '#A16C47',
                accentColor: '#E07B39',
                backgroundColor: '#FFF2E8'
            };
        case 'charcoal':
            return {
                titleColor: '#2A2F36',
                subtitleColor: '#3E4650',
                metaColor: '#59626D',
                accentColor: '#8C99A8',
                backgroundColor: '#F2F5F8'
            };
        case 'midnightgold':
            return {
                titleColor: '#2A2A3D',
                subtitleColor: '#4A4762',
                metaColor: '#666375',
                accentColor: '#B08A2E',
                backgroundColor: '#F6F1E3'
            };
        case 'classicblue':
        default:
            return {
                titleColor: '#143A62',
                subtitleColor: '#2F5C8A',
                metaColor: '#486A8E',
                accentColor: '#2E75B6',
                backgroundColor: '#EAF2FB'
            };
    }
}

function vbFormatPreviewDate(pattern) {
    const now = new Date();
    const yyyy = String(now.getFullYear());
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    const monthNames = [
        'January', 'February', 'March', 'April', 'May', 'June',
        'July', 'August', 'September', 'October', 'November', 'December'
    ];
    const monthFull = monthNames[now.getMonth()];
    const monthShort = monthFull.substring(0, 3);

    const source = String(pattern || 'dd/MM/yyyy').trim() || 'dd/MM/yyyy';
    return source
        .replace(/MMMM/g, monthFull)
        .replace(/MMM/g, monthShort)
        .replace(/yyyy/g, yyyy)
        .replace(/MM/g, mm)
        .replace(/dd/g, dd);
}

function vbBindCoverPreviewEvents() {
    const watchedIds = [
        'vbReportName',
        'builderReportName',
        'vbOrientationSelect',
        'vbEnableCoverPage',
        'vbVisualCoverTemplateSelect',
        'fbCoverTemplateSelect',
        'vbCoverPreviewA4Scale',
        'vbCoverTitle',
        'vbCoverSubtitle',
        'vbCoverAuthor',
        'vbCoverAlignment',
        'vbCoverTheme',
        'vbCoverTitleSize',
        'vbCoverSubtitleSize',
        'vbCoverDatePattern',
        'vbCoverShowDate',
        'vbCoverIncludeReportName',
        'vbCoverAccentEnabled',
        'vbCoverBackgroundShapeEnabled'
    ];

    watchedIds.forEach((id) => {
        const element = document.getElementById(id);
        if (!element || element.dataset.previewBound === '1') {
            return;
        }

        const eventName = (element.tagName === 'SELECT' || element.type === 'checkbox') ? 'change' : 'input';
        element.addEventListener(eventName, () => vbRenderCoverLivePreview());
        if (eventName !== 'change') {
            element.addEventListener('change', () => vbRenderCoverLivePreview());
        }
        element.dataset.previewBound = '1';
    });
}

function vbRenderCoverLivePreview() {
    const container = document.getElementById('vbCoverLivePreview');
    if (!container) return;

    const enabled = vbIsCoverEnabled();
    if (!enabled) {
        container.className = 'vb-cover-live-preview-empty';
        container.style.cssText = '';
        container.textContent = 'Enable cover page to preview.';
        return;
    }

    const includeReportName = document.getElementById('vbCoverIncludeReportName')?.checked !== false;
    const typedCoverTitle = (document.getElementById('vbCoverTitle')?.value || '').trim();
    const title = typedCoverTitle || (includeReportName ? vbCleanReportNameForCover(vbResolveCoverReportNameFallback()) : '');
    const subtitle = (document.getElementById('vbCoverSubtitle')?.value || '').trim();
    const author = (document.getElementById('vbCoverAuthor')?.value || '').trim();
    const alignment = (document.getElementById('vbCoverAlignment')?.value || 'Center').trim();
    const titleSize = vbReadIntegerInput('vbCoverTitleSize', 30, 12, 72);
    const subtitleSize = vbReadIntegerInput('vbCoverSubtitleSize', 16, 10, 48);
    const showDate = document.getElementById('vbCoverShowDate')?.checked === true;
    const datePattern = (document.getElementById('vbCoverDatePattern')?.value || 'dd/MM/yyyy').trim();
    const showAccent = document.getElementById('vbCoverAccentEnabled')?.checked === true;
    const showShape = document.getElementById('vbCoverBackgroundShapeEnabled')?.checked === true;
    const logoData = (document.getElementById('vbCoverLogoData')?.value || '').trim();
    const coverPageFileData = (document.getElementById('vbCoverPageFileData')?.value || '').trim();
    const fullA4Scale = document.getElementById('vbCoverPreviewA4Scale')?.checked === true;
    const isLandscape = VB.orientation === 'landscape';
    const pageWidth = isLandscape ? 842 : 595;
    const pageHeight = isLandscape ? 595 : 842;

    const palette = vbResolveCoverThemePalette(document.getElementById('vbCoverTheme')?.value || 'classicBlue');

    let alignCss = 'center';
    if (alignment === 'Left') {
        alignCss = 'left';
    } else if (alignment === 'Right') {
        alignCss = 'right';
    }

    const escape = (value) => vbEscapeHtml(String(value || ''));
    const dateValue = showDate ? vbFormatPreviewDate(datePattern) : '';
    const previewClassName = fullA4Scale
        ? 'vb-cover-live-preview vb-cover-live-preview-a4'
        : 'vb-cover-live-preview';
    const previewStyle = fullA4Scale
        ? `width:${pageWidth}px;min-height:${pageHeight}px;height:${pageHeight}px;padding:${Math.max(18, Math.round(pageHeight * 0.045))}px;`
        : '';
    const contentMinHeight = fullA4Scale ? Math.max(280, pageHeight - 96) : 224;

    container.className = previewClassName;
    container.style.cssText = previewStyle;
    container.innerHTML = `
        ${coverPageFileData ? `<img class="vb-cover-live-preview-shape" style="object-fit:cover;width:100%;height:100%;" src="${coverPageFileData}" alt="Cover page background">` : ''}
        ${showShape ? `<div class="vb-cover-live-preview-shape" style="background:${palette.backgroundColor};"></div>` : ''}
        <div class="vb-cover-live-preview-content" style="text-align:${alignCss};align-items:${alignCss === 'left' ? 'flex-start' : (alignCss === 'right' ? 'flex-end' : 'center')};min-height:${contentMinHeight}px;">
            ${logoData ? `<img class="vb-cover-live-preview-logo" src="${logoData}" alt="Cover logo">` : ''}
            ${title ? `<p class="vb-cover-live-preview-title" style="font-size:${titleSize}px;color:${palette.titleColor};">${escape(title)}</p>` : ''}
            ${subtitle ? `<p class="vb-cover-live-preview-subtitle" style="font-size:${subtitleSize}px;color:${palette.subtitleColor};">${escape(subtitle)}</p>` : ''}
            ${author ? `<p class="vb-cover-live-preview-meta" style="font-size:12px;color:${palette.metaColor};">${escape(author)}</p>` : ''}
            ${dateValue ? `<p class="vb-cover-live-preview-date" style="font-size:12px;color:${palette.metaColor};">${escape(dateValue)}</p>` : ''}
            ${showAccent ? `<div class="vb-cover-live-preview-accent" style="background:${palette.accentColor};"></div>` : ''}
        </div>
    `;
}

function vbGetBuiltInCoverPresets(reportName) {
    const safeReportName = vbCleanReportNameForCover(reportName);
    return {
        corporate: {
            title: `${safeReportName}`,
            subtitle: 'Confidential Business Report',
            author: 'Prepared by Business Intelligence Department',
            alignment: 'Left',
            theme: 'classicBlue',
            titleSize: 34,
            subtitleSize: 15,
            showDate: true,
            includeReportName: true,
            datePattern: 'MMMM yyyy',
            accentEnabled: true,
            backgroundShapeEnabled: true
        },
        minimal: {
            title: `${safeReportName}`,
            subtitle: 'Summary and key figures',
            author: '',
            alignment: 'Center',
            theme: 'charcoal',
            titleSize: 28,
            subtitleSize: 14,
            showDate: true,
            includeReportName: true,
            datePattern: 'dd/MM/yyyy',
            accentEnabled: false,
            backgroundShapeEnabled: false
        },
        executive: {
            title: `${safeReportName}`,
            subtitle: 'Executive Performance Overview',
            author: 'Executive Office',
            alignment: 'Center',
            theme: 'midnightGold',
            titleSize: 40,
            subtitleSize: 18,
            showDate: true,
            includeReportName: true,
            datePattern: 'MMMM dd, yyyy',
            accentEnabled: true,
            backgroundShapeEnabled: true
        }
    };
}

function vbLoadCoverPresetsFromStorage() {
    try {
        const raw = window.localStorage.getItem(VB_COVER_PRESET_STORAGE_KEY);
        if (!raw) {
            return {};
        }
        const parsed = JSON.parse(raw);
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
            return {};
        }
        return parsed;
    } catch (error) {
        return {};
    }
}

function vbPersistCoverPresetsToStorage(presets) {
    try {
        window.localStorage.setItem(VB_COVER_PRESET_STORAGE_KEY, JSON.stringify(presets));
        return true;
    } catch (error) {
        if (typeof showMessage === 'function') {
            showMessage('Unable to save template in browser storage.', 'error');
        }
        return false;
    }
}

function vbRefreshCoverPresetOptions(preferredValue = null) {
    const select = document.getElementById('vbCoverPreset');
    if (!select) return;

    const desiredValue = preferredValue || select.value || 'custom';
    const customPresets = vbLoadCoverPresetsFromStorage();
    const customNames = Object.keys(customPresets).sort((a, b) => a.localeCompare(b));

    select.innerHTML = `
        <option value="custom">Custom</option>
        <option value="corporate">Corporate</option>
        <option value="minimal">Minimal</option>
        <option value="executive">Executive</option>
    `;

    customNames.forEach((name) => {
        const option = document.createElement('option');
        option.value = `saved:${name}`;
        option.textContent = `My Template: ${name}`;
        select.appendChild(option);
    });

    const hasDesiredValue = Array.from(select.options).some((option) => option.value === desiredValue);
    select.value = hasDesiredValue ? desiredValue : 'custom';
}

function vbBuildCoverPresetConfigFromInputs(includeLogo = true) {
    return {
        title: (document.getElementById('vbCoverTitle')?.value || '').trim(),
        subtitle: (document.getElementById('vbCoverSubtitle')?.value || '').trim(),
        author: (document.getElementById('vbCoverAuthor')?.value || '').trim(),
        alignment: (document.getElementById('vbCoverAlignment')?.value || 'Center').trim(),
        theme: (document.getElementById('vbCoverTheme')?.value || 'classicBlue').trim(),
        titleSize: vbReadIntegerInput('vbCoverTitleSize', 30, 12, 72),
        subtitleSize: vbReadIntegerInput('vbCoverSubtitleSize', 16, 10, 48),
        showDate: document.getElementById('vbCoverShowDate')?.checked === true,
        includeReportName: document.getElementById('vbCoverIncludeReportName')?.checked !== false,
        datePattern: (document.getElementById('vbCoverDatePattern')?.value || 'dd/MM/yyyy').trim(),
        accentEnabled: document.getElementById('vbCoverAccentEnabled')?.checked === true,
        backgroundShapeEnabled: document.getElementById('vbCoverBackgroundShapeEnabled')?.checked === true,
        logoData: includeLogo ? (document.getElementById('vbCoverLogoData')?.value || '').trim() : '',
        coverPageFileData: includeLogo ? (document.getElementById('vbCoverPageFileData')?.value || '').trim() : ''
    };
}

function vbApplyCoverPresetConfig(config) {
    if (!config || typeof config !== 'object') {
        return;
    }

    vbSetInputValue('vbCoverTitle', config.title || '');
    vbSetInputValue('vbCoverSubtitle', config.subtitle || '');
    vbSetInputValue('vbCoverAuthor', config.author || '');
    vbSetInputValue('vbCoverAlignment', config.alignment || 'Center');
    vbSetInputValue('vbCoverTheme', config.theme || 'classicBlue');
    vbSetInputValue('vbCoverTitleSize', String(config.titleSize ?? 30));
    vbSetInputValue('vbCoverSubtitleSize', String(config.subtitleSize ?? 16));
    vbSetInputChecked('vbCoverShowDate', config.showDate !== false);
    vbSetInputChecked('vbCoverIncludeReportName', config.includeReportName !== false);
    vbSetInputValue('vbCoverDatePattern', config.datePattern || 'dd/MM/yyyy');
    vbSetInputChecked('vbCoverAccentEnabled', config.accentEnabled !== false);
    vbSetInputChecked('vbCoverBackgroundShapeEnabled', config.backgroundShapeEnabled !== false);

    if (config.logoData) {
        vbSetInputValue('vbCoverLogoData', config.logoData);
        const preview = document.getElementById('vbCoverLogoPreview');
        const status = document.getElementById('vbCoverLogoStatus');
        if (preview) {
            preview.style.display = '';
            preview.innerHTML = `<img src="${config.logoData}" alt="Cover logo preview">`;
        }
        if (status) {
            status.textContent = 'Logo loaded from template';
        }
    } else {
        vbRemoveCoverLogo();
    }

    if (config.coverPageFileData) {
        vbSetInputValue('vbCoverPageFileData', config.coverPageFileData);
        const coverFileStatus = document.getElementById('vbCoverPageFileStatus');
        if (coverFileStatus) {
            coverFileStatus.textContent = 'Cover page file loaded from template';
        }
    } else {
        vbRemoveCoverPageFile();
    }

    vbRenderCoverLivePreview();
}

function vbSaveCoverPreset() {
    const nameInput = document.getElementById('vbCoverPresetName');
    const rawName = String(nameInput?.value || '').trim();
    const normalizedName = rawName.replace(/\s+/g, ' ').trim();

    if (!normalizedName) {
        if (typeof showMessage === 'function') {
            showMessage('Enter a template name first.', 'error');
        }
        return;
    }

    const includeLogo = document.getElementById('vbCoverPresetIncludeLogo')?.checked === true;
    const customPresets = vbLoadCoverPresetsFromStorage();
    customPresets[normalizedName] = vbBuildCoverPresetConfigFromInputs(includeLogo);

    if (!vbPersistCoverPresetsToStorage(customPresets)) {
        return;
    }

    vbRefreshCoverPresetOptions(`saved:${normalizedName}`);
    vbRefreshBuilderCoverTemplateSelectors();
    if (typeof showMessage === 'function') {
        showMessage(`Template "${normalizedName}" saved.`, 'success');
    }
}

function vbDeleteCoverPreset() {
    const presetSelect = document.getElementById('vbCoverPreset');
    const selectedValue = presetSelect?.value || '';
    if (!selectedValue.startsWith('saved:')) {
        if (typeof showMessage === 'function') {
            showMessage('Select one of your saved templates to delete.', 'error');
        }
        return;
    }

    const presetName = selectedValue.substring('saved:'.length);
    const customPresets = vbLoadCoverPresetsFromStorage();
    if (!Object.prototype.hasOwnProperty.call(customPresets, presetName)) {
        vbRefreshCoverPresetOptions('custom');
        vbRefreshBuilderCoverTemplateSelectors();
        return;
    }

    delete customPresets[presetName];
    if (!vbPersistCoverPresetsToStorage(customPresets)) {
        return;
    }

    vbRefreshCoverPresetOptions('custom');
    vbRefreshBuilderCoverTemplateSelectors();
    if (typeof showMessage === 'function') {
        showMessage(`Template "${presetName}" deleted.`, 'success');
    }
}

function vbDownloadCoverTemplate() {
    const presetSelect = document.getElementById('vbCoverPreset');
    const selectedValue = String(presetSelect?.value || 'custom').trim();

    let templateName = 'cover-template';
    let templateConfig = null;

    if (selectedValue === 'custom') {
        const customName = String(document.getElementById('vbCoverPresetName')?.value || '').trim();
        templateName = customName || 'cover-template';
        templateConfig = vbBuildCoverPresetConfigFromInputs(true);
    } else if (selectedValue.startsWith('saved:')) {
        templateName = selectedValue.substring('saved:'.length) || 'cover-template';
        templateConfig = vbResolveCoverTemplateConfig(selectedValue, 'visual');
    } else {
        templateName = selectedValue;
        templateConfig = vbResolveCoverTemplateConfig(`builtin:${selectedValue}`, 'visual');
    }

    if (!templateConfig) {
        if (typeof showMessage === 'function') {
            showMessage('Unable to export cover template.', 'error');
        }
        return;
    }

    const payload = {
        name: templateName,
        exportedAt: new Date().toISOString(),
        version: 1,
        config: templateConfig
    };

    const fileSafeName = templateName.replace(/[^a-zA-Z0-9_-]+/g, '_').replace(/^_+|_+$/g, '') || 'cover-template';
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `${fileSafeName}.json`;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    URL.revokeObjectURL(url);
}

function vbImportCoverTemplate(event) {
    const file = event?.target?.files?.[0];
    if (!file) {
        return;
    }

    const finalizeInput = () => {
        const input = document.getElementById('vbCoverTemplateImportInput');
        if (input) {
            input.value = '';
        }
    };

    const reader = new FileReader();
    reader.onload = (loadEvent) => {
        try {
            const raw = String(loadEvent?.target?.result || '').trim();
            if (!raw) {
                throw new Error('Template file is empty.');
            }

            const parsed = JSON.parse(raw);
            const importedConfig = (parsed && typeof parsed === 'object' && parsed.config && typeof parsed.config === 'object')
                ? parsed.config
                : parsed;

            if (!importedConfig || typeof importedConfig !== 'object' || Array.isArray(importedConfig)) {
                throw new Error('Invalid template format.');
            }

            const baseName = String((parsed && parsed.name) || file.name.replace(/\.json$/i, '') || '').trim();
            const normalizedName = baseName.replace(/\s+/g, ' ').trim();
            if (!normalizedName) {
                throw new Error('Template name is missing.');
            }

            const customPresets = vbLoadCoverPresetsFromStorage();
            customPresets[normalizedName] = importedConfig;
            if (!vbPersistCoverPresetsToStorage(customPresets)) {
                finalizeInput();
                return;
            }

            const presetValue = `saved:${normalizedName}`;
            vbRefreshCoverPresetOptions(presetValue);
            vbRefreshBuilderCoverTemplateSelectors();

            const coverEnabledCheckbox = document.getElementById('vbEnableCoverPage');
            if (coverEnabledCheckbox && !coverEnabledCheckbox.checked) {
                coverEnabledCheckbox.checked = true;
                vbToggleCoverOptions();
            }

            vbApplyCoverPresetConfig(importedConfig);

            if (typeof showMessage === 'function') {
                showMessage(`Template "${normalizedName}" imported.`, 'success');
            }
        } catch (error) {
            if (typeof showMessage === 'function') {
                showMessage(error.message || 'Unable to import template.', 'error');
            }
        } finally {
            finalizeInput();
        }
    };

    reader.onerror = () => {
        finalizeInput();
        if (typeof showMessage === 'function') {
            showMessage('Unable to read template file.', 'error');
        }
    };

    reader.readAsText(file);
}

function vbApplyCoverPreset() {
    const presetSelect = document.getElementById('vbCoverPreset');
    const preset = presetSelect?.value || 'custom';
    if (preset === 'custom') {
        vbRenderCoverLivePreview();
        return;
    }

    const reportName = vbResolveCoverReportNameFallback();
    const builtInPresets = vbGetBuiltInCoverPresets(reportName);
    let selectedPreset = builtInPresets[preset];

    if (!selectedPreset && preset.startsWith('saved:')) {
        const presetName = preset.substring('saved:'.length);
        const customPresets = vbLoadCoverPresetsFromStorage();
        selectedPreset = customPresets[presetName] || null;
    }

    if (!selectedPreset) {
        return;
    }

    const coverEnabledCheckbox = document.getElementById('vbEnableCoverPage');
    if (coverEnabledCheckbox && !coverEnabledCheckbox.checked) {
        coverEnabledCheckbox.checked = true;
        vbToggleCoverOptions();
    }

    vbApplyCoverPresetConfig(selectedPreset);
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

        vbRenderCoverLivePreview();
    };
    reader.readAsDataURL(file);
}

function vbIsSupportedCoverFile(file) {
    if (!file) return false;
    const mime = String(file.type || '').toLowerCase();
    const fileName = String(file.name || '').toLowerCase();
    const hasSupportedExtension = VB_SUPPORTED_COVER_FILE_EXTENSIONS.some((ext) => fileName.endsWith(ext));

    if (!mime) {
        return hasSupportedExtension;
    }
    return VB_SUPPORTED_COVER_FILE_MIME_TYPES.has(mime) || hasSupportedExtension;
}

function vbUploadCoverPageFile(event) {
    const file = event?.target?.files?.[0];
    if (!file) return;

    if (!vbIsSupportedCoverFile(file)) {
        if (typeof showMessage === 'function') {
            showMessage('Unsupported cover file format. Supported: PDF, PNG, JPG, JPEG, WEBP, GIF, BMP.', 'error');
        }
        const input = document.getElementById('vbCoverPageFileInput');
        if (input) {
            input.value = '';
        }
        return;
    }

    const formData = new FormData();
    formData.append('file', file);

    showLoading('Uploading cover page file...');
    fetch('/api/builder/upload-cover-file', {
        method: 'POST',
        headers: {
            ...vbGetCsrfHeaders()
        },
        body: formData
    })
        .then((response) => vbReadJsonResponse(response, 'Unable to upload cover page file'))
        .then((data) => {
            hideLoading();

            const coverImageData = String(data?.coverImageData || '');
            if (!coverImageData) {
                throw new Error('Server did not return cover image data.');
            }

            const hiddenInput = document.getElementById('vbCoverPageFileData');
            const status = document.getElementById('vbCoverPageFileStatus');
            if (hiddenInput) {
                hiddenInput.value = coverImageData;
            }
            if (status) {
                status.textContent = data?.convertedFromPdf
                    ? `Cover page PDF converted: ${file.name}`
                    : `Cover page file selected: ${file.name}`;
            }

            vbRenderCoverLivePreview();
        })
        .catch((error) => {
            hideLoading();

            const input = document.getElementById('vbCoverPageFileInput');
            if (input) {
                input.value = '';
            }

            if (typeof showMessage === 'function') {
                showMessage(error.message || 'Unable to upload cover page file.', 'error');
            }
        });
}

function vbRemoveCoverPageFile() {
    const hiddenInput = document.getElementById('vbCoverPageFileData');
    const status = document.getElementById('vbCoverPageFileStatus');
    const fileInput = document.getElementById('vbCoverPageFileInput');

    if (hiddenInput) {
        hiddenInput.value = '';
    }
    if (status) {
        status.textContent = 'No cover page file selected';
    }
    if (fileInput) {
        fileInput.value = '';
    }

    vbRenderCoverLivePreview();
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

    vbRenderCoverLivePreview();
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

function vbBuildGenerationDesignData() {
    const reportNameInput = document.getElementById('vbReportName');
    const outputFormatSelect = document.getElementById('vbOutputFormat');
    const reportName = (reportNameInput?.value || 'Report').trim();
    const reportFormat = (outputFormatSelect?.value || 'pdf').trim().toLowerCase();
    const sharedCoverOptions = vbBuildSharedCoverOptions('visual');
    const coverPageEnabled = sharedCoverOptions.coverPageEnabled === true;

    if (VB.elements.length === 0 && !coverPageEnabled) {
        if (typeof showMessage === 'function') {
            showMessage('Add elements to the canvas or enable a cover page first.', 'error');
        }
        return null;
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
        ...sharedCoverOptions,
    };

    const designData = {
        reportName,
        reportFormat,
        elements: VB.elements,
        pageSettings,
        reportOptions,
        datasourceId: VB.datasourceId
    };

    return designData;
}

function vbSubmitGeneration(mode = 'jrxml') {
    const endpointByMode = {
        'jrxml': '/api/builder/visual/generate',
        'jrxml-and-report': '/api/builder/visual/generate-and-report',
        'report-only': '/api/builder/visual/generate-report-only'
    };

    const loadingByMode = {
        'jrxml': 'Saving JRXML template...',
        'jrxml-and-report': 'Saving JRXML template and generating report...',
        'report-only': 'Generating report (without saving JRXML template)...'
    };

    const designData = vbBuildGenerationDesignData();
    if (!designData) {
        return;
    }

    showLoading(loadingByMode[mode] || 'Processing...');

    fetch(endpointByMode[mode] || endpointByMode.jrxml, {
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

            if (typeof loadJrxmlTemplates === 'function' && mode !== 'report-only') {
                loadJrxmlTemplates();
            }
            if (typeof loadGeneratedReports === 'function' && mode !== 'jrxml') {
                loadGeneratedReports(0);
            }
            if (typeof switchReportsSubTab === 'function' && mode !== 'jrxml') {
                switchReportsSubTab('available-reports');
            }

            const generatedName = data.reportName || (String(designData.reportName || 'Report').endsWith('.jrxml')
                ? String(designData.reportName || 'Report')
                : `${String(designData.reportName || 'Report')}.jrxml`);
            if (mode === 'jrxml') {
                vbOpenResultModal('Template Saved', `${generatedName} is now available in "Available JRXML Templates".`);
            } else if (mode === 'jrxml-and-report') {
                const reportFileName = data.fileName || 'generated report';
                vbOpenResultModal('Template + Report Generated', `${generatedName} was saved and report ${reportFileName} is available in "Available Reports".`);
            } else {
                const reportFileName = data.fileName || 'generated report';
                vbOpenResultModal('Report Generated', `${reportFileName} is available in "Available Reports". JRXML template was not saved.`);
            }
        })
        .catch((error) => {
            hideLoading();
            if (typeof showMessage === 'function') {
                showMessage(error.message || 'Error generating report template', 'error');
            }
        });
}

function vbGenerate() {
    vbSubmitGeneration('jrxml');
}

function vbGenerateAndReport() {
    vbSubmitGeneration('jrxml-and-report');
}

function vbGenerateReportOnly() {
    vbSubmitGeneration('report-only');
}

function vbBuildSharedCoverOptions(target = 'visual') {
    const templateValue = String(vbGetCoverTemplateSelect(target)?.value || '').trim();
    const selectedTemplate = vbResolveCoverTemplateConfig(templateValue, target);

    if (!selectedTemplate) {
        return { coverPageEnabled: false };
    }

    return {
        coverPageEnabled: true,
        coverTitle: String(selectedTemplate.title || '').trim(),
        coverSubtitle: String(selectedTemplate.subtitle || '').trim(),
        coverAuthor: String(selectedTemplate.author || '').trim(),
        coverDateEnabled: selectedTemplate.showDate !== false,
        coverIncludeReportName: selectedTemplate.includeReportName !== false,
        coverDatePattern: String(selectedTemplate.datePattern || 'dd/MM/yyyy').trim(),
        coverAlignment: String(selectedTemplate.alignment || 'Center').trim(),
        coverTheme: String(selectedTemplate.theme || 'classicBlue').trim(),
        coverAccentEnabled: selectedTemplate.accentEnabled !== false,
        coverBackgroundShapeEnabled: selectedTemplate.backgroundShapeEnabled !== false,
        coverTitleSize: Number.isFinite(Number(selectedTemplate.titleSize))
            ? Math.max(12, Math.min(72, Number.parseInt(selectedTemplate.titleSize, 10)))
            : 30,
        coverSubtitleSize: Number.isFinite(Number(selectedTemplate.subtitleSize))
            ? Math.max(10, Math.min(48, Number.parseInt(selectedTemplate.subtitleSize, 10)))
            : 16,
        coverLogoData: String(selectedTemplate.logoData || '').trim(),
        coverPageFileData: String(selectedTemplate.coverPageFileData || '').trim()
    };
}

document.addEventListener('DOMContentLoaded', () => {
    setTimeout(() => {
        VB.init();
        vbRefreshCoverPresetOptions('custom');
        vbRefreshBuilderCoverTemplateSelectors();
        vbToggleCoverOptions();
        vbBindCoverPreviewEvents();
        vbHandleCoverTemplateSelectionChange('visual');
        vbHandleCoverTemplateSelectionChange('form');
        vbRenderCoverLivePreview();
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
