class ModalManager {
    constructor(modalId, options = {}) {
        this.modalId = modalId;
        this.modalElement = null;
        this.modal = null;
        this.modalOptions = {
            keyboard: true,
            backdrop: true,
            focus: true
        };
        this.options = options;
    }

    init() {
        this.modalElement = document.getElementById(this.modalId);
        if (!this.modalElement) {
            throw new Error(`Modal element with id "${this.modalId}" not found`);
        }

        // Bootstrap 모달 초기화
        this.modal = new bootstrap.Modal(this.modalElement, this.modalOptions);

        // ESC 키 이벤트 리스너 추가
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.modalElement.classList.contains('show')) {
                this.hide();
            }
        });

        return this;
    }

    show() {
        if (this.modal) {
            this.modal.show();
        }
    }

    hide() {
        if (this.modal) {
            this.modal.hide();
        }
    }
} 