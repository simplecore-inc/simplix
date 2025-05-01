/**
 * 그리드 매니저 클래스
 */
class GridManager {
    constructor(options = {}) {
        this.options = {
            // 필수 옵션
            gridElementId: options.gridElementId,         // 그리드 엘리먼트 ID
            searchFormId: options.searchFormId,           // 검색폼 ID
            searchUrl: options.searchUrl,                 // 검색 API URL
            columnDefs: options.columnDefs,               // 컬럼 정의
            
            // 선택적 옵션
            storageKey: options.storageKey,              // 상태 저장용 키
            customButtons: options.customButtons || [],
            buttonGroups: options.buttonGroups || [],
            defaultColDef: options.defaultColDef || {},   // 기본 컬럼 설정
            gridOptions: options.gridOptions || {},       // 추가 그리드 옵션
            searchFields: options.searchFields || {},     // 검색 필드 설정
            
            // 페이징 관련 옵션
            pageSize: options.pageSize || 10,            // 페이지 크기
            maxBlocksInCache: options.maxBlocksInCache || 1, // 최대 캐시 블록 수
            
            // pageSize가 paginationPageSizeSelector에 없으면 추가하고 정렬
            paginationPageSizeSelector: (() => {
                const defaultSizes = options.paginationPageSizeSelector || [10, 30, 50, 100];
                const pageSize = options.pageSize;
                if (!defaultSizes.includes(pageSize)) {
                    defaultSizes.push(pageSize);
                    defaultSizes.sort((a, b) => a - b);
                }
                return defaultSizes;
            })(),
            
            // 지연시간 관련 옵션
            tooltipDelay: options.tooltipDelay || 0,     // 툴팁 표시 지연시간
            buttonPanelDelay: options.buttonPanelDelay || 0, // 버튼 패널 추가 지연시간
            pageRestoreDelay: options.pageRestoreDelay || 100, // 페이지 복원 지연시간
            
            // 이벤트 핸들러
            onGridReady: options.onGridReady,            // 그리드 준비 완료 시
            onSearch: options.onSearch,                  // 검색 시
            onReset: options.onReset,                    // 초기화 시
            onStateRestored: options.onStateRestored,     // 상태 복원 시
            onSelectionChanged: options.onSelectionChanged, // 선택 변경 시 (추가)

            // 그리드 기본 동작 옵션
            suppressCellFocus: options.suppressCellFocus ?? true,      // 셀 포커스 비활성화
            suppressRowHoverHighlight: options.suppressRowHoverHighlight ?? false, // 행 호버 하이라이트
            enableCellTextSelection: options.enableCellTextSelection ?? true,     // 셀 텍스트 선택 가능
            ensureDomOrder: options.ensureDomOrder ?? true,           // DOM 순서 보장
            animateRows: options.animateRows ?? true,                 // 행 애니메이션
            accentedSort: options.accentedSort ?? true,               // 악센트 문자 정렬
            enableBrowserTooltips: options.enableBrowserTooltips ?? true, // 브라우저 기본 툴팁

            // 오버레이 메시지 옵션
            loadingMessage: options.loadingMessage || '데이터를 불러오는 중입니다...', // 로딩 메시지
            noRowsMessage: options.noRowsMessage || '데이터가 없습니다.',           // 데이터 없음 메시지
            
            // 다중 선택 옵션
            rowSelection: {
                mode: options.rowSelection === 'single' ? 'singleRow' : 'multiRow',
                enableClickSelection: false,  // 행 클릭으로 선택 불가능
                checkboxes: true,  // 내장 체크박스 사용
                checkboxLocation: 'selectionColumn',  // 전용 체크박스 컬럼에 표시
                enableSelectionWithoutKeys: true,  // Ctrl 키 없이 다중 선택 가능
                headerCheckbox: false  // 헤더 체크박스 비활성화 (infinite scroll에서는 지원하지 않음)
            },

            // 스타일 관련 옵션
            rowClass: options.rowClass || '',                         // 행 기본 클래스
            rowHeight: options.rowHeight || 50,                       // 행 높이
            headerHeight: options.headerHeight || 50,                 // 헤더 높이
        };

        this.isChangingPageSize = false;  // 페이지 사이즈 변경 플래그 추가

        // searchBuilder는 항상 초기화
        this.searchBuilder = new SearchConditionBuilder().setPagination(0, this.options.pageSize);
        this.searchBuilder.sort = { orders: [] };

        // restore 체크
        const urlParams = new URLSearchParams(window.location.search);
        const isRestore = urlParams.get('restore') === 'true';

        if (isRestore && this.options.storageKey) {
            // restore인 경우 requestData 설정
            const state = JSON.parse(sessionStorage.getItem(this.options.storageKey));
            if (state) {
                this.requestData = {...state};
            }
        }
        
        // 그리드 초기화
        const gridDiv = document.querySelector(`#${this.options.gridElementId}`);
        const grid = agGrid.createGrid(gridDiv, this.createGridOptions());

        // 검색 폼 초기화
        this.setupSearchForm();
    }

    createGridOptions() {
        return {
            ...this.options.gridOptions,
            columnDefs: this.options.columnDefs,
            defaultColDef: {
                sortable: false,
                filter: false,
                resizable: true,
                minWidth: 50,
                unSortIcon: true,
                suppressHeaderMenuButton: true,
                ...this.options.gridOptions?.defaultColDef
            },
            
            // 기존 옵션들...
            rowModelType: 'infinite',
            pagination: true,
            paginationPageSize: this.options.pageSize,
            cacheBlockSize: this.options.pageSize,
            rowBuffer: this.options.pageSize,
            maxBlocksInCache: this.options.maxBlocksInCache,
            paginationPageSizeSelector: this.options.paginationPageSizeSelector,
            
            domLayout: 'autoHeight',
            rowSelection: this.options.rowSelection,
            
            // 그리드 기본 동작 옵션 적용
            suppressCellFocus: this.options.suppressCellFocus,
            suppressRowHoverHighlight: this.options.suppressRowHoverHighlight,
            enableCellTextSelection: this.options.enableCellTextSelection,
            ensureDomOrder: this.options.ensureDomOrder,
            animateRows: this.options.animateRows,
            accentedSort: this.options.accentedSort,
            enableBrowserTooltips: this.options.enableBrowserTooltips,
            
            // 오버레이 컴포넌트 설정
            overlayLoadingTemplate:
                `<div class="ag-overlay-loading-center">
                    <i class="ri-loader-4-line ri-spin"></i>
                    <span>${this.options.loadingMessage}</span>
                </div>`,
            overlayNoRowsTemplate:
                `<div class="ag-overlay-no-rows-center">
                    <i class="ri-information-line"></i>
                    <span>${this.options.noRowsMessage}</span>
                </div>`,
            
            // 스타일 관련 옵션
            rowClass: this.options.rowClass,
            rowHeight: this.options.rowHeight,
            headerHeight: this.options.headerHeight,
            
            // 테마 객체 직접 지정
            theme: 'legacy',
            
            onGridReady: params => {
                window.gridApi = params.api;

                // 상태 복원을 데이터소스 설정 전에 먼저 수행
                const urlParams = new URLSearchParams(window.location.search);
                if (urlParams.get('restore') === 'true' && this.options.storageKey) {
                    const state = JSON.parse(sessionStorage.getItem(this.options.storageKey));
                    if (state) {
                        this.requestData = {...state};

                        // 페이지 크기와 현재 페이지 설정
                        const pageSize = state.size || this.options.pageSize;
                        const currentPage = state.page || 0;
                        
                        // 그리드의 페이지 크기 설정
                        window.gridApi.setGridOption('paginationPageSize', pageSize);
                        
                        // 폼 값 복원
                        if (state.formValues) {
                            Object.entries(state.formValues).forEach(([key, value]) => {
                                const element = document.getElementById(key);
                                if (element) {
                                    element.value = value || '';
                                }
                            });
                        }
                        
                        // 정렬 상태 복원 - applyColumnState 사용
                        if (state.sort?.orders?.length > 0) {
                            const sortOrder = state.sort.orders[0];
                            window.gridApi.applyColumnState({
                                state: [{
                                    colId: sortOrder.field,
                                    sort: sortOrder.direction.toLowerCase()
                                }]
                            });
                        }

                        // restore 파라미터 제거 및 URL 업데이트
                        urlParams.delete('restore');
                        const newUrl = window.location.pathname + (urlParams.toString() ? '?' + urlParams.toString() : '');
                        window.history.replaceState({}, '', newUrl);

                        // 데이터소스 설정
                        const dataSource = {
                            getRows: params => {
                                // 첫 호출에서는 저장된 페이지로 강제 설정
                                if (this.requestData) {
                                    params.startRow = currentPage * pageSize;
                                    params.endRow = (currentPage + 1) * pageSize;
                                    
                                    const originalGetRows = this.createDataSource().getRows;
                                    const wrappedCallback = params.successCallback;
                                    params.successCallback = (rowsThisBlock, lastRow) => {
                                        wrappedCallback(rowsThisBlock, state.totalElements || lastRow);
                                        
                                        setTimeout(() => {
                                            window.gridApi.paginationGoToPage(currentPage);
                                        }, this.options.pageRestoreDelay);
                                    };
                                    originalGetRows(params);
                                } else {
                                    this.createDataSource().getRows(params);
                                }
                            }
                        };
                        
                        window.gridApi.setGridOption('datasource', dataSource);
                    }
                } else {
                    window.gridApi.setGridOption('datasource', this.createDataSource());
                }

                // 커스텀 버튼 추가 (한 번만 실행)
                if (this.options.customButtons?.length > 0) {
                    this.addCustomButtonPanel();
                }

                // 사용자 정의 onGridReady 호출
                if (this.options.onGridReady) {
                    this.options.onGridReady(params);
                }

                // 페이지 변경 이벤트 리스너 추가
                params.api.addEventListener('paginationChanged', () => {
                    const checkbox = document.getElementById('selectAllCheckbox');
                    if (checkbox) {
                        this.updateSelectAllCheckbox(checkbox);
                    }
                });
            },

            onSortChanged: () => {
                // gridApi가 초기화되지 않은 경우 무시
                if (!window.gridApi) return;

                if (this.options.storageKey) {
                    this.saveState();
                }
            },

            onPaginationChanged: (event) => {
                // gridApi가 초기화되지 않은 경우 무시
                if (!window.gridApi) return;
                
                // 현재 페이지 사이즈 가져오기
                const currentPageSize = window.gridApi.paginationGetPageSize();
                
                // 캐시 사이즈나 rowBuffer가 페이지 사이즈와 다른 경우 업데이트
                if (currentPageSize !== this.options.cacheBlockSize || currentPageSize !== this.options.rowBuffer) {
                    this.isChangingPageSize = true;  // 페이지 사이즈 변경 시작
                    
                    // 옵션 업데이트
                    this.options.cacheBlockSize = currentPageSize;
                    this.options.rowBuffer = currentPageSize;
                    
                    // 첫 페이지로 이동
                    window.gridApi.paginationGoToFirstPage();
                    
                    // 그리드 옵션 업데이트 (순서 중요)
                    window.gridApi.setGridOption('paginationPageSize', currentPageSize);
                    window.gridApi.setGridOption('cacheBlockSize', currentPageSize);
                    window.gridApi.setGridOption('rowBuffer', currentPageSize);
                }

                // 상태 저장
                if (this.options.storageKey) {
                    this.saveState();
                }
            },

            onSelectionChanged: () => {
                if (!window.gridApi) return;

                // 사용자 정의 onSelectionChanged 호출
                if (this.options.onSelectionChanged) {
                    this.options.onSelectionChanged(window.gridApi.getSelectedRows());
                }
            }
        };
    }

    createDataSource() {
        return {
            getRows: params => {
                // 로딩 상태 설정
                window.gridApi.setGridOption('loading', true);
                
                window.gridApi.deselectAll();
                
                let requestData;
                const pageSize = window.gridApi.paginationGetPageSize();
                
                // restore 데이터가 있는 경우
                if (this.requestData) {
                    requestData = {...this.requestData};
                    
                    // 저장된 페이지 크기와 현재 페이지 크기가 다른 경우 조정
                    if (requestData.size !== pageSize) {
                        const totalElements = requestData.totalElements || 0;
                        const currentRow = requestData.page * requestData.size;
                        requestData.page = Math.floor(currentRow / pageSize);
                        requestData.size = pageSize;
                    }
                    
                    // searchBuilder 상태 복원
                    this.searchBuilder.conditions = requestData.conditions || [];
                    this.searchBuilder.sort = requestData.sort || { orders: [] };
                    this.searchBuilder.setPagination(requestData.page, requestData.size);
                    
                    this.requestData = null;
                } else {
                    // 일반적인 경우 현재 페이지 계산
                    const currentPage = Math.floor(params.startRow / pageSize);
                    this.searchBuilder.setPagination(currentPage, pageSize);
                    
                    // 정렬 정보 설정
                    if (params.sortModel?.length > 0) {
                        const sort = params.sortModel[0];
                        this.searchBuilder.sort = {
                            orders: [{
                                field: sort.colId,
                                direction: sort.sort.toUpperCase()
                            }]
                        };
                    }
                    
                    requestData = this.searchBuilder.build();
                }
                
                // API 호출 로그 추가
                console.log('API Request:', {
                    startRow: params.startRow,
                    endRow: params.endRow,
                    pageSize: pageSize,
                    requestData: requestData
                });

                fetch(this.options.searchUrl, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(requestData)
                })
                .then(response => response.json())
                .then(data => {
                    if (data.body) {
                        const { content, totalElements } = data.body;
                        params.successCallback(content, totalElements);
                        
                        if (content.length === 0) {
                            window.gridApi.showNoRowsOverlay();
                        }

                        // 데이터 로드 성공 후 상태 저장
                        if (this.options.storageKey) {
                            const stateToSave = {
                                ...requestData,
                                totalElements
                            };
                            sessionStorage.setItem(this.options.storageKey, JSON.stringify(stateToSave));
                        }
                    } else {
                        throw new Error('데이터 없음');
                    }
                    // 로딩 상태 해제
                    window.gridApi.setGridOption('loading', false);
                })
                .catch(error => {
                    console.error('Error:', error);
                    params.failCallback();
                    window.gridApi.showNoRowsOverlay();
                    // 에러 시에도 로딩 상태 해제
                    window.gridApi.setGridOption('loading', false);
                });
            }
        };
    }

    setupSearchForm() {
        const form = document.getElementById(this.options.searchFormId);
        if (!form) return;

        // 검색 버튼 이벤트
        const searchButton = form.querySelector('#btnSearch');
        if (searchButton) {
            searchButton.addEventListener('click', (e) => {
                e.preventDefault();
                this.handleSearch();
            });
        }

        // 초기화 버튼 이벤트
        const resetButton = form.querySelector('#btnReset');
        if (resetButton) {
            resetButton.addEventListener('click', (e) => {
                e.preventDefault();
                form.reset();
                this.handleReset();
            });
        }
    }

    handleSearch() {
        // searchBuilder 초기화
        this.searchBuilder = new SearchConditionBuilder().setPagination(0, this.options.pageSize);
        
        // 검색 필드 조건 추가
        Object.entries(this.options.searchFields).forEach(([key, config]) => {
            const value = document.getElementById(key).value;
            if (value) {
                this.searchBuilder.addCondition(
                    'and',
                    config.field,
                    config.operator,
                    value
                );
            }
        });

        // 데이터소스 재설정
        window.gridApi.setGridOption('datasource', this.createDataSource());

        if (this.options.onSearch) {
            this.options.onSearch();
        }
    }

    handleReset() {
        // 검색 조건 초기화
        this.searchBuilder = new SearchConditionBuilder().setPagination(0, this.options.pageSize);
        
        // 데이터소스 재설정
        window.gridApi.setGridOption('datasource', this.createDataSource());

        if (this.options.onReset) {
            this.options.onReset();
        }
    }

    restoreState() {
        try {
            const savedState = localStorage.getItem(this.options.storageKey);
            if (savedState) {
                const state = JSON.parse(savedState);
                
                // 검색 폼 상태 복원
                if (state.searchForm && this.searchForm) {
                    Object.entries(state.searchForm).forEach(([key, value]) => {
                        const element = this.searchForm.elements[key];
                        if (element) {
                            if (element.type === 'select-multiple' && Array.isArray(value)) {
                                Array.from(element.options).forEach(option => {
                                    option.selected = value.includes(option.value);
                                });
                            } else {
                                element.value = value;
                            }
                        }
                    });
                }

                // 그리드 상태 복원
                if (state.gridState && window.gridApi) {
                    window.gridApi.setFilterModel(state.gridState.filterModel || null);
                    window.gridApi.setSortModel(state.gridState.sortModel || null);
                    
                    // 필터와 정렬이 적용된 후 데이터 새로고침
                    window.gridApi.refreshInfiniteCache();
                }
            }
        } catch (error) {
            console.error('Failed to restore state:', error);
        }
    }

    addCustomButtonPanel() {
        if (!this.options.customButtons?.length) return;

        setTimeout(() => {
            const pagingPanel = document.querySelector('.ag-paging-panel');
            if (!pagingPanel) return;

            const buttonContainer = document.createElement('div');
            buttonContainer.className = 'ag-paging-button-wrapper d-flex align-items-center gap-2';

            const buttonGroup = document.createElement('div');
            buttonGroup.className = 'btn-group btn-group-sm';

            // 전체 선택 버튼 추가
            const selectAllButton = document.createElement('button');
            selectAllButton.className = 'btn btn-secondary btn-xs d-flex align-items-center gap-1';
            selectAllButton.title = '전체 선택';

            // 체크박스 생성
            const checkbox = document.createElement('input');
            checkbox.type = 'checkbox';
            checkbox.className = 'form-check-input m-0';
            checkbox.id = 'selectAllCheckbox';
            checkbox.style.cursor = 'pointer';

            // 체크박스 이벤트 핸들러
            checkbox.addEventListener('change', (e) => {
                if (!window.gridApi) return;

                const currentPage = window.gridApi.paginationGetCurrentPage();
                const pageSize = window.gridApi.paginationGetPageSize();
                const startRow = currentPage * pageSize;
                const endRow = startRow + pageSize;
                const displayedRows = [];

                // 현재 페이지의 모든 행 가져오기
                for (let i = startRow; i < endRow; i++) {
                    const rowNode = window.gridApi.getDisplayedRowAtIndex(i);
                    if (rowNode) {
                        displayedRows.push(rowNode);
                    }
                }

                // 체크박스 상태에 따라 현재 페이지의 모든 행 선택/해제
                displayedRows.forEach(node => {
                    node.setSelected(e.target.checked);
                });
            });

            // 선택 상태 변경 감지 및 체크박스 상태 업데이트
            if (window.gridApi) {
                window.gridApi.addEventListener('selectionChanged', () => {
                    this.updateSelectAllCheckbox(checkbox);
                });
            }

            // 버튼에 체크박스와 텍스트 추가
            selectAllButton.appendChild(checkbox);
            selectAllButton.appendChild(document.createTextNode(' 전체'));

            // 버튼을 버튼 그룹의 첫 번째 항목으로 추가
            buttonGroup.appendChild(selectAllButton);

            // 나머지 버튼들 추가
            this.options.customButtons.forEach(buttonConfig => {
                const button = document.createElement('button');
                button.id = buttonConfig.id;
                button.className = buttonConfig.className || 'btn btn-secondary';
                button.title = buttonConfig.title || '';
                button.disabled = buttonConfig.disabled || false;
                
                if (buttonConfig.onclick) {
                    if (typeof buttonConfig.onclick === 'function') {
                        button.onclick = buttonConfig.onclick;
                    } else {
                        button.setAttribute('onclick', buttonConfig.onclick);
                    }
                }

                if (buttonConfig.icon) {
                    const icon = document.createElement('i');
                    icon.className = buttonConfig.icon;
                    button.appendChild(icon);
                }

                if (buttonConfig.text) {
                    if (buttonConfig.icon) {
                        button.appendChild(document.createTextNode(' '));
                    }
                    button.appendChild(document.createTextNode(buttonConfig.text));
                }

                buttonGroup.appendChild(button);
            });

            buttonContainer.appendChild(buttonGroup);
            pagingPanel.insertBefore(buttonContainer, pagingPanel.firstChild);
        }, this.options.buttonPanelDelay || 0);
    }

    // 전체 선택 체크박스 상태 업데이트
    updateSelectAllCheckbox(checkbox) {
        if (!window.gridApi || !checkbox) return;

        const currentPage = window.gridApi.paginationGetCurrentPage();
        const pageSize = window.gridApi.paginationGetPageSize();
        const startRow = currentPage * pageSize;
        const endRow = startRow + pageSize;
        const displayedRows = [];
        let selectedCount = 0;

        // 현재 페이지의 모든 행 확인
        for (let i = startRow; i < endRow; i++) {
            const rowNode = window.gridApi.getDisplayedRowAtIndex(i);
            if (rowNode) {
                displayedRows.push(rowNode);
                if (rowNode.isSelected()) {
                    selectedCount++;
                }
            }
        }

        const totalDisplayedRows = displayedRows.length;

        if (selectedCount === 0) {
            // 아무것도 선택되지 않은 경우
            checkbox.checked = false;
            checkbox.indeterminate = false;
        } else if (selectedCount === totalDisplayedRows) {
            // 현재 페이지의 모든 행이 선택된 경우
            checkbox.checked = true;
            checkbox.indeterminate = false;
        } else {
            // 일부만 선택된 경우
            checkbox.checked = false;
            checkbox.indeterminate = true;
        }
    }

    saveState() {
        if (!this.options.storageKey) return;
        
        // requestData가 있으면 그대로 사용, 없으면 searchBuilder에서 build
        const searchCondition = this.requestData || this.searchBuilder.build();

        // 검색 폼 값 저장
        const formValues = {};
        Object.keys(this.options.searchFields).forEach(key => {
            const element = document.getElementById(key);
            if (element) {
                formValues[key] = element.value;
            }
        });

        // 검색 조건과 폼 값을 함께 저장
        const stateToSave = {
            ...searchCondition,
            formValues
        };

        sessionStorage.setItem(this.options.storageKey, JSON.stringify(stateToSave));
    }

    updateSelectedCount() {
        if (!window.gridApi) return 0;  // gridApi가 없으면 0 반환
        
        const selectedRows = window.gridApi.getSelectedRows();
        const countElement = document.querySelector('.selected-count');
        if (countElement) {
            countElement.textContent = `${selectedRows.length}개 항목 선택됨`;
        }
        return selectedRows.length;
    }

    setupEventHandlers() {
        // onSelectionChanged 이벤트 핸들러
        this.gridOptions.onSelectionChanged = (event) => {
            const selectedRows = event.api.getSelectedRows();
            const selectedCount = selectedRows.length;
            
            // 선택된 행 수에 따라 버튼 활성화/비활성화
            const editBtn = document.getElementById('btnBatchEdit');
            const deleteBtn = document.getElementById('btnDeleteSelected');
            const compareBtn = document.getElementById('btnCompareSelected');
            
            if (editBtn) {
                editBtn.disabled = selectedCount === 0;
            }
            if (deleteBtn) {
                deleteBtn.disabled = selectedCount === 0;
            }
            if (compareBtn) {
                compareBtn.disabled = selectedCount < 2;
            }

            // 선택 항목 수 업데이트
            this.updateSelectedCount();

            // 사용자 정의 콜백 호출
            if (typeof this.options.onSelectionChanged === 'function') {
                this.options.onSelectionChanged(selectedRows);
            }
        };

        // 버튼 클릭 이벤트 핸들러들
        const editBtn = document.getElementById('btnBatchEdit');
        if (editBtn) {
            editBtn.onclick = () => {
                const selectedCount = window.gridApi.getSelectedRows().length;
                window.batchEditModal.show(selectedCount);
            };
        }
    }

    init() {
        // URL 파라미터 확인
        const urlParams = new URLSearchParams(window.location.search);
        const shouldRestore = urlParams.get('restore') === 'true';

        // 그리드 초기화
        this.initializeGrid();

        // 상태 복원이 요청된 경우
        if (shouldRestore && this.options.storageKey) {
            this.restoreState();
            // restore 파라미터 제거
            const newUrl = window.location.pathname;
            window.history.replaceState({}, '', newUrl);
        }

        // 검색 폼 이벤트 핸들러 설정
        this.setupSearchForm();

        return this;
    }

    initializeGrid() {
        try {
            const gridElement = document.getElementById(this.options.gridElementId);
            if (!gridElement) {
                throw new Error(`Grid element with id '${this.options.gridElementId}' not found`);
            }

            // 그리드 옵션 설정
            const gridOptions = {
                ...this.defaultGridOptions,
                ...this.options.gridOptions,
                columnDefs: this.options.columnDefs,
                // 다른 옵션들...
            };

            // 새로운 초기화 방식 사용
            const grid = agGrid.createGrid(gridElement, gridOptions);
            
            // 그리드 API 저장
            window.gridApi = grid.api;
            this.gridApi = grid.api;

            // 초기 데이터 로드
            this.loadData();

        } catch (error) {
            console.error('Grid initialization failed:', error);
            throw error;
        }
    }
} 