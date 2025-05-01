/**
 * HTMX Select Handler
 * Handles the initialization and data loading for select elements with HTMX
 */
const HTMXSelect = {
    /**
     * Initialize HTMX select elements
     * @param {string} selector - CSS selector for HTMX select elements
     */
    init(selector = '.simplix-htmx-select') {
        document.addEventListener('DOMContentLoaded', () => {
            document.querySelectorAll(selector).forEach(select => {
                // Store initial options
                select._initialOptions = Array.from(select.options).map(opt => ({
                    value: opt.value,
                    text: opt.textContent
                }));
                select.addEventListener('htmx:afterOnLoad', this.handleResponse);
            });
        });
    },

    /**
     * Handle HTMX response and update select options
     * @param {Event} evt - HTMX after load event
     */
    handleResponse(evt) {
        try {
            const select = evt.target;
            const response = JSON.parse(evt.detail.xhr.response);
            const items = response.body?.content || [];
            
            // Clear current options
            select.innerHTML = '';
            
            // Restore initial options
            if (select._initialOptions) {
                select._initialOptions.forEach(opt => {
                    const option = document.createElement('option');
                    option.value = opt.value;
                    option.textContent = opt.text;
                    select.appendChild(option);
                });
            }

            // Add new options from response
            const existingValues = new Set(Array.from(select.options).map(opt => opt.value));
            items.forEach(item => {
                const value = item.id?.toString() || '';
                if (!existingValues.has(value)) {
                    const option = document.createElement('option');
                    option.value = value;
                    option.textContent = item.name;
                    select.appendChild(option);
                }
            });

            // Dispatch custom event
            select.dispatchEvent(new CustomEvent('optionsLoaded', {
                detail: { items }
            }));
        } catch (error) {
            console.error('Select options parsing error:', error);
            KRDS.showToast('목록을 불러오는데 실패했습니다.', 'error');
        }
    }
};

// Export for use in other files
window.HTMXSelect = HTMXSelect; 