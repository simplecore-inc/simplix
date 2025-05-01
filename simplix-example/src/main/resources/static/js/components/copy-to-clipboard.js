/**
 * 클립보드 복사 기능을 위한 컴포넌트
 */
class CopyToClipboard {
    constructor() {
        this.init();
        this.setupModalListeners();
    }

    init(container = document) {
        // copy-to-clipboard 클래스를 가진 모든 요소에 대해 처리
        container.querySelectorAll('.copy-to-clipboard').forEach(element => {
            // 이미 초기화된 요소는 건너뛰기
            if (element.dataset.clipboardInitialized) return;
            
            // 복사 버튼 생성 및 추가
            const button = this.createCopyButton();
            const wrapper = this.wrapElement(element);
            wrapper.appendChild(button);

            // 클릭 이벤트 핸들러 추가
            button.addEventListener('click', () => this.copyText(element.textContent.trim(), button));
            
            // 초기화 완료 표시
            element.dataset.clipboardInitialized = 'true';
        });
    }

    setupModalListeners() {
        // 모달이 열릴 때마다 해당 모달 내부의 요소들 초기화
        document.addEventListener('shown.bs.modal', event => {
            const modal = event.target;
            this.init(modal);
        });

        // 동적으로 추가되는 컨텐츠를 위한 MutationObserver 설정
        const observer = new MutationObserver((mutations) => {
            mutations.forEach((mutation) => {
                if (mutation.addedNodes.length) {
                    mutation.addedNodes.forEach((node) => {
                        if (node.nodeType === 1) { // ELEMENT_NODE
                            this.init(node);
                        }
                    });
                }
            });
        });

        // 문서 전체를 감시
        observer.observe(document.body, {
            childList: true,
            subtree: true
        });
    }

    createCopyButton() {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-link btn-sm p-0 text-muted ms-2 text-decoration-none';
        button.title = '클립보드에 복사';
        
        const icon = document.createElement('i');
        icon.className = 'ri-file-copy-line';
        button.appendChild(icon);
        
        return button;
    }

    wrapElement(element) {
        // 이미 래퍼가 있는 경우 그대로 사용
        if (element.parentElement.classList.contains('copy-wrapper')) {
            return element.parentElement;
        }

        // 새로운 래퍼 생성
        const wrapper = document.createElement('div');
        wrapper.className = 'copy-wrapper d-flex align-items-center';
        element.parentNode.insertBefore(wrapper, element);
        wrapper.appendChild(element);
        return wrapper;
    }

    async copyText(text, button) {
        try {
            await navigator.clipboard.writeText(text);
            this.showCopySuccess(button);
            SimpliXUtils.showToast('클립보드에 복사되었습니다.', 'success');
        } catch (err) {
            console.error('클립보드 복사 실패:', err);
            SimpliXUtils.showToast('클립보드 복사에 실패했습니다.', 'danger');
        }
    }

    showCopySuccess(button) {
        const icon = button.querySelector('i');
        const originalClass = icon.className;
        icon.className = 'ri-check-line text-success';
        
        setTimeout(() => {
            icon.className = originalClass;
        }, 1000);
    }
}

// 전역 인스턴스 생성
window.copyToClipboard = new CopyToClipboard(); 