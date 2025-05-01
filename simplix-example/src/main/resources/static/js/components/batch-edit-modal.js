class BatchEditModal extends ModalManager {
    constructor(options = {}) {
        super('batchEditModal', options);
        this.templateId = options.templateId;
        this.onSave = options.onSave;
        this.selectedRows = [];
    }

    init() {
        this.createModal();
        this.setupEventHandlers();
        return super.init();
    }

    createModal() {
        const template = document.getElementById(this.templateId);
        if (!template) {
            throw new Error(`Template with id "${this.templateId}" not found`);
        }

        const modalHtml = `
            <div class="modal fade" id="batchEditModal" tabindex="-1" role="dialog" aria-labelledby="batchEditModalLabel">
                <div class="modal-dialog modal-dialog-centered modal-dialog-scrollable" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="batchEditModalLabel">일괄 수정</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body position-relative" style="max-height: 70vh; overflow-y: scroll;">
                            <form id="batchEditModalForm">
                                ${template.innerHTML}
                            </form>
                        </div>
                        <div class="modal-footer justify-content-between">
                            <div class="selected-count text-muted"></div>
                            <div>
                                <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">취소</button>
                                <button type="button" class="btn btn-sm btn-primary" onclick="batchEditModal.save()">저장</button>
                            </div>
                        </div>

                        <!-- 확인 오버레이 -->
                        <div id="confirmOverlay" class="position-absolute top-0 start-0 w-100 h-100 d-none" 
                             style="background: rgba(0,0,0,0.5); z-index: 1050;">
                            <div class="position-absolute top-50 start-50 translate-middle">
                                <div class="bg-white rounded p-4 shadow-sm" style="min-width: 300px;">
                                    <p class="text-center mb-4 confirm-message"></p>
                                    <div class="d-flex justify-content-center gap-2">
                                        <button type="button" class="btn btn-sm btn-secondary" onclick="batchEditModal.hideConfirm()">취소</button>
                                        <button type="button" class="btn btn-sm btn-primary" onclick="batchEditModal.confirmSave()">확인</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // 기존 모달이 있다면 제거
        const existingModal = document.getElementById('batchEditModal');
        if (existingModal) {
            existingModal.remove();
        }

        // 새 모달 추가
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        this.modalElement = document.getElementById('batchEditModal');
    }

    setupEventHandlers() {
        // 토글 스위치 이벤트 핸들러
        const toggles = this.modalElement.querySelectorAll('.section-toggle');
        toggles.forEach(toggle => {
            toggle.addEventListener('change', (e) => {
                const targetId = e.target.dataset.target;
                const section = document.getElementById(targetId);
                if (section) {
                    const inputs = section.querySelectorAll('input, select');
                    inputs.forEach(input => {
                        input.disabled = !e.target.checked;
                    });
                }
            });
        });
    }

    resetForm() {
        // 폼 초기화
        const form = this.modalElement.querySelector('#batchEditModalForm');
        if (form) {
            form.reset();
            
            // 모든 입력 필드 비활성화
            const inputs = form.querySelectorAll('input:not(.section-toggle), select');
            inputs.forEach(input => {
                input.disabled = true;
            });
        }

        // 확인 오버레이 초기화
        const overlay = this.modalElement.querySelector('#confirmOverlay');
        if (overlay) {
            overlay.classList.add('d-none');
            const message = overlay.querySelector('.confirm-message');
            if (message) {
                message.textContent = '';
            }
        }

        // requestData 초기화
        this.requestData = null;
    }

    show(selectedCount) {
        // 선택된 행 저장
        this.selectedRows = window.gridApi.getSelectedRows();
        
        // 선택된 항목 수 표시
        const countElement = this.modalElement.querySelector('.selected-count');
        if (countElement) {
            countElement.textContent = `${selectedCount}개 항목 선택됨`;
        }

        // 폼 초기화
        this.resetForm();

        // 모달 표시
        super.show();
    }

    save() {
        const form = this.modalElement.querySelector('form');
        if (!form) return;

        const enabledSections = Array.from(this.modalElement.querySelectorAll('.section-toggle'))
            .filter(toggle => toggle.checked)
            .map(toggle => toggle.dataset.target);

        if (enabledSections.length === 0) {
            SimpliXUtils.showToast('변경할 항목을 선택해주세요.', 'warning');
            return;
        }

        // 데이터를 객체로 변환하여 처리
        this.requestData = {
            ids: this.selectedRows.map(row => row.id)
        };

        enabledSections.forEach(sectionId => {
            const section = document.getElementById(sectionId);
            if (section) {
                const inputs = section.querySelectorAll('input, select');
                inputs.forEach(input => {
                    if (input.type === 'checkbox') {
                        this.requestData[input.name] = input.checked;
                    } else if (input.multiple) {
                        this.requestData[input.name] = Array.from(input.selectedOptions)
                            .map(option => option.value)
                            .filter(value => value !== '');
                    } else if (input.value) {
                        this.requestData[input.name] = input.value;
                    }
                });
            }
        });

        // 확인 오버레이 표시
        const overlay = this.modalElement.querySelector('#confirmOverlay');
        const message = overlay.querySelector('.confirm-message');
        message.textContent = `${this.selectedRows.length}건의 데이터가 수정됩니다.\n계속 진행하시겠습니까?`;
        overlay.classList.remove('d-none');
    }

    hide() {
        // 폼 초기화
        this.resetForm();
        
        // 부모 클래스의 hide 메서드 호출
        super.hide();
    }

    hideConfirm() {
        const overlay = this.modalElement.querySelector('#confirmOverlay');
        overlay.classList.add('d-none');
    }

    confirmSave() {
        if (typeof this.onSave === 'function') {
            this.onSave(this.requestData)
                .then(() => {
                    SimpliXUtils.showToast('저장되었습니다.', 'success');
                    this.hide();
                    window.gridApi.refreshInfiniteCache();
                })
                .catch(error => {
                    console.error('Save failed:', error);
                    SimpliXUtils.showToast('저장에 실패했습니다.', 'danger');
                });
        }
    }
} 