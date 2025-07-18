# Javadoc Extractor

멀티 모듈 프로젝트에서 Javadoc을 추출하여 Excel 파일로 내보내는 도구입니다.

## 기능

- JavaParser를 사용하여 Java 소스 코드에서 Javadoc 주석 추출
- 클래스, 메서드, 필드의 Javadoc 정보 추출
- 추출된 정보를 Excel 파일로 내보내기
- 멀티 모듈 프로젝트 지원

## 사용 방법

### Gradle 태스크 실행

프로젝트 루트 디렉토리에서 다음 명령어를 실행하여 모든 모듈의 Javadoc을 추출하고 Excel 파일로 내보낼 수 있습니다:

```bash
./gradlew :javadoc-extractor:extractJavadoc
```

이 명령은 프로젝트 루트 디렉토리에 `javadoc-output.xlsx` 파일을 생성합니다.

### 직접 실행

또는 다음과 같이 직접 실행할 수도 있습니다:

```bash
./gradlew :javadoc-extractor:run --args="<프로젝트_루트_경로> <출력_엑셀_파일_경로>"
```

## 출력 결과

생성된 Excel 파일은 다음과 같은 세 개의 시트로 구성됩니다:

1. **Classes** - 모든 클래스 정보
   - 패키지, 클래스 이름, 타입(클래스/인터페이스/열거형/어노테이션), 접근 제어자, 설명, 태그

2. **Methods** - 모든 메서드 정보
   - 클래스, 메서드 이름, 반환 타입, 매개변수, 접근 제어자, 설명, 반환값 설명, 태그

3. **Fields** - 모든 필드 정보
   - 클래스, 필드 이름, 타입, 접근 제어자, 설명, 태그

## 구현 세부 사항

1. **JavadocDoclet**: JavaParser를 사용하여 Javadoc 정보를 추출하는 클래스
2. **JavadocExcelExporter**: Apache POI를 사용하여 추출된 정보를 Excel로 내보내는 클래스
3. **MultiModuleJavadocExtractor**: 멀티 모듈 프로젝트에서 모든 모듈의 Java 소스 파일을 찾는 클래스

## 확장 가능성

이 도구는 다음과 같이 확장할 수 있습니다:

1. **필터링 기능 추가**: 특정 패키지나 클래스만 추출하는 기능
2. **다양한 출력 형식 지원**: Excel 외에도 HTML, Markdown 등 다양한 형식으로 출력
3. **커스텀 태그 처리**: 프로젝트에서 사용하는 커스텀 Javadoc 태그 처리 기능 추가

## 의존성

- JavaParser: Java 소스 코드 파싱
- Apache POI: Excel 파일 생성
- Lombok: 보일러플레이트 코드 감소