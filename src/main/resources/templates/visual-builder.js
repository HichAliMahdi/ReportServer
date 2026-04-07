// ========== CLEAN VISUAL BUILDER ==========

const VB = {
    canvas: null,
    elements: [],
    selectedId: null,
    nextId: 1,
    currentBand: 'detail',
    dragState: null,

    init() {
        this.canvas = document.getElementById('vbCanvas');
        if (!this.canvas) {
            console.error('Canvas not found!');
            return;
        }
        console.log('Visual Builder initialized');
        this.setupCanvasDragDrop();
        this.render();
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
            console.log('Dropped element type:', elementType);
            
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
            this.addElementAtPosition(elementType, x, y);
        }, false);
    },

    addElementAtPosition(type, x, y) {
        const id = this.nextId++;
        
        const element = {
            id,
            type,
            band: this.currentBand,
            x, y,
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
        // Default position for click-based additions
        const x = 50 + (id % 3) * 40;
        const y = 50 + Math.floor(id / 3) * 40;
        
        this.addElementAtPosition(type, x, y);
    },

    getDefaultText(type) {
        const texts = {
            'text': 'Text Label',
            'label': 'Label',
            'field': 'Field Name',
            'line': '',
            'rectangle': '',
            'pageNumber': 'Page',
            'date': 'Date'
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
        this.elements = this.elements.filter(el => el.id !== this.selectedId);
        this.selectedId = null;
        this.render();
        this.updateProperties();
    },

    render() {
        if (!this.canvas) {
            console.error('Canvas not initialized');
            return;
        }
        
        // Filter by band
        const bandElements = this.elements.filter(el => el.band === this.currentBand);
        
        // Clear canvas
        this.canvas.replaceChildren();
        
        if (bandElements.length === 0) {
            const emptyState = document.createElement('div');
            emptyState.style.padding = '40px';
            emptyState.style.textAlign = 'center';
            emptyState.style.color = '#999';
            emptyState.style.pointerEvents = 'none';

            const icon = document.createElement('p');
            icon.style.fontSize = '32px';
            icon.style.margin = '0';
            icon.textContent = '📄';

            const message = document.createElement('p');
            message.style.margin = '10px 0 0 0';
            message.append('Drag elements here');
            message.appendChild(document.createElement('br'));

            const small = document.createElement('small');
            small.style.fontSize = '12px';
            small.textContent = 'or click buttons to add';
            message.appendChild(small);

            emptyState.append(icon, message);
            this.canvas.appendChild(emptyState);
            return;
        }
        
        console.log('Rendering', bandElements.length, 'elements in band', this.currentBand);
        
        // Add elements
        bandElements.forEach(el => {
            const div = document.createElement('div');
            div.className = 'vb-element' + (el.id === this.selectedId ? ' selected' : '');
            div.style.left = el.x + 'px';
            div.style.top = el.y + 'px';
            div.style.width = el.width + 'px';
            div.style.height = el.height + 'px';
            div.style.fontSize = el.fontSize + 'px';
            div.style.color = el.color;
            div.setAttribute('data-element-id', el.id);
            
            // Set element content
            if (el.type === 'line') {
                div.style.height = '2px';
                div.style.background = '#000';
                div.style.border = 'none';
            } else if (el.type === 'rectangle') {
                div.style.background = 'transparent';
                div.style.border = '2px solid #000';
            } else {
                div.textContent = el.text;
            }
            
            // Add event listeners
            div.onclick = (e) => { e.stopPropagation(); this.select(el.id); };
            div.onmousedown = (e) => this.startDrag(e, el.id);
            
            this.canvas.appendChild(div);
        });
    },

    startDrag(e, elementId) {
        const element = this.elements.find(el => el.id === elementId);
        if (!element) return;
        
        this.dragState = {
            elementId,
            startX: e.clientX,
            startY: e.clientY,
            originalX: element.x,
            originalY: element.y
        };
        
        document.onmousemove = (e) => this.continueDrag(e);
        document.onmouseup = () => this.endDrag();
    },

    continueDrag(e) {
        if (!VB.dragState) return;
        
        const element = VB.elements.find(el => el.id === VB.dragState.elementId);
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
            panel.replaceChildren(document.createTextNode('Select an element to edit'));
            return;
        }
        
        const el = this.elements.find(e => e.id === this.selectedId);
        if (!el) return;

        const container = document.createElement('div');

        const summary = document.createElement('div');
        summary.style.marginBottom = '15px';
        const type = document.createElement('strong');
        type.style.color = '#007bff';
        type.textContent = String(el.type || '').toUpperCase();
        const lineBreak = document.createElement('br');
        const idLabel = document.createElement('small');
        idLabel.style.color = '#666';
        idLabel.textContent = `ID: ${el.id}`;
        summary.append(type, lineBreak, idLabel);

        const makeField = (labelText, input) => {
            const wrapper = document.createElement('div');
            wrapper.style.marginBottom = '10px';
            const label = document.createElement('label');
            label.style.fontSize = '12px';
            label.style.display = 'block';
            label.style.marginBottom = '3px';
            label.textContent = labelText;
            wrapper.append(label, input);
            return wrapper;
        };

        const makeInput = (type, value, minValue, maxValue, handler) => {
            const input = document.createElement('input');
            input.type = type;
            input.value = value;
            if (minValue != null) input.min = String(minValue);
            if (maxValue != null) input.max = String(maxValue);
            input.style.width = '100%';
            input.style.padding = '4px';
            input.style.fontSize = '12px';
            if (type === 'color') {
                input.style.cursor = 'pointer';
            }
            input.addEventListener('change', (event) => handler(event.target.value));
            return input;
        };

        const textInput = makeInput('text', el.text, null, null, (value) => VB.updateElement('text', value));
        const fontSizeInput = makeInput('number', el.fontSize, 8, 32, (value) => VB.updateElement('fontSize', value));
        const colorInput = makeInput('color', el.color, null, null, (value) => VB.updateElement('color', value));
        const widthInput = makeInput('number', el.width, 20, null, (value) => VB.updateElement('width', value));
        const heightInput = makeInput('number', el.height, 20, null, (value) => VB.updateElement('height', value));

        const deleteButton = document.createElement('button');
        deleteButton.type = 'button';
        deleteButton.style.width = '100%';
        deleteButton.style.padding = '6px';
        deleteButton.style.background = '#dc3545';
        deleteButton.style.color = 'white';
        deleteButton.style.border = 'none';
        deleteButton.style.borderRadius = '4px';
        deleteButton.style.cursor = 'pointer';
        deleteButton.style.fontSize = '12px';
        deleteButton.style.marginTop = '10px';
        deleteButton.textContent = 'Delete';
        deleteButton.addEventListener('click', () => VB.delete());

        container.append(
            summary,
            makeField('Text:', textInput),
            makeField('Font Size:', fontSizeInput),
            makeField('Color:', colorInput),
            makeField('Width:', widthInput),
            makeField('Height:', heightInput),
            deleteButton
        );

        panel.replaceChildren(container);
    },

    updateElement(property, value) {
        const el = this.elements.find(e => e.id === this.selectedId);
        if (!el) return;
        
        if (property === 'fontSize' || property === 'width' || property === 'height') {
            el[property] = parseInt(value);
        } else {
            el[property] = value;
        }
        
        this.render();
        this.updateProperties();
    }
};

// Visual Builder Functions (Global)
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
    VB.elements = VB.elements.filter(el => el.band !== VB.currentBand);
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
    
    // Build simple JRXML
    let jrxml = `<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd" name="${reportName}" pageWidth="595" pageHeight="842" columnWidth="555" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
    <detail>
        <band height="50">`;
    
    VB.elements.forEach(el => {
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

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded, initializing Visual Builder...');
    setTimeout(() => {
        VB.init();
    }, 200);
});

// Debug/Test function - can be called from console
function vbTest() {
    console.log('Visual Builder Status:');
    console.log('- Canvas found:', !!VB.canvas);
    console.log('- Elements:', VB.elements.length);
    console.log('- Current band:', VB.currentBand);
    
    // Add a test element
    VB.addElement('text');
    console.log('Test element added! Try dragging more elements now.');
}
