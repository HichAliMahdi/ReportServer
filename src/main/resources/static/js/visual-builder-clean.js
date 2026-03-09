// ========== CLEAN VISUAL BUILDER ==========

const VB = {
    canvas: null,
    elements: [],
    selectedId: null,
    nextId: 1,
    currentBand: 'detail',
    dragState: null,
    datasourceId: null,
    availableTables: [],
    tableColumns: {},

    init() {
        this.canvas = document.getElementById('vbCanvas');
        if (!this.canvas) {
            console.error('Canvas not found!');
            return;
        }
        console.log('Visual Builder initialized');
        this.setupCanvasDragDrop();
        this.loadDatasources();
        this.render();
    },

    loadDatasources() {
        fetch('/api/datasources')
            .then(response => response.json())
            .then(datasources => {
                const select = document.getElementById('vbDatasourceSelect');
                select.innerHTML = '<option value="">🗄️ Select Datasource</option>';
                
                datasources.forEach(ds => {
                    if (ds.type === 'JDBC') { // Only show JDBC datasources for table browsing
                        const option = document.createElement('option');
                        option.value = ds.id;
                        option.textContent = `${ds.name} (${ds.type})`;
                        select.appendChild(option);
                    }
                });
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
            this.canvas.style.backgroundColor = 'rgba(0, 123, 255, 0.15)';
            this.canvas.style.borderColor = '#0056b3';
        }, false);

        this.canvas.addEventListener('dragleave', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.canvas.style.backgroundColor = 'white';
            this.canvas.style.borderColor = '#007bff';
        }, false);

        this.canvas.addEventListener('drop', (e) => {
            e.preventDefault();
            e.stopPropagation();
            this.canvas.style.backgroundColor = 'white';
            this.canvas.style.borderColor = '#007bff';

            const elementType = e.dataTransfer.getData('elementType');
            const tableName = e.dataTransfer.getData('tableName');
            console.log('Dropped element type:', elementType, 'tableName:', tableName);

            if (!elementType) {
                console.error('No elementType in drop data');
                return;
            }

            const rect = this.canvas.getBoundingClientRect();
            const scrollLeft = this.canvas.scrollLeft || 0;
            const scrollTop = this.canvas.scrollTop || 0;

            const x = Math.max(10, e.clientX - rect.left + scrollLeft - 60);
            const y = Math.max(10, e.clientY - rect.top + scrollTop - 12);

            console.log('Adding element at position:', x, y);
            
            if (elementType === 'dbTable' && tableName) {
                this.addElementAtPosition(elementType, x, y, { tableName });
            } else {
                this.addElementAtPosition(elementType, x, y);
            }
        }, false);
    },

    addElementAtPosition(type, x, y) {
        const id = this.nextId++;

        const element = {
            id,
            type,
            band: this.currentBand,
            x,
            y,
            width: 120,
            height: 25,
            text: this.getDefaultText(type),
            fontSize: 12,
            color: '#000000'
        };

        this.elements.push(element);
        this.render();
        this.select(id);
    },

    addElement(type) {
        const id = this.nextId++;
        const x = 50 + (id % 3) * 40;
        const y = 50 + Math.floor(id / 3) * 40;

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

        const bandElements = this.elements.filter((el) => el.band === this.currentBand);
        const placeholder = document.getElementById('vbPlaceholder');

        const elementDivs = this.canvas.querySelectorAll('.vb-element');
        elementDivs.forEach((div) => div.remove());

        if (bandElements.length === 0) {
            if (placeholder) placeholder.style.display = 'block';
            return;
        }

        if (placeholder) placeholder.style.display = 'none';

        console.log('Rendering', bandElements.length, 'elements in band', this.currentBand);

        bandElements.forEach((el) => {
            const div = document.createElement('div');
            div.className = 'vb-element' + (el.id === this.selectedId ? ' selected' : '');
            div.style.left = el.x + 'px';
            div.style.top = el.y + 'px';
            div.style.width = el.width + 'px';
            div.style.height = el.height + 'px';
            div.style.fontSize = el.fontSize + 'px';
            div.style.color = el.color;
            div.setAttribute('data-element-id', el.id);

            if (el.type === 'line') {
                div.style.height = '2px';
                div.style.background = '#000';
                div.style.border = 'none';
            } else if (el.type === 'rectangle') {
                div.style.background = 'transparent';
                div.style.border = '2px solid #000';
            } else if (el.type === 'logo') {
                div.style.background = '#f0f0f0';
                div.style.border = '2px dashed #ccc';
                div.textContent = '🎨 Logo';
                div.style.display = 'flex';
                div.style.alignItems = 'center';
                div.style.justifyContent = 'center';
            } else if (el.type === 'table') {
                div.style.background = '#f9f9f9';
                div.style.border = '1px solid #ddd';
                div.textContent = '📊 Table';
                div.style.display = 'flex';
                div.style.alignItems = 'center';
                div.style.justifyContent = 'center';
            } else if (el.type === 'dbTable') {
                div.style.background = '#f8f9fa';
                div.style.border = '2px solid #667eea';
                div.style.overflow = 'hidden';
                div.style.padding = '5px';
                div.style.fontSize = (el.fontSize || 10) + 'px';
                
                // Create a mini preview of the table
                let tableHTML = `<div style="font-weight: bold; color: #667eea; margin-bottom: 3px;">📊 ${el.tableName || 'Table'}</div>`;
                if (el.selectedColumns && el.selectedColumns.length > 0) {
                    tableHTML += '<div style="font-size: 8px; color: #666;">';
                    el.selectedColumns.slice(0, 5).forEach(col => {
                        tableHTML += `<div style="white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${col}</div>`;
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

        element.x = Math.max(0, VB.dragState.originalX + deltaX);
        element.y = Math.max(0, VB.dragState.originalY + deltaY);

        VB.render();
    },

    endDrag() {
        VB.dragState = null;
        document.onmousemove = null;
        document.onmouseup = null;
    },

    updateProperties() {
        const panel = document.getElementById('vbPropsPanel');

        if (!this.selectedId) {
            panel.innerHTML = 'Select an element to edit';
            return;
        }

        const el = this.elements.find((item) => item.id === this.selectedId);
        if (!el) return;

        let html = `
            <div style="margin-bottom: 15px;">
                <strong style="color: #007bff;">${el.type.toUpperCase()}</strong><br>
                <small style="color: #666;">ID: ${el.id}</small>
            </div>
        `;

        if (el.type === 'logo') {
            html += `
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Logo Image:</label>
                    <button onclick="document.getElementById('vbLogoInput').click()" style="width: 100%; padding: 4px; background: #007bff; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px;">📤 Upload Image</button>
                </div>
            `;
        } else if (el.type === 'table') {
            html += `
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Columns:</label>
                    <input type="number" value="${el.numColumns || 3}" min="2" max="10" onchange="VB.updateElement('numColumns', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
                </div>

                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Rows:</label>
                    <input type="number" value="${el.rows || 5}" min="2" max="20" onchange="VB.updateElement('rows', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
                </div>
            `;
        } else if (el.type === 'dbTable') {
            html += `
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px; font-weight: bold;">Table: ${el.tableName || 'Unknown'}</label>
                </div>
                
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Font Size:</label>
                    <input type="number" value="${el.fontSize || 10}" min="6" max="16" onchange="VB.updateElement('fontSize', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
                </div>
                
                <div style="margin-bottom: 10px;">
                    <label style="display: flex; align-items: center; gap: 5px; font-size: 12px;">
                        <input type="checkbox" ${el.showHeaders ? 'checked' : ''} onchange="VB.updateElement('showHeaders', this.checked)">
                        Show Headers
                    </label>
                </div>
                
                <div style="margin-bottom: 10px;">
                    <label style="display: flex; align-items: center; gap: 5px; font-size: 12px;">
                        <input type="checkbox" ${el.headerBold ? 'checked' : ''} onchange="VB.updateElement('headerBold', this.checked)">
                        Bold Headers
                    </label>
                </div>
                
                <div style="margin-bottom: 10px;">
                    <label style="display: flex; align-items: center; gap: 5px; font-size: 12px;">
                        <input type="checkbox" ${el.alternateRows ? 'checked' : ''} onchange="VB.updateElement('alternateRows', this.checked)">
                        Alternate Rows
                    </label>
                </div>
                
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 5px; font-weight: bold;">Columns to Display:</label>
                    <div style="max-height: 150px; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px; padding: 5px; font-size: 11px;">
                        ${el.columns ? el.columns.map((col, idx) => `
                            <label style="display: flex; align-items: center; gap: 5px; padding: 2px; cursor: pointer;">
                                <input type="checkbox" ${el.selectedColumns && el.selectedColumns.includes(col) ? 'checked' : ''} 
                                       onchange="vbToggleColumn('${col}', this.checked)">
                                <span>${col}</span>
                            </label>
                        `).join('') : '<div style="color: #999;">No columns</div>'}
                    </div>
                </div>
            `;
        } else {
            html += `
                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Text:</label>
                    <input type="text" value="${el.text}" onchange="VB.updateElement('text', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
                </div>

                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Font Size:</label>
                    <input type="number" value="${el.fontSize}" min="8" max="32" onchange="VB.updateElement('fontSize', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
                </div>

                <div style="margin-bottom: 10px;">
                    <label style="font-size: 12px; display: block; margin-bottom: 3px;">Color:</label>
                    <input type="color" value="${el.color}" onchange="VB.updateElement('color', this.value)" style="width: 100%; padding: 4px; cursor: pointer;">
                </div>
            `;
        }

        html += `
            <div style="margin-bottom: 10px;">
                <label style="font-size: 12px; display: block; margin-bottom: 3px;">Width:</label>
                <input type="number" value="${el.width}" min="20" onchange="VB.updateElement('width', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
            </div>

            <div style="margin-bottom: 10px;">
                <label style="font-size: 12px; display: block; margin-bottom: 3px;">Height:</label>
                <input type="number" value="${el.height}" min="20" onchange="VB.updateElement('height', this.value)" style="width: 100%; padding: 4px; font-size: 12px;">
            </div>

            <button onclick="VB.delete()" style="width: 100%; padding: 6px; background: #dc3545; color: white; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; margin-top: 10px;">🗑️ Delete</button>
        `;

        panel.innerHTML = html;
    },

    updateElement(property, value) {
        const el = this.elements.find((item) => item.id === this.selectedId);
        if (!el) return;

        if (property === 'fontSize' || property === 'width' || property === 'height' || property === 'numColumns' || property === 'rows') {
            el[property] = parseInt(value, 10);
        } else {
            el[property] = value;
        }

        this.render();
        this.updateProperties();
    }
};

function vbStartDrag(event, elementType) {
    console.log('Starting drag for:', elementType);
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
    const select = document.getElementById('vbBandSelect');
    VB.currentBand = select.value;
    VB.selectedId = null;
    VB.render();
    VB.updateProperties();
    console.log('Band changed to:', VB.currentBand);
}

function vbClear() {
    if (!confirm('Clear all elements from the canvas?')) return;
    VB.elements = VB.elements.filter((el) => el.band !== VB.currentBand);
    VB.selectedId = null;
    VB.render();
    VB.updateProperties();
}

function vbGenerate() {
    const reportName = document.getElementById('vbReportName').value || 'Report';

    if (VB.elements.length === 0) {
        alert('Add some elements to the canvas first!');
        return;
    }

    let jrxml = `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd" name="${reportName}" pageWidth="595" pageHeight="842" columnWidth="555" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
    <detail>
        <band height="50">`;

    VB.elements.forEach((el) => {
        jrxml += `
            <staticText>
                <reportElement x="${el.x}" y="${el.y}" width="${el.width}" height="${el.height}" />
                <text><![CDATA[${el.text}]]></text>
            </staticText>`;
    });

    jrxml += `
        </band>
    </detail>
</jasperReport>`;

    console.log('Generated JRXML:', jrxml);
    alert('Report generated! Check console for JRXML.');
}

document.addEventListener('DOMContentLoaded', () => {
    console.log('Page loaded, initializing Visual Builder...');
    setTimeout(() => {
        VB.init();
    }, 200);
});

function vbTest() {
    console.log('Visual Builder Status:');
    console.log('- Canvas found:', !!VB.canvas);
    console.log('- Elements:', VB.elements.length);
    console.log('- Current band:', VB.currentBand);

    VB.addElement('text');
    console.log('Test element added! Try dragging more elements now.');
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
        const logoData = {
            id,
            type: 'logo',
            band: VB.currentBand,
            x: 50,
            y: 50,
            width: 150,
            height: 60,
            text: '',
            fontSize: 12,
            color: '#000000',
            imageData: e.target.result
        };

        VB.elements.push(logoData);
        VB.render();
        VB.select(id);
        console.log('Logo added! You can drag to reposition or resize.');
    };
    reader.readAsDataURL(file);
}

function vbAddTable() {
    if (!VB.datasourceId) {
        alert('Please select a datasource first');
        return;
    }
    
    const columns = prompt('Enter number of columns (2-10):', '3');
    if (!columns || isNaN(columns) || columns < 2 || columns > 10) {
        alert('Please enter a valid number between 2 and 10');
        return;
    }

    const numColumns = parseInt(columns, 10);
    const id = VB.nextId++;

    const tableData = {
        id,
        type: 'table',
        band: VB.currentBand,
        x: 50,
        y: 100,
        width: Math.min(500, 50 * numColumns),
        height: 150,
        text: '',
        fontSize: 12,
        color: '#000000',
        numColumns,
        rows: 5,
        tableName: null,
        columns: []
    };

    VB.elements.push(tableData);
    VB.render();
    VB.select(id);
    console.log(`Table with ${numColumns} columns added!`);
}

// Load tables from selected datasource
function vbLoadTables() {
    const datasourceId = document.getElementById('vbDatasourceSelect').value;
    const tablesList = document.getElementById('vbTablesList');
    
    if (!datasourceId) {
        tablesList.innerHTML = '<div style="font-size: 12px; color: #999;">Select a datasource first</div>';
        VB.datasourceId = null;
        VB.availableTables = [];
        return;
    }
    
    VB.datasourceId = parseInt(datasourceId);
    tablesList.innerHTML = '<div style="font-size: 12px; color: #999;">Loading tables...</div>';
    
    fetch(`/api/builder/datasources/${datasourceId}/tables`)
        .then(response => response.json())
        .then(data => {
            if (data.success && data.tables && data.tables.length > 0) {
                VB.availableTables = data.tables;
                
                let html = '';
                data.tables.forEach(table => {
                    html += `<button class="vb-toolbox-btn" 
                                draggable="true" 
                                ondragstart="vbStartTableDrag(event, '${table}')" 
                                ondragend="vbEndDrag(event)"
                                onclick="vbAddTableFromDB('${table}')"
                                title="Drag to canvas or click to add">
                                📊 ${table}
                            </button>`;
                });
                tablesList.innerHTML = html;
            } else {
                tablesList.innerHTML = '<div style="font-size: 12px; color: #999;">No tables found</div>';
                VB.availableTables = [];
            }
        })
        .catch(error => {
            console.error('Error loading tables:', error);
            tablesList.innerHTML = '<div style="font-size: 12px; color: #dc3545;">Error loading tables</div>';
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
function vbAddTableFromDB(tableName) {
    if (!VB.datasourceId) {
        alert('Please select a datasource first');
        return;
    }
    
    showLoading('Loading table columns...');
    
    // Load columns for this table
    fetch(`/api/builder/datasources/${VB.datasourceId}/tables/${tableName}/columns`)
        .then(response => response.json())
        .then(data => {
            hideLoading();
            
            if (data.success && data.columns && data.columns.length > 0) {
                VB.tableColumns[tableName] = data.columns;
                
                const id = VB.nextId++;
                const columnWidth = 120;
                const totalWidth = Math.min(600, columnWidth * data.columns.length);
                
                const tableData = {
                    id,
                    type: 'dbTable',
                    band: VB.currentBand,
                    x: 50,
                    y: 100,
                    width: totalWidth,
                    height: 200,
                    text: tableName,
                    fontSize: 10,
                    color: '#000000',
                    tableName: tableName,
                    columns: data.columns.slice(0, 10), // Limit to first 10 columns
                    selectedColumns: data.columns.slice(0, 10),
                    showHeaders: true,
                    headerBold: true,
                    alternateRows: true
                };
                
                VB.elements.push(tableData);
                VB.render();
                VB.select(id);
            } else {
                alert('No columns found for this table');
            }
        })
        .catch(error => {
            hideLoading();
            console.error('Error loading columns:', error);
            alert('Error loading table columns: ' + error.message);
        });
}

// Update the addElementAtPosition to handle dbTable drops
VB.addElementAtPositionOriginal = VB.addElementAtPosition;
VB.addElementAtPosition = function(type, x, y, data = {}) {
    if (type === 'dbTable' && data.tableName) {
        vbAddTableFromDB(data.tableName);
        // Adjust position of the last added element
        setTimeout(() => {
            if (VB.elements.length > 0) {
                const lastEl = VB.elements[VB.elements.length - 1];
                lastEl.x = x;
                lastEl.y = y;
                VB.render();
            }
        }, 100);
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
    VB.updateProperties();
}
