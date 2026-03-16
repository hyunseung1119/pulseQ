"""시뮬레이션 학습 데이터 생성 — 정상 사용자 80,000 + 봇 20,000 = 100,000건"""

import random

import numpy as np
import pandas as pd


def generate_normal_user() -> dict:
    """실제 사용자: 불규칙한 클릭, 마우스 움직임 있음, IP당 1-2명"""
    interval_mean = max(500, random.gauss(2000, 800))
    return {
        "click_interval_mean": interval_mean,
        "click_interval_std": random.uniform(200, min(interval_mean * 0.8, 2000)),
        "click_interval_min": max(100, interval_mean - random.uniform(500, 1500)),
        "request_count_1m": random.randint(1, 15),
        "request_count_5m": random.randint(1, 40),
        "exact_interval_ratio": random.uniform(0.0, 0.15),
        "time_before_event": random.uniform(0, 3600),
        "page_dwell_time": random.uniform(5, 300),
        "mouse_movement_entropy": random.uniform(2.5, 8.0),
        "scroll_events": random.randint(0, 30),
        "has_cookie": int(random.random() > 0.05),  # 95% have cookie
        "ip_request_count": random.randint(1, 10),
        "ip_user_count": random.choice([1, 1, 1, 1, 2]),  # mostly 1
        "is_datacenter_ip": int(random.random() < 0.02),  # 2%
        "is_vpn_tor": int(random.random() < 0.05),  # 5%
        "ua_is_headless": 0,
        "fingerprint_collision": random.choice([0, 0, 0, 0, 1]),
        "is_bot": 0,
    }


def generate_bot_user() -> dict:
    """봇: 매우 빠르고 규칙적인 클릭, 마우스 없음, IP당 다수"""
    bot_type = random.choice(["fast_bot", "stealth_bot", "farm_bot"])

    if bot_type == "fast_bot":
        # 고속 매크로: 매우 빠르고 규칙적
        interval_mean = max(10, random.gauss(100, 30))
        return {
            "click_interval_mean": interval_mean,
            "click_interval_std": random.uniform(0, 10),
            "click_interval_min": max(5, interval_mean - random.uniform(0, 20)),
            "request_count_1m": random.randint(30, 200),
            "request_count_5m": random.randint(100, 800),
            "exact_interval_ratio": random.uniform(0.7, 1.0),
            "time_before_event": random.uniform(0, 5),  # 이벤트 시작 직후
            "page_dwell_time": random.uniform(0, 2),
            "mouse_movement_entropy": random.uniform(0, 0.5),
            "scroll_events": 0,
            "has_cookie": int(random.random() > 0.7),
            "ip_request_count": random.randint(50, 500),
            "ip_user_count": random.randint(5, 50),
            "is_datacenter_ip": int(random.random() < 0.6),
            "is_vpn_tor": int(random.random() < 0.3),
            "ua_is_headless": int(random.random() < 0.8),
            "fingerprint_collision": random.randint(3, 30),
            "is_bot": 1,
        }
    elif bot_type == "stealth_bot":
        # 스텔스 봇: 속도는 중간이지만 규칙적, 헤드리스
        interval_mean = max(200, random.gauss(500, 100))
        return {
            "click_interval_mean": interval_mean,
            "click_interval_std": random.uniform(5, 30),
            "click_interval_min": max(100, interval_mean - random.uniform(10, 50)),
            "request_count_1m": random.randint(10, 40),
            "request_count_5m": random.randint(40, 150),
            "exact_interval_ratio": random.uniform(0.4, 0.8),
            "time_before_event": random.uniform(0, 30),
            "page_dwell_time": random.uniform(1, 10),
            "mouse_movement_entropy": random.uniform(0.3, 1.5),
            "scroll_events": random.randint(0, 3),
            "has_cookie": int(random.random() > 0.3),
            "ip_request_count": random.randint(10, 80),
            "ip_user_count": random.randint(2, 10),
            "is_datacenter_ip": int(random.random() < 0.4),
            "is_vpn_tor": int(random.random() < 0.2),
            "ua_is_headless": int(random.random() < 0.5),
            "fingerprint_collision": random.randint(1, 10),
            "is_bot": 1,
        }
    else:
        # 팜 봇: 느리지만 동일 IP에서 대량 유저
        interval_mean = max(800, random.gauss(1500, 300))
        return {
            "click_interval_mean": interval_mean,
            "click_interval_std": random.uniform(10, 100),
            "click_interval_min": max(300, interval_mean - random.uniform(100, 500)),
            "request_count_1m": random.randint(5, 20),
            "request_count_5m": random.randint(15, 60),
            "exact_interval_ratio": random.uniform(0.3, 0.6),
            "time_before_event": random.uniform(0, 60),
            "page_dwell_time": random.uniform(2, 20),
            "mouse_movement_entropy": random.uniform(0.1, 2.0),
            "scroll_events": random.randint(0, 5),
            "has_cookie": int(random.random() > 0.5),
            "ip_request_count": random.randint(30, 200),
            "ip_user_count": random.randint(10, 100),
            "is_datacenter_ip": int(random.random() < 0.5),
            "is_vpn_tor": int(random.random() < 0.15),
            "ua_is_headless": int(random.random() < 0.3),
            "fingerprint_collision": random.randint(5, 50),
            "is_bot": 1,
        }


def generate_dataset(n_normal: int = 80000, n_bot: int = 20000, seed: int = 42) -> pd.DataFrame:
    random.seed(seed)
    np.random.seed(seed)

    data = []
    for _ in range(n_normal):
        data.append(generate_normal_user())
    for _ in range(n_bot):
        data.append(generate_bot_user())

    df = pd.DataFrame(data)
    df = df.sample(frac=1, random_state=seed).reset_index(drop=True)

    print(f"Generated {len(df)} samples: {n_normal} normal + {n_bot} bot")
    print(f"Bot ratio: {df['is_bot'].mean():.1%}")
    print(f"Memory: {df.memory_usage(deep=True).sum() / 1024 / 1024:.1f} MB")

    return df


if __name__ == "__main__":
    df = generate_dataset()
    output_path = "training/data/training_data.csv"
    import os
    os.makedirs("training/data", exist_ok=True)
    df.to_csv(output_path, index=False)
    print(f"Saved to {output_path}")
