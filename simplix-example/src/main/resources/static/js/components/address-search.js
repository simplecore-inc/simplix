/**
 * 우편번호 검색 컴포넌트
 */
class AddressSearch {
    constructor() {
        this.init();
    }

    init() {
        // 우편번호 검색 버튼에 대한 처리
        document.querySelectorAll('.address-search').forEach(button => {
            // 이미 초기화된 버튼은 건너뛰기
            if (button.dataset.addressSearchInitialized) return;

            // data 속성에서 필드 ID 가져오기
            const postalCodeField = button.dataset.postalCodeField;
            const addressField = button.dataset.addressField;
            const addressDetailField = button.dataset.addressDetailField;

            // 클릭 이벤트 핸들러 추가
            button.addEventListener('click', () => {
                this.openAddressSearch(postalCodeField, addressField, addressDetailField);
            });

            // 초기화 완료 표시
            button.dataset.addressSearchInitialized = 'true';
        });
    }

    openAddressSearch(postalCodeField, addressField, addressDetailField) {
        new daum.Postcode({
            oncomplete: (data) => {
                // 우편번호 필드 설정
                if (postalCodeField) {
                    const postalCodeInput = document.getElementById(postalCodeField);
                    if (postalCodeInput) {
                        postalCodeInput.value = data.zonecode;
                    }
                }

                // 주소 필드 설정
                if (addressField) {
                    const addressInput = document.getElementById(addressField);
                    if (addressInput) {
                        addressInput.value = data.address;
                    }
                }

                // 상세주소 필드로 포커스 이동
                if (addressDetailField) {
                    const detailInput = document.getElementById(addressDetailField);
                    if (detailInput) {
                        detailInput.focus();
                    }
                }
            }
        }).open();
    }

    // 모달 내부 등 동적으로 추가되는 요소를 위한 재초기화 메서드
    reinitialize(container = document) {
        this.init();
    }
}

// 전역 인스턴스 생성
window.addressSearch = new AddressSearch();

// 모달이 열릴 때 재초기화
document.addEventListener('shown.bs.modal', event => {
    window.addressSearch.reinitialize(event.target);
}); 