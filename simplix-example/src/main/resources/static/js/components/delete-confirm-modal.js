class DeleteConfirmModal extends ModalManager {
    constructor(modalId = 'deleteConfirmModal', options = {}) {
        super(modalId, options);
        this.confirmCode = '';
        this.callback = null;
        this.useConfirmCode = options.useConfirmCode ?? true; // 확인 문자 사용 여부 옵션
    }

    init() {
        this.createModal();
        this.setupInputHandlers();
        return super.init();
    }

    createModal() {
        const modalHtml = `
            <div class="modal fade" id="deleteConfirmModal" tabindex="-1" role="dialog" aria-labelledby="deleteConfirmModalLabel">
                <div class="modal-dialog modal-dialog-centered" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h5 class="modal-title" id="deleteConfirmModalLabel">삭제 확인</h5>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                        </div>
                        <div class="modal-body text-center">
                            <p class="delete-message mb-4"></p>
                            <div class="confirm-code-wrapper border rounded p-1 bg-light ${this.useConfirmCode ? '' : 'd-none'}">
                                <div class="confirm-code-guide">
                                    <div class="confirm-code-label mb-1">삭제하려면 확인 문자를 입력하세요</div>
                                    <div class="confirm-code-display mb-2">
                                        <span class="confirm-code-text fw-bold fs-5"></span>
                                    </div>
                                </div>
                                <div class="confirm-code-input d-flex gap-1 mb-2 justify-content-center">
                                    <input type="text" class="form-control form-control-sm text-center confirm-digit" maxlength="1" 
                                           placeholder="" style="text-transform: uppercase; width: 40px;">
                                    <input type="text" class="form-control form-control-sm text-center confirm-digit" maxlength="1" 
                                           placeholder="" style="text-transform: uppercase; width: 40px;">
                                    <input type="text" class="form-control form-control-sm text-center confirm-digit" maxlength="1" 
                                           placeholder="" style="text-transform: uppercase; width: 40px;">
                                    <input type="text" class="form-control form-control-sm text-center confirm-digit" maxlength="1" 
                                           placeholder="" style="text-transform: uppercase; width: 40px;">
                                </div>
                            </div>
                        </div>
                        <div class="modal-footer justify-content-between py-2">
                            <div class="selected-count text-muted"></div>
                            <div>
                                <button type="button" class="btn btn-sm btn-secondary" data-bs-dismiss="modal">취소</button>
                                <button type="button" class="btn btn-sm btn-danger" onclick="deleteConfirmModal.confirm()">삭제</button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        `;

        // 기존 모달이 있다면 제거
        const existingModal = document.getElementById(this.modalId);
        if (existingModal) {
            existingModal.remove();
        }

        // 새 모달 추가
        document.body.insertAdjacentHTML('beforeend', modalHtml);
        this.modalElement = document.getElementById(this.modalId);
    }

    generateRandomString() {
        const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
        let result = '';
        for (let i = 0; i < 4; i++) {
            result += chars.charAt(Math.floor(Math.random() * chars.length));
        }
        return result;
    }

    show(message, callback, selectedCount) {
        this.callback = callback;
        
        // 확인 문자 사용 시에만 코드 생성 및 입력란 초기화
        if (this.useConfirmCode) {
            this.confirmCode = this.generateRandomString();
        }
        
        const messageElement = this.modalElement.querySelector('.delete-message');
        const codeElement = this.modalElement.querySelector('.confirm-code-text');
        const countElement = this.modalElement.querySelector('.selected-count');
        const inputs = this.modalElement.querySelectorAll('.confirm-digit');
        const confirmButton = this.modalElement.querySelector('.btn-danger');
        
        if (messageElement) messageElement.textContent = message;
        if (this.useConfirmCode) {
            if (codeElement) codeElement.textContent = this.confirmCode;
            if (confirmButton) confirmButton.disabled = true;
            
            // 입력란 초기화
            inputs.forEach((input, i) => {
                input.value = '';
                input.style.borderColor = '';
                input.style.boxShadow = '';
                input.placeholder = this.confirmCode[i];
            });
            
            // 첫 번째 입력 필드에 포커스
            if (inputs.length > 0) {
                setTimeout(() => inputs[0].focus(), 100);
            }
        } else {
            if (confirmButton) confirmButton.disabled = false;
        }
        
        if (countElement) {
            countElement.textContent = selectedCount ? `${selectedCount}개 항목 선택됨` : '';
        }
        
        super.show();
    }

    setupInputHandlers() {
        const inputs = this.modalElement.querySelectorAll('.confirm-digit');
        const confirmButton = this.modalElement.querySelector('.btn-danger');

        inputs.forEach((input, index) => {
            input.addEventListener('input', (e) => {
                const value = e.target.value.toUpperCase();
                e.target.value = value;

                if (value.length === 1) {
                    if (index < inputs.length - 1) {
                        // 마지막 입력란이 아닌 경우 다음 입력란으로 이동
                        inputs[index + 1].focus();
                    } else {
                        // 마지막 입력란인 경우 전체 입력값 확인
                        const inputCode = Array.from(inputs).map(input => input.value.toUpperCase()).join('');
                        if (inputCode === this.confirmCode && confirmButton) {
                            // 입력이 유효하면 삭제 버튼으로 포커스 이동
                            confirmButton.disabled = false;
                            confirmButton.focus();
                        }
                    }
                }

                // 입력값 검증 및 스타일 적용
                const inputCode = Array.from(inputs).map(input => input.value.toUpperCase()).join('');
                const isValid = inputCode === this.confirmCode;
                
                inputs.forEach((input, i) => {
                    if (input.value) {
                        if (input.value.toUpperCase() === this.confirmCode[i]) {
                            input.style.borderColor = '#198754';  // Bootstrap success 색상
                            input.style.boxShadow = '0 0 0 0.25rem rgba(25, 135, 84, 0.25)';
                        } else {
                            input.style.borderColor = '#dc3545';  // Bootstrap danger 색상
                            input.style.boxShadow = '0 0 0 0.25rem rgba(220, 53, 69, 0.25)';
                        }
                    } else {
                        input.style.borderColor = '';
                        input.style.boxShadow = '';
                    }
                });

                if (confirmButton) {
                    confirmButton.disabled = !isValid;
                }
            });

            // 백스페이스 처리
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Backspace' && e.target.value.length === 0 && index > 0) {
                    inputs[index - 1].focus();
                    inputs[index - 1].value = '';  // 값 초기화
                    inputs[index - 1].style.borderColor = '';
                    inputs[index - 1].style.boxShadow = '';
                }
            });

            // 붙여넣기 처리
            input.addEventListener('paste', (e) => {
                e.preventDefault();
                const pastedText = (e.clipboardData || window.clipboardData)
                    .getData('text')
                    .toUpperCase()
                    .replace(/[^A-Z0-9]/g, '');

                if (pastedText.length > 0) {
                    // 각 문자를 해당 입력란에 분배
                    for (let i = 0; i < Math.min(pastedText.length, inputs.length - index); i++) {
                        inputs[index + i].value = pastedText[i];
                        inputs[index + i].dispatchEvent(new Event('input'));
                    }
                }
            });
        });
    }

    confirm() {
        if (!this.useConfirmCode || this.validateConfirmCode()) {
            if (typeof this.callback === 'function') {
                this.callback();
            }
            this.hide();
        }
    }

    validateConfirmCode() {
        if (!this.useConfirmCode) return true;
        
        const inputs = this.modalElement.querySelectorAll('.confirm-digit');
        const inputCode = Array.from(inputs).map(input => input.value.toUpperCase()).join('');
        return inputCode === this.confirmCode;
    }
} 