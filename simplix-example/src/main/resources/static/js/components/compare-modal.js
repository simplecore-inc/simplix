class CompareModalManager extends ModalManager {
    constructor(options = {}) {
        const modalId = options.modalId || 'compareModal';
        super(modalId, options);
        
        // 모달 옵션 설정
        this.modalOptions = {
            keyboard: true,
            backdrop: true,
            focus: true
        };

        // 컴포넌트 옵션 설정
        this.options = {
            itemMinWidth: 450,
            maxWidthRatio: 0.9,
            maxHeightRatio: 0.8,
            loadingTemplate: '<div class="spinner-border text-primary" role="status"><span class="visually-hidden">Loading...</span></div>',
            errorTemplate: '<div class="alert alert-danger">Failed to load details</div>',
            getDetailUrl: () => '',
            ...options
        };
    }

    init() {
        this.createModal();
        return super.init();
    }

    createModal() {
        // 기존 모달이 있다면 제거
        const existingModal = document.getElementById(this.modalId);
        if (existingModal) {
            existingModal.remove();
        }

        const modalHtml = `
            <div class="modal fade" id="${this.modalId}" tabindex="-1" role="dialog" aria-labelledby="compareModalLabel">
                <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="compareModalLabel">항목 비교</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body d-flex gap-3">
                            <!-- 동적으로 내용이 추가됨 -->
                        </div>
                        <div class="modal-footer justify-content-between">
                            <div class="selected-count text-muted"></div>
                            <div>
                                <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">닫기</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // 새 모달 추가
        document.body.insertAdjacentHTML('beforeend', modalHtml);
    }

    async compare(items) {
        if (!items?.length || items.length < 2) return;

        // 선택된 항목 수 표시
        const countElement = this.modalElement.querySelector('.selected-count');
        if (countElement) {
            countElement.textContent = `${items.length}개 항목 선택됨`;
        }

        // 모달 크기 조정
        const modalDialog = this.modalElement.querySelector('.modal-dialog');
        if (modalDialog) {
            const totalWidth = items.length * this.options.itemMinWidth;
            const maxWidth = window.innerWidth * this.options.maxWidthRatio;
            modalDialog.style.maxWidth = `${Math.min(totalWidth, maxWidth)}px`;
            modalDialog.style.width = '90vw';
        }

        // 모달 표시
        this.show();

        // 모달 바디 초기화
        const modalBody = this.modalElement.querySelector('.modal-body');
        if (!modalBody) return;
        
        modalBody.innerHTML = '';

        // 각 항목에 대한 로딩 처리
        const loadPromises = items.map(async (item) => {
            const container = document.createElement('div');
            Object.assign(container.style, {
                flexGrow: '1',
                minWidth: `${this.options.itemMinWidth}px`
            });
            
            container.innerHTML = this.options.loadingTemplate;
            modalBody.appendChild(container);
            
            try {
                const response = await fetch(this.options.getDetailUrl(item));
                if (!response.ok) throw new Error('Failed to load details');
                const html = await response.text();
                container.innerHTML = html;
            } catch (error) {
                console.error('Failed to load details:', error);
                container.innerHTML = this.options.errorTemplate;
            }
        });
        
        await Promise.all(loadPromises);
    }
} 