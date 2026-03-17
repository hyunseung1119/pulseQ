# PulseQ 개발 계획 (2026-03-17 ~ 2026-03-22)

> 1주 1프로젝트 목표: 면접/포트폴리오용 PoC 수준 완성 + 4파트 블로그 시리즈

## 완료된 Phase (3/16 완료)

| Phase | 내용 | 상태 |
|-------|------|------|
| 1 | 멀티모듈 + 테넌트/이벤트 API | Done |
| 2 | Redis Sorted Set 대기열 엔진 | Done |
| 3 | Kafka 이벤트 스트리밍 | Done |
| 4 | LightGBM 봇 탐지 | Done |
| 5 | React 실시간 대시보드 | Done |
| - | 분산 락 경합 버그 수정 (506% 개선) | Done |
| - | 부하 테스트 스크립트 + 성능 측정 | Done |

## 성능 기준선 (3/16 측정)

| 지표 | 수치 |
|------|------|
| POST enter 처리량 | 66.0 req/s |
| POST enter p50 | 587ms |
| POST enter p99 | 1,330ms |
| GET status 처리량 | 108.5 req/s |
| GET status p50 | 102ms |
| Bot enter 처리량 | 76.9 req/s |

---

## Day 2 (3/17 월): 테스트 + 고도화

### 오전: 테스트 코드 작성
- [ ] QueueService 단위 테스트 (enter, processQueue, verifyToken)
  - 정상 입장, 중복 입장 거부, 정원 초과, 봇 차단
- [ ] EventService 단위 테스트 (CRUD + 상태 전이)
- [ ] TenantService 단위 테스트 (가입, 로그인, API키)
- [ ] 목표: 커버리지 80%+

### 오후: API 고도화
- [ ] Rate Limiting 구현 (Redis 기반, 테넌트별 + 엔드포인트별)
- [ ] 이벤트 카운터 원자적 업데이트 (DB 레벨 INCREMENT)
  - 현재: read → modify → write (lost update 가능)
  - 개선: `UPDATE events SET total_entered = total_entered + 1 WHERE id = ?`
- [ ] 입장 토큰 만료 시 자동 대기열 복귀 로직

### 블로그: Part 2 초안
- WebSocket 지수 백오프 구현 + XSS 방지
- TanStack Query/Router 설계 결정
- Zustand vs Context 선택 근거

---

## Day 3 (3/18 화): Observability

### Prometheus + Grafana 구축
- [ ] Micrometer 연동 (Spring Boot Actuator)
- [ ] 커스텀 메트릭 정의:
  - `pulseq_queue_enter_total` (Counter)
  - `pulseq_queue_enter_latency` (Histogram)
  - `pulseq_queue_size` (Gauge)
  - `pulseq_bot_blocked_total` (Counter)
  - `pulseq_kafka_lag` (Gauge)
- [ ] Grafana 대시보드 3개:
  1. API 성능 (처리량, 레이턴시, 에러율)
  2. 대기열 상태 (크기, 처리 속도, 봇 차단)
  3. 인프라 (Redis 메모리, Kafka lag, DB 커넥션)
- [ ] Alert 규칙 설정 (에러율 > 5%, p99 > 3s)

### docker-compose 업데이트
- [ ] Prometheus + Grafana 서비스 추가
- [ ] 네트워크 정리 (monitoring 프로파일)

---

## Day 4 (3/19 수): 성능 최적화 + 2차 부하 테스트

### 최적화 대상
- [ ] 이벤트 카운터 원자적 업데이트 적용 확인
- [ ] Redis 파이프라인 배치 처리 (enqueue + getPosition 합치기)
- [ ] DB 커넥션 풀 튜닝 (HikariCP)
- [ ] Kafka 프로듀서 배치 설정 최적화

### 2차 부하 테스트
- [ ] 동시 100, 요청 1000으로 스케일업
- [ ] 장시간 테스트 (5분 지속 부하)
- [ ] 메모리 누수 확인
- [ ] Grafana 대시보드로 실시간 모니터링하며 테스트

### 블로그: Part 3 초안
- 분산 락 경합 디버깅 스토리 (Before/After 수치)
- Redis Sorted Set vs 대안 비교
- 부하 테스트 방법론과 결과 분석

---

## Day 5 (3/20 목): 인프라 + 배포

### AWS 배포 준비
- [ ] Dockerfile 프로덕션 최적화 (멀티스테이지, 비-root)
- [ ] docker-compose.prod.yml 분리
- [ ] 환경변수 분리 (.env.production)
- [ ] Health check 엔드포인트 강화
- [ ] GitHub Actions CI/CD 파이프라인 작성

### 배포 (옵션: AWS or Railway)
- [ ] EC2 + Docker Compose 또는 ECS Fargate
- [ ] RDS PostgreSQL + ElastiCache Redis
- [ ] ALB + SSL 인증서

---

## Day 6 (3/21 금): 문서화 + 마무리

### API 문서
- [ ] Swagger/OpenAPI 자동 생성 (springdoc-openapi)
- [ ] Postman Collection 내보내기

### 프로젝트 정리
- [ ] README.md 최종 업데이트 (배포 URL, 데모 영상)
- [ ] ARCHITECTURE.md 업데이트 (최종 ADR)
- [ ] 코드 품질 최종 점검

### 블로그: Part 4 완성
- 전체 회고 + 아키텍처 결정 기록
- 면접 예상 질문 & 답변 포함
- 수치적 성과 종합 정리

---

## Day 7 (3/22 일): 블로그 최종 편집 + 발행

### 블로그 시리즈 최종 구성

| Part | 제목 (안) | 유형 | 핵심 내용 |
|------|----------|------|----------|
| **1** | 선착순 대기열 SaaS 설계하기 | 회고/ADR | 아키텍처, 기술 선택, 삽질 기록, 락 버그 |
| **2** | React + WebSocket 실시간 대시보드 | 기술 심화 | TanStack, WS 재접속, XSS 방지 |
| **3** | 분산 락 경합 디버깅과 성능 최적화 | 트러블슈팅 | 10.9→66 req/s, 테스트 방법론 |
| **4** | 1주 1프로젝트 회고 + 면접 대비 | 회고 | ADR, 트레이드오프, 면접 Q&A |

---

## 면접 대비: 예상 질문 목록

### 아키텍처
- Q: "왜 헥사고날 아키텍처를 선택했나?"
- Q: "멀티모듈을 나눈 기준은?"
- Q: "WebFlux를 쓴 이유와 트레이드오프는?"

### 대기열
- Q: "Redis Sorted Set 대신 다른 방법은 없었나?"
- Q: "분산 락의 범위를 어떻게 결정했나?"
- Q: "정원 초과 체크에 race condition은 없나?"

### 성능
- Q: "처리량을 506% 개선한 과정을 설명해주세요"
- Q: "부하 테스트는 어떻게 설계했나?"
- Q: "수평 확장 시 어떤 문제가 생길 수 있나?"

### 봇 탐지
- Q: "Rule 기반 대신 ML을 쓴 이유는?"
- Q: "False positive를 어떻게 처리하나?"
- Q: "ML 모델의 학습 데이터는 어떻게 구성했나?"

### 프론트엔드
- Q: "TanStack Query를 선택한 이유는?"
- Q: "WebSocket 재접속 전략을 설명해주세요"
- Q: "XSS 방어를 어떻게 했나?"
