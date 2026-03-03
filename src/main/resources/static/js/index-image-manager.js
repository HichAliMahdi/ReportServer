// ========== Image Manager Functions ==========

        function openImageManager() {
            document.getElementById('imageManagerModal').style.display = 'block';
            loadImages();
        }

        function closeImageManager() {
            document.getElementById('imageManagerModal').style.display = 'none';
        }

        function uploadImage() {
            const fileInput = document.getElementById('imageUploadInput');
            const file = fileInput.files[0];

            if (!file) return;

            const formData = new FormData();
            formData.append('file', file);

            fetch('/api/builder/upload-image', {
                method: 'POST',
                body: formData
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showMessage('Image uploaded successfully', 'success');
                    loadImages();
                    fileInput.value = '';
                } else {
                    showMessage(data.message || 'Failed to upload image', 'error');
                }
            })
            .catch(error => {
                showMessage('Error uploading image: ' + error, 'error');
            });
        }

        function loadImages() {
            fetch('/api/builder/images')
                .then(response => response.json())
                .then(data => {
                    const gallery = document.getElementById('imageGallery');

                    if (data.images && data.images.length > 0) {
                        gallery.innerHTML = '';
                        data.images.forEach(image => {
                            const div = document.createElement('div');
                            div.className = 'image-gallery-item';
                            div.innerHTML = `
                                <img src="/${image.path}" alt="${image.name}">
                                <div class="image-actions">
                                    <button onclick="useImage('${image.path}')" style="background: #28a745; color: white;">Use</button>
                                    <button onclick="deleteImage('${image.name}')" style="background: #dc3545; color: white;">Delete</button>
                                </div>
                            `;
                            gallery.appendChild(div);
                        });
                    } else {
                        gallery.innerHTML = '<p style="color: #999; text-align: center; padding: 40px;">No images uploaded yet</p>';
                    }
                })
                .catch(error => {
                    console.error('Error loading images:', error);
                });
        }

        function useImage(imagePath) {
            if (visualBuilder.selectedElement) {
                const element = visualBuilder.elements.find(el => el.id === visualBuilder.selectedElement);
                if (element && element.type === 'image') {
                    element.imagePath = imagePath;
                    renderCanvas();
                    updatePropertiesPanel();
                    closeImageManager();
                }
            } else {
                showMessage('Please select an image element first', 'error');
            }
        }

        function selectImageForElement() {
            openImageManager();
        }

        function deleteImage(fileName) {
            if (!confirm('Are you sure you want to delete this image?')) return;

            fetch('/api/builder/images/' + fileName, {
                method: 'DELETE'
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showMessage('Image deleted successfully', 'success');
                    loadImages();
                } else {
                    showMessage(data.message || 'Failed to delete image', 'error');
                }
            })
            .catch(error => {
                showMessage('Error deleting image: ' + error, 'error');
            });
        }
