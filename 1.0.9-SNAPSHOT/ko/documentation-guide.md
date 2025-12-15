# Documentation Guide

SimpliX 문서 시스템에 대한 가이드입니다. 문서 빌드, 로컬 미리보기, 기여 방법을 설명합니다.

## 문서 구조

SimpliX 문서는 각 모듈에 분산되어 있으며, 빌드 시 통합됩니다.

```
simplix/
├── docs/
│   ├── index.html          # Docsify 설정
│   ├── _coverpage.md       # 커버 페이지
│   ├── _navbar.md          # 상단 네비게이션
│   └── ko/
│       ├── _sidebar.md     # 사이드바 네비게이션
│       ├── README.md       # 메인 페이지
│       ├── quick-start.md  # 튜토리얼
│       └── ...
├── simplix-core/docs/ko/   # Core 모듈 문서
├── simplix-auth/docs/ko/   # Auth 모듈 문서
├── simplix-cache/docs/ko/  # Cache 모듈 문서
└── ...                     # 기타 모듈 문서
```

## 로컬 빌드 및 미리보기

### 1. 빌드 스크립트 실행

```bash
# 프로젝트 루트에서 실행
./scripts/build-docs.sh
```

빌드 스크립트는 다음 작업을 수행합니다:
- 모든 모듈의 문서를 `build-docs/` 폴더로 복사
- 모듈 간 상대 경로 링크를 Docsify 절대 경로로 변환
- Docsify 설정 파일 복사

### 2. 로컬 서버 실행

**방법 1: Python 간이 서버**

```bash
cd build-docs
python -m http.server 3000
```

**방법 2: docsify-cli (Node.js 필요)**

```bash
npm install -g docsify-cli
docsify serve build-docs
```

### 3. 브라우저에서 확인

```
http://localhost:3000
```

## 문서 작성 가이드

### 새 문서 추가

1. 해당 모듈의 `docs/ko/` 폴더에 마크다운 파일 생성
2. `docs/ko/_sidebar.md`에 네비게이션 링크 추가

```markdown
* **모듈명**
  * [새 문서](ko/module/new-doc.md)
```

### 모듈 간 링크

다른 모듈의 문서를 참조할 때는 상대 경로를 사용합니다:

```markdown
<!-- simplix-auth/docs/ko/getting-started.md 에서 -->
자세한 내용은 [Core 모듈](ko/core/overview.md)을 참조하세요.
```

빌드 시 자동으로 Docsify 경로(`/ko/core/overview.md`)로 변환됩니다.

### 지원되는 마크다운 기능

**코드 블록 (구문 강조)**

```java
@Service
public class UserService {
    // Java 코드
}
```

지원 언어: `java`, `kotlin`, `yaml`, `bash`, `groovy`, `json`, `properties`, `sql`

**표**

| 열1 | 열2 |
|-----|-----|
| 값1 | 값2 |

**알림 박스**

```markdown
> [!TIP]
> 유용한 팁 내용

> [!WARNING]
> 주의사항 내용

> [!IMPORTANT]
> 중요한 내용
```

## GitHub Pages 배포

### 자동 배포

`main` 브랜치에 문서 변경사항이 push되면 GitHub Actions가 자동으로 배포합니다.

트리거 조건:
- `docs/**` 파일 변경
- `**/docs/ko/**` 파일 변경
- `scripts/build-docs.sh` 변경

### 수동 배포

GitHub Actions 탭에서 "Deploy Documentation" 워크플로우를 수동 실행할 수 있습니다.

### 배포 URL

```
https://simplecore-inc.github.io/simplix/
```

## 문서 기여

### 오타/오류 수정

1. 해당 문서 페이지에서 "GitHub에서 편집" 링크 클릭
2. GitHub에서 직접 수정 후 PR 생성

### 새 문서 추가

1. 저장소 fork
2. 해당 모듈의 `docs/ko/` 폴더에 문서 작성
3. `docs/ko/_sidebar.md`에 링크 추가
4. 로컬에서 빌드 및 확인
5. PR 생성

## 트러블슈팅

### 빌드 스크립트 실행 오류

```bash
# 실행 권한 부여
chmod +x scripts/build-docs.sh
```

### 링크가 작동하지 않음

- 빌드 후 `build-docs/` 폴더에서 확인해야 합니다
- 원본 `docs/` 폴더에서는 모듈 간 링크가 작동하지 않습니다

### 검색이 작동하지 않음

- 검색 인덱스는 페이지 첫 로드 시 생성됩니다
- 캐시를 비우고 새로고침해보세요
