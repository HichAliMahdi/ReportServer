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
        this.canvas.innerHTML = '';
        
        if (bandElements.length === 0) {
            this.canvas.innerHTML = `<div style="padding: 40px; text-align: center; color: #999; pointer-events: none;">
                <p style="font-size: 32px; margin: 0;">📄</p>
                <p style="margin: 10px 0 0 0;">Drag elements here<br><small style="font-size: 12px;">or click buttons to add</small></p>
            </div>`;
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
            panel.innerHTML = 'Select an element to edit';
            return;
        }
        
        const el = this.elements.find(e => e.id === this.selectedId);
        if (!el) return;
        
        let html = `
            <div style="margin-bottom: 15px;">
                <strong style="color: #007bff;">${el.type.toUpperCase()}</strong><br>
                <small style="color: #666;">ID: ${el.id}</small>
            </div>
            
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
