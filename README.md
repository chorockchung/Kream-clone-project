![CI/CD](https://github.com/chorockchung/Kream-clone-project/actions/workflows/ci.yml/badge.svg)


# Kream Clone — 입찰-체결 시스템

중고 거래 플랫폼 'Kream'의 입찰-체결 시스템을 구현한 프로젝트입니다. 단순 CRUD를 넘어, **동시성 문제 해결**, **성능 최적화**, **CI/CD 파이프라인 구축 및 실제 배포**까지 진행했습니다.

![CI/CD](https://github.com/chorockchung/{저장소명}/actions/workflows/ci.yml/badge.svg)

## 데모

- Swagger UI: `http://3.35.3.95:8080/swagger-ui/index.html`
- Health Check: `http://3.35.3.95:8080/actuator/health`

> ⚠️ 비용 관리를 위해 인스턴스를 상시 가동하지 않습니다. 시연이 필요하시면 언제든 요청해주세요.

## 프로젝트 목표

- Docker 기반으로 MySQL, Redis 인프라 구성
- Redis 분산 락으로 동시성 문제 해결
- 캐싱 및 인덱스를 통한 조회 성능 최적화 (수치로 검증)
- GitHub Actions CI/CD 파이프라인 구축 및 AWS EC2 실배포

## 기술 스택

`Java 17` `Spring Boot 3.2.5` `Spring Data JPA` `MySQL 8.0` `Redis / Redisson` `Docker & Docker Compose` `JUnit5` `Testcontainers` `GitHub Actions` `GHCR` `AWS EC2`

## 아키텍처

```
Controller → Service → Repository → Domain
                ↓
              DTO
```

### 배포 아키텍처

```
개발자 (master로 push)
        │
        ▼
┌─────────────────────────────────────────────┐
│              GitHub Actions                   │
│                                                │
│  [test]                                       │
│   - GitHub Actions services로 MySQL/Redis 기동 │
│   - mvn package (테스트 + jar 빌드)            │
│   - 테스트 실패 시 여기서 파이프라인 중단        │
│        │ (성공 시에만)                         │
│        ▼                                      │
│  [build-and-push]                             │
│   - Docker 이미지 빌드 (buildx)                │
│   - GHCR에 이미지 push                         │
│        │ (master push 시에만)                  │
│        ▼                                      │
│  [deploy]                                     │
│   - SSH로 EC2 접속                             │
│   - 최신 이미지 pull → 컨테이너 재기동          │
│   - 이미지/빌드 캐시 자동 정리                  │
└─────────────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────────────┐
│         AWS EC2 (Docker Compose)              │
│   [kream-app] [kream-mysql] [kream-redis]     │
└─────────────────────────────────────────────┘
        │
        ▼
   실제 서비스 접속 (Swagger UI로 API 검증)
```

---

## 핵심 기능

### 1. 입찰-체결(매칭) 시스템

- 구매 입찰(BUY_BID) / 판매 입찰(SELL_BID)과, 즉시 체결을 원하는 BUY_INSTANT / SELL_INSTANT 두 가지 흐름 지원
- 조건에 맞는 상대 입찰을 찾아 자동으로 매칭하고 Trade(체결) 레코드 생성

### 2. Redisson 분산 락으로 동시성 문제 해결

거래가 몰리는 순간(다수의 판매자가 동일한 구매 입찰 하나를 동시에 노리는 상황)을 재현하여, Redisson 기반 분산 락으로 중복 체결을 방지했습니다.

- 락 그레인(lock granularity)을 `productSize` 단위가 아닌 **개별 `Bid` 단위**로 설계하여, 서로 다른 입찰을 대상으로 하는 요청은 병렬로 처리되도록 함
- `tryLock` 획득 → 트랜잭션 경계 안에서 재조회·상태 검증·상태 변경·저장을 한 번에 커밋 → 락 해제 순서를 명시적으로 제어

**동시성 테스트 결과** (10명이 동일한 입찰 1건에 대해 동시 요청)

| 시나리오 | 성공 | 실패(이미 매칭됨) | 생성된 Trade | 상태 변경 |
|---|---|---|---|---|
| 판매자 10명 → 구매 입찰 1건 | 1건 | 9건 | 1건 | MATCHED 1회만 |
| 구매자 10명 → 판매 입찰 1건 | 1건 | 9건 | 1건 | MATCHED 1회만 |

### 3. Redis 캐싱으로 조회 성능 개선

상품 사이즈별 현재가(최근 체결가) 조회는 read가 압도적으로 많고 write는 매칭 성공 시점에만 발생하는 read-heavy 데이터로 판단하여, **Cache-Aside + Write-Through** 전략을 적용했습니다.

- 조회(Cache-Aside): 캐시 먼저 확인 → 없으면 DB 조회 후 캐시에 채움
- 갱신(Write-Through): 매칭 성공(`matchBid`) 시점에 Trade 저장과 동시에 캐시도 갱신하여 정합성 보장

**더미 데이터 22,000+건 기준 성능 비교**

| 조건 | DB 조회 평균 | 캐시 조회 평균 | 개선율 |
|---|---|---|---|
| 단일 컬럼 인덱스만 | 7.983 ms | 0.447 ms | **17.9배** |
| 복합 인덱스 적용 | 5.799 ms | 0.512 ms | **11.3배** |

### 4. 인덱스 설계를 통한 DB 레벨 최적화

`Trade` 테이블에서 "특정 상품 사이즈의 가장 최근 체결가"를 조회하는 쿼리를 `EXPLAIN`으로 분석하여 개선했습니다.

- **적용 전**: `product_size_id` 단일 컬럼 인덱스만 존재 → `type: ref`(필터링은 인덱스 활용)이지만 `Using filesort`(정렬은 별도 수행) 발생
- **원인**: 단일 인덱스는 `product_size_id`로 그룹 짓는 것만 보장할 뿐, 그룹 내부의 `traded_at` 순서는 보장하지 않음
- **개선**: `(product_size_id, traded_at DESC)` 복합 인덱스로 변경 → 인덱스 자체가 이미 정렬된 상태를 가지도록 하여 `filesort` 제거

```sql
CREATE INDEX idx_trade_productsize_tradedat
ON trade (product_size_id, traded_at DESC);
```

인덱스 적용 후 DB 평균 조회 시간이 **7.98ms → 5.80ms로 약 27% 개선**되었습니다.

### 5. GitHub Actions CI/CD 파이프라인

**CI — 테스트 자동화**

- push(master, develop) 및 PR(master 대상) 시 자동으로 테스트 실행
- GitHub Actions `services` 기능으로 MySQL·Redis를 매번 새로 기동해, 로컬과 동일한 조건에서 검증
- `needs`로 job 간 의존관계를 걸어, 테스트 실패 시 이후 빌드·배포가 아예 실행되지 않도록 구조적으로 차단

테스트의 성격이 다르다는 점을 인지하고 CI 실행 대상을 분리했습니다.

| 테스트 | 성격 | CI 포함 여부 |
|---|---|---|
| `BidServiceConcurrencyTest` | 동시성 정확성 검증 (회귀 테스트) | 포함 |
| `ProductPriceCachePerformanceTest` | 캐시 성능 벤치마크 | `@Tag("performance")`로 제외 |
| `DummyDataInitializerTest` | 데이터 시딩 도구 | `@Tag("seeding")`으로 제외 |

**CD — 빌드 및 배포 자동화**

- 테스트를 통과한 jar로 Docker 이미지 빌드 (`docker/setup-buildx-action` + `docker/build-push-action`)
- GHCR(GitHub Container Registry)에 이미지 push
- `appleboy/ssh-action`으로 EC2에 SSH 접속 → 최신 이미지 pull → `docker compose up -d`로 재배포
- 배포 직후 사용하지 않는 이미지·빌드 캐시를 자동 정리해, 반복 배포로 인한 서버 디스크 고갈 예방

### 6. AWS EC2 실배포

- EC2(t2.micro, Ubuntu 22.04)에 Docker/Docker Compose 설치, 보안 그룹으로 필요한 포트만 개방
- 앱·MySQL·Redis를 한 번에 기동하는 배포용 `docker-compose.yml` 구성 (컨테이너 간 서비스 이름으로 상호 참조)
- 비밀번호 등 민감 정보는 `.env`로 분리, `.gitignore`로 저장소 노출 방지
- GitHub Secrets로 `EC2_HOST`, `EC2_SSH_KEY`, `GHCR_TOKEN`, `MYSQL_ROOT_PASSWORD` 등 관리
- 리소스 제약 환경(RAM 1GB) 대응: 스왑 파일 구성, 배포 스크립트에 자동 리소스 정리 단계 포함

---

## 트러블슈팅

### 1. 락을 걸었는데도 전원이 체결되는 동시성 문제

**증상**: Redisson 락을 도입했음에도, 동시 요청 테스트에서 10명 전원이 동일한 입찰에 체결되는 현상 발생.

**원인**
1. 락 도입 전 사용하던 `@Transactional`을 서비스 코드에서 제거하면서, 트랜잭션 경계 없이 `save()`만 호출하는 구조가 되어 변경 사항이 제대로 반영되지 않고 있었음
2. 매칭 로직에 실제 상태 변경(`match()`) 호출이 빠져 있어, 락 안에서 재조회를 해도 여전히 `WAITING`으로 보여 재검증 로직을 계속 통과 — **락 자체는 걸렸지만, 락이 지켜야 할 "상태 변경"이 없어 무력화된 상태**
3. "즉시 체결" 요청을 받는 즉시 `BidStatus.MATCHED`로 저장해버리는 로직 때문에, 실제 매칭 성공 여부와 무관하게 상태가 먼저 확정되는 논리적 오류도 존재

**해결**: `TransactionTemplate`을 사용해 락 블록 안에서 트랜잭션 경계를 명시적으로 제어.

```java
// 락 획득 → 트랜잭션 시작 → 재조회 + 상태 검증 + 상태 변경 + 저장 → 커밋 → 락 해제
transactionTemplate.execute(status -> {
    Bid buyBid = bidRepository.findById(candidate.getId())
            .orElseThrow(EntityNotFoundException::new);

    if (buyBid.getBidStatus() != BidStatus.WAITING) {
        throw new IllegalStateException("이미 매칭된 입찰입니다.");
    }

    buyBid.match();
    sellBid.match();
    bidRepository.save(buyBid);
    bidRepository.save(sellBid);

    matchBid(buyBid, sellBid);
    return null;
});
```

`@Transactional`(AOP 프록시 기반)은 트랜잭션 시작/종료 시점을 메서드 진입/종료에 고정할 수밖에 없어, "락 획득 → 트랜잭션 → 커밋 → 락 해제"라는 순서를 세밀하게 제어하기 어렵다. `TransactionTemplate`으로 트랜잭션 범위를 락 블록 안에 명시적으로 감싸, 다른 스레드가 락을 얻었을 때는 항상 이전 스레드의 커밋이 완전히 끝난 상태를 보장하도록 설계했다.

### 2. Testcontainers와 최신 Docker Engine 간 API 버전 호환성 문제

**증상**: Testcontainers로 격리된 Redis 컨테이너를 띄우려 했으나, 다음 에러로 테스트가 실행되지 않음.

```
Could not find a valid Docker environment. Please check configuration. Attempted configurations were:
NpipeSocketClientProviderStrategy: failed with exception BadRequestException (Status 400: ...)
```

로컬 Docker Desktop은 정상 실행 중이었고 `docker ps`도 문제없었지만, Testcontainers만 유독 Docker 데몬을 찾지 못했다.

**원인 분석**
1. Named Pipe와 TCP 연결을 모두 시도했으나 동일한 패턴의 400 에러 발생 → 전송 계층 문제가 아님을 확인
2. 에러 응답 바디의 `ServerVersion`, `KernelVersion` 등 대부분 필드가 빈 값 → 통신 자체는 성립했지만 **클라이언트가 응답을 파싱하는 과정에서 실패**하고 있다는 결론
3. 근본 원인: Docker Desktop이 최신 버전(Docker Engine 29.x, API 1.55)으로 업데이트되어 있었는데, Testcontainers 1.21.3의 `docker-java` 클라이언트가 이 API 버전의 응답 스키마를 아직 지원하지 못해 파싱에 실패

**해결 과정**
1. Testcontainers 최신 버전으로 업그레이드했으나 완전히 해결되지 않음
2. `DOCKER_API_VERSION` 환경변수로 우회 시도
3. 최종적으로는 **여러 우회 설정(TCP 노출, 커스텀 API 버전 지정 등)을 모두 원상복구하고 표준 구성(npipe 기본값)으로 되돌린 것**이 실질적인 해결책
4. 병행하여, Windows에 별도 설치되어 있던 `MySQL80` 서비스가 Docker 컨테이너와 동일한 3306 포트를 점유하고 있던 문제도 함께 발견 및 해결

**배운 점**
- 에러 메시지를 그대로 믿기보다 응답 구조를 직접 뜯어봐야 진짜 원인이 드러날 때가 있다
- 서로 다른 연결 방식에서 동일한 실패 패턴이 나온다는 사실이, 문제가 연결 자체가 아니라 그 이후 단계에 있다는 강력한 단서가 되었다
- 자동 업데이트되는 인프라 도구와 업데이트 주기가 느린 라이브러리 사이의 API 버전 격차는 실무 CI 환경 구성에서도 흔히 마주치는 문제다
- 여러 우회책을 동시에 쌓기보다, 설정을 하나씩 표준 상태로 되돌리며 원인을 격리하는 접근이 더 효과적일 때가 있다

### 3. `mvn test`와 `mvn package`의 차이로 배포 아티팩트 누락

**증상**: CI에서 jar 아티팩트를 다운로드하려는데 `Artifact not found for name: app-jar` 에러.

**원인**: `test` job에서 `mvn test`를 실행하고 있었는데, Maven 라이프사이클상 `test`는 `package`보다 앞선 단계라 `target/*.jar`가 생성되지 않았다.

**해결**: `mvn test` → `mvn package`로 변경 (`package`는 `test`를 포함하는 상위 라이프사이클).

### 4. `no main manifest attribute` — 실행 불가능한 jar

**증상**: 이미지는 정상 빌드·배포됐지만 컨테이너가 즉시 종료.

**원인**: `pom.xml`에 `spring-boot-maven-plugin`이 등록되어 있지 않아, `mvn package`가 만드는 jar에 `Main-Class` 매니페스트 정보가 없었다.

**해결**:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

### 5. Docker Buildx 캐시 드라이버 미지원

**증상**: `ERROR: failed to build: Cache export is not supported for the docker driver.`

**원인**: `cache-to: type=gha`를 쓰려 했으나, 별도 설정 없이는 기본 `docker` 드라이버가 선택되어 이 캐시 방식을 지원하지 않았다.

**해결**: `docker/build-push-action` 실행 전에 `docker/setup-buildx-action`을 추가해 `docker-container` 드라이버로 전환.

### 6. SSH 배포 시 연결 타임아웃

**증상**: `dial tcp ***:22: i/o timeout`

**원인 분석**: 로컬에서는 같은 키로 접속이 잘 됐지만 CI에서만 실패 → 인증 문제가 아니라 네트워크 차단. EC2 보안 그룹의 SSH(22번) 인바운드 규칙이 "내 IP"로만 좁게 설정되어 있어, 매번 동적으로 바뀌는 GitHub Actions 러너의 IP가 차단되고 있었다.

**해결**: 보안 그룹의 22번 포트 소스를 `0.0.0.0/0`으로 변경 (실제 로그인은 `.pem` 키가 있어야만 가능하므로 이 방식을 채택).

### 7. `t3.micro` 리소스 한계 — 메모리 및 디스크 부족

**증상**: 컨테이너가 뜬 뒤 서버 응답이 급격히 느려지거나, 재배포 시 `no space left on device` 발생.

**원인**
- **메모리**: RAM 1GB에 앱+MySQL+Redis 세 컨테이너를 동시 구동하면서 가용 메모리가 거의 0에 수렴, 스왑도 없어 여유가 전혀 없었음
- **디스크**: 여러 차례 재배포로 Docker 이미지 레이어와 빌드 캐시가 누적되며 8GB 볼륨이 100% 소진

**해결**
- 스왑 파일 추가 및 메모리 크기 8GB -> 32GB로 올림

- `docker system prune -a --volumes`로 정리

**배운 점**: 프리티어 인스턴스로 멀티 컨테이너 서비스를 운영할 때는, 기능 구현만큼이나 리소스 관리 자동화가 안정적인 운영에 실질적으로 더 중요하다는 것을 체감했다.

### 8. 벤치마크/시딩용 테스트가 CI를 계속 실패시킴

**증상**: 로컬에서는 통과하던 `ProductPriceCachePerformanceTest`, `DummyDataInitializerTest`가 CI에서만 `NoSuchElementException`으로 실패.

**원인**: 두 테스트 모두 "사전에 대량 시딩된 데이터가 있다"는 전제로 작성되어 있었는데, CI는 매번 완전히 빈 MySQL 컨테이너로 새로 시작해 전제 자체가 성립하지 않았다.

**해결**: 두 테스트를 "회귀 테스트"가 아닌 "성능 벤치마크"/"데이터 시딩 도구"로 성격을 구분하고, `@Tag`로 분리해 CI에서만 제외.

```java
@Tag("performance")   // 또는 @Tag("seeding")
```

```xml
<excludedGroups>performance,seeding</excludedGroups>
```

### 9. `main`이 `test` 코드를 참조하려는 구조적 오류

**증상**: 배포 환경에서도 필요할 때 더미데이터를 시딩할 수 있도록 `Main.java`에 `@Profile("seed")`로 `DummyDataInitializer`를 등록하려 했는데, import 자체가 인식되지 않음.

**원인**: `DummyDataInitializer`가 `src/test/java`에 있었다. `test` 코드는 `main` 코드를 참조할 수 있지만, 반대로 `main`이 `test`를 참조하는 것은 허용되지 않는다. `test` 산출물은 운영용 빌드(jar)에 포함되지 않기 때문에 컴파일 단계에서부터 막힌다.

**해결**: `DummyDataInitializer`(로직 클래스)를 `src/main/java`로 이동하고, 이를 검증/실행하는 `DummyDataInitializerTest`만 `src/test/java`에 남겨두었다.

**배운 점**: main/test 소스 루트 분리 원칙은 단순 컨벤션이 아니라, "운영 환경에 실제로 필요한 코드"와 "검증에만 필요한 코드"를 명확히 구분해 배포 산출물의 무결성을 지키기 위한 구조적 장치라는 것을 실제 배포 과정에서 체감했다.

---

## 종합적으로 배운 점

- CI/CD는 "돌아가게 만드는 것"과 "안정적으로 계속 돌아가게 만드는 것" 사이에 생각보다 큰 간극이 있다. 특히 리소스가 제한된 환경에서는 기능 구현만큼 운영 자동화가 중요했다.
- 테스트를 "전부 통과시켜야 하는 대상"으로만 보지 않고, 목적(회귀 검증 / 벤치마크 / 데이터 시딩)에 따라 CI 파이프라인에 다르게 편입시키는 판단이 필요했다.
- 로컬 개발 환경과 CI 환경은 "매번 새로 시작하는가"라는 근본적인 차이가 있고, 이 차이를 전제하지 않은 테스트는 로컬에서만 통과하는 착시를 만들 수 있다.
- 인덱스와 캐싱처럼 성격이 다른 최적화 기법을 실측 수치로 비교하며, "무조건 캐싱"이 아니라 상황에 맞는 선택을 하는 판단력을 기를 수 있었다.

## 테스트

```
- BidServiceConcurrencyTest: 동시 입찰 시 락을 통한 중복 체결 방지 검증
- ProductPriceCachePerformanceTest: 캐시 적용 전후 조회 성능 비교 (로컬 전용, CI 제외)
- DummyDataInitializerTest: 더미 데이터 시딩 (로컬/필요시 전용, CI 제외)
```

