/**
 * SearchCondition 빌더 클래스
 */
class SearchConditionBuilder {
    constructor() {
        this.conditions = [];
        this.page = 0;
        this.size = 10;
        this.sort = { orders: [] };
        this.direction = null;
    }

    /**
     * 단일 조건 추가
     */
    addCondition(operator = 'and', field, searchOperator = 'equals', value) {
        this.conditions.push({
            field: field,
            value: value,
            operator: operator,
            searchOperator: searchOperator
        });
        return this;
    }

    /**
     * 중첩 조건 그룹 추가
     */
    addConditionGroup(operator = 'and',conditions) {
        if (conditions.length > 0) {
            const group = {
                operator,
                conditions
            };

            if (this.conditions.length > 0) {
                group.operator = operator;
            }

            this.conditions.push(group);
        }
        return this;
    }

    /**
     * OR 조건 그룹 생성
     */
    addOrConditions(conditions) {
        return this.addConditionGroup(conditions, 'or');
    }

    /**
     * AND 조건 그룹 생성
     */
    addAndConditions(conditions) {
        return this.addConditionGroup(conditions, 'and');
    }

    /**
     * 정렬 설정 함수 수정
     */
    setSort(field, direction) {
        this.sort = {
            orders: [{
                field: field,
                direction: direction
            }]
        };
        return this;
    }

    /**
     * 페이징 설정
     */
    setPagination(page, size) {
        this.page = page;
        this.size = size;
        return this;
    }

    /**
     * 검색 조건 생성
     */
    build() {
        return {
            conditions: this.conditions,
            page: this.page,
            size: this.size,
            sort: this.sort
        };
    }
}

/**
 * 검색 조건 생성을 위한 헬퍼 함수들
 */
const SearchConditionHelper = {
    // Comparison operators
    equals(field, value) {
        return { field, searchOperator: 'equals', value, entityField: field };
    },
    
    notEquals(field, value) {
        return { field, searchOperator: 'notEquals', value, entityField: field };
    },
    
    greaterThan(field, value) {
        return { field, searchOperator: 'greaterThan', value, entityField: field };
    },
    
    greaterThanOrEqualTo(field, value) {
        return { field, searchOperator: 'greaterThanOrEqualTo', value, entityField: field };
    },
    
    lessThan(field, value) {
        return { field, searchOperator: 'lessThan', value, entityField: field };
    },
    
    lessThanOrEqualTo(field, value) {
        return { field, searchOperator: 'lessThanOrEqualTo', value, entityField: field };
    },
    
    // LIKE operators
    contains(field, value) {
        return { field, searchOperator: 'contains', value, entityField: field };
    },
    
    notContains(field, value) {
        return { field, searchOperator: 'notContains', value, entityField: field };
    },
    
    startsWith(field, value) {
        return { field, searchOperator: 'startsWith', value, entityField: field };
    },
    
    notStartsWith(field, value) {
        return { field, searchOperator: 'notStartsWith', value, entityField: field };
    },
    
    endsWith(field, value) {
        return { field, searchOperator: 'endsWith', value, entityField: field };
    },
    
    notEndsWith(field, value) {
        return { field, searchOperator: 'notEndsWith', value, entityField: field };
    },
    
    // NULL checks
    isNull(field) {
        return { field, searchOperator: 'isNull', value: null, entityField: field };
    },
    
    isNotNull(field) {
        return { field, searchOperator: 'isNotNull', value: null, entityField: field };
    },
    
    // IN operators
    in(field, values) {
        const value = Array.isArray(values) ? values.join(',') : values;
        return { field, searchOperator: 'in', value, entityField: field };
    },
    
    notIn(field, values) {
        const value = Array.isArray(values) ? values.join(',') : values;
        return { field, searchOperator: 'notIn', value, entityField: field };
    },
    
    // BETWEEN operators
    between(field, value1, value2) {
        return { 
            field, 
            searchOperator: 'between', 
            value: value1,
            value2: value2,
            entityField: field 
        };
    },
    
    notBetween(field, value1, value2) {
        return { 
            field, 
            searchOperator: 'notBetween', 
            value: value1,
            value2: value2,
            entityField: field 
        };
    }
};
