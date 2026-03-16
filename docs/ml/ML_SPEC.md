# PulseQ — ML 모델 명세서

## 1. 봇 탐지 모델 (Bot Detection)

### 목표
매크로/봇 사용자와 실제 사용자를 구분하여 공정한 대기열 운영

### 모델 선택

| 모델 | 검토 결과 | 선택 여부 |
|------|----------|----------|
| **LightGBM** | 빠른 추론 (<5ms), 피처 중요도 해석 가능, 적은 데이터로 학습 가능 | ✅ **선택** |
| Random Forest | LightGBM 대비 추론 느림 | ❌ |
| Neural Network | 해석 불가, 적은 데이터에서 과적합 | ❌ |
| Rule-based | 우회 쉬움, 유지보수 어려움 | 초기 fallback용 |

**선택 근거**: 사이드 프로젝트 규모에서 LightGBM은 학습 데이터 10만 건으로도 90%+ 정확도 달성 가능하며, 추론 속도가 sub-5ms로 실시간 서비스에 적합. 피처 중요도 확인으로 디버깅/개선 용이.

### 피처 설계

#### 요청 패턴 피처 (실시간)
| 피처 | 설명 | 타입 | 범위 |
|------|------|------|------|
| `click_interval_mean` | 최근 10회 요청 간격 평균 (ms) | float | 0-∞ |
| `click_interval_std` | 최근 10회 요청 간격 표준편차 | float | 0-∞ |
| `click_interval_min` | 최소 요청 간격 | float | 0-∞ |
| `request_count_1m` | 최근 1분간 요청 수 | int | 0-∞ |
| `request_count_5m` | 최근 5분간 요청 수 | int | 0-∞ |
| `exact_interval_ratio` | 정확히 동일한 간격 비율 | float | 0-1 |

#### 행동 피처
| 피처 | 설명 | 타입 | 범위 |
|------|------|------|------|
| `time_before_event` | 이벤트 시작 전 접속 시간 (초) | float | 0-∞ |
| `page_dwell_time` | 페이지 체류 시간 | float | 0-∞ |
| `mouse_movement_entropy` | 마우스 움직임 엔트로피 (JS 수집) | float | 0-∞ |
| `scroll_events` | 스크롤 이벤트 수 | int | 0-∞ |
| `has_cookie` | 쿠키 존재 여부 | bool | 0/1 |

#### 네트워크 피처
| 피처 | 설명 | 타입 | 범위 |
|------|------|------|------|
| `ip_request_count` | 동일 IP에서의 총 요청 수 | int | 0-∞ |
| `ip_user_count` | 동일 IP에서의 고유 사용자 수 | int | 0-∞ |
| `is_datacenter_ip` | 데이터센터 IP 여부 | bool | 0/1 |
| `is_vpn_tor` | VPN/Tor 여부 | bool | 0/1 |
| `ua_is_headless` | 헤드리스 브라우저 여부 | bool | 0/1 |
| `fingerprint_collision` | 동일 fingerprint 다중 요청 | int | 0-∞ |

### 학습 데이터 생성 (시뮬레이션)

실제 봇 데이터가 없으므로 **시뮬레이션으로 학습 데이터 생성**:

```python
# 실제 사용자 시뮬레이션
normal_user = {
    "click_interval_mean": random.gauss(2000, 800),      # 평균 2초, 편차 큼
    "click_interval_std": random.uniform(300, 2000),      # 불규칙
    "exact_interval_ratio": random.uniform(0, 0.1),       # 정확히 같은 간격 거의 없음
    "mouse_movement_entropy": random.uniform(3.0, 8.0),   # 자연스러운 마우스
    "ip_user_count": 1,                                    # IP당 1명
}

# 봇 시뮬레이션
bot_user = {
    "click_interval_mean": random.gauss(100, 20),          # 평균 0.1초, 매우 빠름
    "click_interval_std": random.uniform(0, 10),           # 매우 규칙적
    "exact_interval_ratio": random.uniform(0.7, 1.0),      # 정확히 같은 간격 많음
    "mouse_movement_entropy": random.uniform(0, 0.5),      # 마우스 움직임 없음
    "ip_user_count": random.randint(5, 50),                # IP당 다수 사용자
}
```

### 학습 데이터 규모 (로컬 PC 적합)

| 항목 | 규모 | 메모리 | 학습 시간 |
|------|------|--------|----------|
| 정상 사용자 | 80,000건 | ~50MB | — |
| 봇 사용자 | 20,000건 | ~12MB | — |
| 총 학습 데이터 | 100,000건 | ~62MB | **< 30초** |
| 검증 데이터 | 20,000건 | ~12MB | — |

### 모델 평가 기준

| 지표 | 목표 | 사유 |
|------|------|------|
| **Precision** | > 85% | 오탐 (실사용자 차단) 최소화 — 가장 중요 |
| **Recall** | > 90% | 봇 탈루 최소화 |
| **F1 Score** | > 87% | 균형 |
| **추론 시간** | < 5ms | 실시간 서비스 요건 |
| **모델 크기** | < 10MB | 메모리 효율 |

### 서빙 아키텍처

```
┌──────────────────────────────────────────────────┐
│                 봇 탐지 흐름                       │
│                                                    │
│  1. 요청 도착                                       │
│     │                                               │
│  2. Redis 캐시 확인 (bot_score:{eventId}:{userId})  │
│     ├─ 캐시 히트 → 스코어 반환 (sub-ms)              │
│     │                                               │
│     └─ 캐시 미스 →                                   │
│        3. Kafka로 행동 데이터 발행 (비동기)            │
│           │                                          │
│        4. ML Consumer가 소비                         │
│           │                                          │
│        5. LightGBM 추론 (~3ms)                       │
│           │                                          │
│        6. 결과를 Redis에 캐싱 (TTL 10분)              │
│           │                                          │
│        7. 스코어 > 임계치 → 차단 이벤트 발행           │
│                                                      │
│  ※ 첫 요청은 봇 스코어 없이 통과 (Cold Start)          │
│  ※ 2번째 요청부터 스코어 기반 판단                     │
└──────────────────────────────────────────────────────┘
```

### Rule-based Fallback (ML 모델 없이도 동작)

ML 서비스 장애 시 최소한의 봇 차단:

```
Rule 1: 동일 IP에서 1분 내 100회+ 요청 → 차단
Rule 2: 요청 간격이 정확히 동일 (std < 5ms) → 차단
Rule 3: User-Agent가 알려진 봇 → 차단
Rule 4: 동일 fingerprint로 5+ userId 요청 → 차단
```

## 2. 트래픽 예측 모델 (Phase 6, 선택)

### 목표
과거 이벤트 데이터 기반으로 다음 이벤트의 트래픽 패턴 예측 → Auto-scaling 트리거

### 모델
- **Prophet** (Meta): 시계열 예측, 계절성/이벤트 효과 자동 감지
- 학습 데이터: 과거 이벤트의 분 단위 요청 수 시계열

### 피처
| 피처 | 설명 |
|------|------|
| `event_type` | 이벤트 유형 (콘서트, 수강신청, 세일) |
| `max_capacity` | 최대 인원 |
| `day_of_week` | 요일 |
| `hour_of_day` | 시간대 |
| `historical_peak` | 과거 유사 이벤트 피크 TPS |

## 3. ML 서비스 API 명세

### POST /ml/bot-score — 봇 스코어 계산
```
Request:
{
    "eventId": "evt_bts2026",
    "userId": "user_12345",
    "features": {
        "click_interval_mean": 150.5,
        "click_interval_std": 3.2,
        "exact_interval_ratio": 0.85,
        "request_count_1m": 45,
        "ip_user_count": 12,
        "ua_is_headless": true,
        "mouse_movement_entropy": 0.2
    }
}

Response:
{
    "userId": "user_12345",
    "botScore": 0.92,
    "isBot": true,
    "topReasons": [
        {"feature": "exact_interval_ratio", "value": 0.85, "impact": 0.35},
        {"feature": "mouse_movement_entropy", "value": 0.2, "impact": 0.28},
        {"feature": "ip_user_count", "value": 12, "impact": 0.18}
    ],
    "inferenceTimeMs": 3
}
```

### GET /ml/health — 모델 상태
```
Response:
{
    "status": "UP",
    "model": {
        "name": "bot_detector_v1",
        "version": "1.0.0",
        "loadedAt": "2026-03-16T12:00:00Z",
        "accuracy": 0.91,
        "f1Score": 0.89
    }
}
```

## 4. 디렉토리 구조

```
ml/
├── app/
│   ├── main.py              # FastAPI 앱 진입점
│   ├── api/
│   │   ├── routes.py         # 엔드포인트
│   │   └── schemas.py        # Pydantic 모델
│   ├── models/
│   │   ├── bot_detector.py   # LightGBM 래퍼
│   │   └── model.pkl         # 학습된 모델 파일
│   ├── features/
│   │   └── engineering.py    # 피처 엔지니어링
│   └── config.py             # 설정
├── training/
│   ├── generate_data.py      # 시뮬레이션 데이터 생성
│   ├── train.py              # 모델 학습 스크립트
│   ├── evaluate.py           # 모델 평가
│   └── notebooks/
│       └── exploration.ipynb # EDA 노트북
├── tests/
│   ├── test_bot_detector.py
│   └── test_features.py
├── requirements.txt
├── Dockerfile
└── pyproject.toml
```
