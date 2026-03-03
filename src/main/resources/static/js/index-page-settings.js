// ========== Page Settings Functions ==========

        function openPageSettings() {
            document.getElementById('pageSettingsModal').style.display = 'block';
        }

        function closePageSettings() {
            document.getElementById('pageSettingsModal').style.display = 'none';
        }

        function applyPageSettings(event) {
            event.preventDefault();

            visualBuilder.pageSettings = {
                width: parseInt(document.getElementById('pageWidth').value),
                height: parseInt(document.getElementById('pageHeight').value),
                leftMargin: parseInt(document.getElementById('marginLeft').value),
                rightMargin: parseInt(document.getElementById('marginRight').value),
                topMargin: parseInt(document.getElementById('marginTop').value),
                bottomMargin: parseInt(document.getElementById('marginBottom').value),
                orientation: document.getElementById('pageOrientation').value
            };

            closePageSettings();
            showMessage('Page settings applied', 'success');
        }
