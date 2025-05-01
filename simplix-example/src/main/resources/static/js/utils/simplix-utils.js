// SimpliX 공통 유틸리티 함수
const SimpliXUtils = {
    init: function() {
        // Bootstrap 드롭다운 초기화는 자동으로 처리됨
        
        // 사이드바 토글 (모바일)
        const sidebarToggle = document.querySelector('[data-bs-toggle="sidebar"]');
        if (sidebarToggle) {
            sidebarToggle.addEventListener('click', () => {
                document.querySelector('.sidebar').classList.toggle('show');
            });
        }
    },

    /**
     * 검색 폼 초기화
     */
    initSearchForm: function(options) {
        const form = document.getElementById(options.formId);
        if (!form) return;

        // 검색 버튼 이벤트
        const searchButton = form.querySelector('#btnSearch');
        if (searchButton) {
            searchButton.addEventListener('click', (e) => {
                e.preventDefault();
                if (typeof options.onSearch === 'function') {
                    options.onSearch();
                }
            });
        }

        // 초기화 버튼 이벤트
        const resetButton = form.querySelector('#btnReset');
        if (resetButton) {
            resetButton.addEventListener('click', (e) => {
                e.preventDefault();
                if (typeof options.onReset === 'function') {
                    options.onReset();
                }
            });
        }

        // 엔터키 이벤트
        form.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                if (typeof options.onSearch === 'function') {
                    options.onSearch();
                }
            }
        });
    },

    // Grid.js 기본 설정
    gridConfig: {
        className: {
            table: 'table table-hover',
            thead: '',
            tbody: '',
            th: '',
            td: '',
            pagination: 'pagination',
            paginationButton: 'page-item',
            paginationButtonCurrent: 'active',
            paginationButtonNext: '',
            paginationButtonPrev: ''
        }
    },

    // 토스트 메시지 표시
    showToast: function(message, type = 'info') {
        const toastContainer = document.getElementById('toast-container') 
            || document.createElement('div');
        
        if (!toastContainer.id) {
            toastContainer.id = 'toast-container';
            toastContainer.className = 'toast-container position-fixed bottom-0 end-0 p-3';
            document.body.appendChild(toastContainer);
        }

        const toast = document.createElement('div');
        toast.className = `toast align-items-center text-white bg-${type}`;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'assertive');
        toast.setAttribute('aria-atomic', 'true');

        toast.innerHTML = `
            <div class="d-flex">
                <div class="toast-body">${message}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button>
            </div>
        `;

        toastContainer.appendChild(toast);
        const bsToast = new bootstrap.Toast(toast);
        bsToast.show();

        toast.addEventListener('hidden.bs.toast', () => {
            toast.remove();
        });
    }
};

// DOM 로드 시 초기화
document.addEventListener('DOMContentLoaded', function() {
    SimpliXUtils.init();
}); 