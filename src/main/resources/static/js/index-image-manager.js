// ========== Image Manager Functions ==========

// Get CSRF tokens from meta tags
const imgMgrCsrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
const imgMgrCsrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute('content');

function getImageMgrHeaders(additionalHeaders = {}) {
    const headers = { ...additionalHeaders };
    if (imgMgrCsrfToken && imgMgrCsrfHeader) {
        headers[imgMgrCsrfHeader] = imgMgrCsrfToken;
    }
    return headers;
}

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
                headers: getImageMgrHeaders(),
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
                        gallery.replaceChildren();
                        data.images.forEach(image => {
                            const div = document.createElement('div');
                            div.className = 'image-gallery-item';

                            const imageEl = document.createElement('img');
                            imageEl.src = '/' + image.path;
                            imageEl.alt = image.name;

                            const actions = document.createElement('div');
                            actions.className = 'image-actions';

                            const useButton = document.createElement('button');
                            useButton.type = 'button';
                            useButton.style.background = '#28a745';
                            useButton.style.color = 'white';
                            useButton.textContent = 'Use';
                            useButton.addEventListener('click', () => useImage(image.path));

                            const deleteButton = document.createElement('button');
                            deleteButton.type = 'button';
                            deleteButton.style.background = '#dc3545';
                            deleteButton.style.color = 'white';
                            deleteButton.textContent = 'Delete';
                            deleteButton.addEventListener('click', () => deleteImage(image.name));

                            actions.appendChild(useButton);
                            actions.appendChild(deleteButton);
                            div.appendChild(imageEl);
                            div.appendChild(actions);
                            gallery.appendChild(div);
                        });
                    } else {
                        gallery.replaceChildren();
                        const empty = document.createElement('p');
                        empty.style.color = '#999';
                        empty.style.textAlign = 'center';
                        empty.style.padding = '40px';
                        empty.textContent = 'No images uploaded yet';
                        gallery.appendChild(empty);
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
                method: 'DELETE',
                headers: getImageMgrHeaders()
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
