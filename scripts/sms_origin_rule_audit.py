#!/usr/bin/env python3
"""Audit RTDB sms_origin export against local sms_rules_v1.json.

The script is intentionally read-only. It does not print raw SMS bodies unless
--show-body is passed, because originBody may contain personal information.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Any


ALLOWED_FAST_PATH_TYPES = {"expense", "overseas", "payment", "debit"}
NUM_TOKEN = r"(?:\d+(?:\.\d+)?|\{N\}(?:\.\{N\})?)"
COUNT_TOKEN = r"(?:\d+|\{N\})\s*건"
NON_TRANSACTION_PATTERNS = (
    re.compile(r"(?<![\d,])0\s*원\s*(?:승인|결제|사용)"),
    re.compile(
        r"(?=.*\[[^\]\n]*(?:쇼핑|홈쇼핑)[^\]\n]*\])"
        r"(?=.*[\d,]+원\s*/\s*[가-힣A-Za-z]+\s+\d{2,6}(?:-\d{2,6}){1,3})"
        r"(?!.*(?:승인|출금|결제완료|입금완료|사용)).*",
        re.S,
    ),
    re.compile(
        r"(?=.*(?:무료체험|가입\s*시|전원\s*지급|상품권\s*\d+만원\s*도착))"
        r"(?=.*(?:신청|가입|혜택|지급|무료)).*",
        re.I | re.S,
    ),
    re.compile(r"(?=.*(?:요율|단가))(?=.*(?:안내|부가세|VAT|MMS|데이터|로밍|국제)).*", re.I | re.S),
    re.compile(
        rf"(?:MMS|SMS|데이터).{{0,80}}{NUM_TOKEN}원\s*/\s*{NUM_TOKEN}\s*(?:KB|MB|GB)",
        re.I | re.S,
    ),
    re.compile(rf"(?:걸\s*때|받을\s*때).{{0,80}}{NUM_TOKEN}원\s*/\s*초", re.I | re.S),
    re.compile(rf"(?=.*(?:교통카드|교통[-\s]?버스|후불하이패스))(?=.*{COUNT_TOKEN}).*", re.S),
    re.compile(
        rf"(?=.*KSNET)(?=.*마이장부)(?=.*(?:신용카드승인|매출접수))(?=.*{COUNT_TOKEN}).*",
        re.I | re.S,
    ),
    re.compile(rf"(?=.*매출접수)(?=.*{COUNT_TOKEN})(?=.*(?:교통|하이패스|기준|집계)).*", re.S),
    re.compile(r"(?=.*(?:입금\s*결과\s*안내|입금결과\s*안내|결과\s*안내))(?=.*(?:습니다|니다|입니다)).*", re.S),
    re.compile(
        r"(?=.*(?:캐시백|캐쉬백))(?=.*(?:결제금액|기본\s*캐시백|프로모션\s*캐시백|입금\s*결과|입금결과))"
        r"(?=.*(?:입금되었습니다|지급되었습니다|처리되었습니다|완료되었습니다|입니다)).*",
        re.S,
    ),
    re.compile(
        r"(?=.*(?:안내|알림))(?=.*(?:습니다|니다|입니다))"
        r"(?=.*(?:결제예정|출금예정|납입|납부|청구|명세서|이용대금|이용금액|카드대금|대출|이자|수수료)).*",
        re.S,
    ),
)
CARD_BILL_PATTERN = re.compile(
    r"카드대금|카드\s+대금|결제대금|결제\s+대금|이용대금|이용\s+대금|이용금액|이용\s+금액|"
    r"청구금액|신용카드대금|카드결제|카드\s+결제",
    re.I,
)
CARD_BILL_SETTLEMENT_PATTERN = re.compile(r"출금|자동이체|납부|결제완료|인출", re.I)
CARD_USAGE_PATTERN = re.compile(r"승인|일시불|할부|가맹점|체크카드출금", re.I)
CARD_BILL_NOTICE_PATTERN = re.compile(r"예정|명세서|청구서|납부안내|결제일", re.I)
WON_AMOUNT_PATTERN = re.compile(r"[\d,]+원")
FOREIGN_CURRENCY_PATTERN = re.compile(
    r"\b(?:USD|EUR|JPY|GBP|CNY|HKD|AUD|CAD|SGD|CHF)\s+\d+(?:\.\d+)?\b",
    re.I,
)
INCOMPLETE_PAYMENT_INSTRUCTION_PATTERN = re.compile(
    r"\[[^\]\n]*(?:쇼핑|홈쇼핑)[^\]\n]*\]\s*[\d,]+원\s*/\s*[^\n]+\d{2,6}(?:-\d{2,6}){1,3}",
    re.S,
)
COMPLETED_TRANSACTION_PATTERN = re.compile(r"승인|출금|결제완료|입금완료|사용", re.I)
STORE_NUMBER_ONLY_PATTERN = re.compile(r"^[\d,.:/\-\s]+$")
STORE_DATE_OR_TIME_PATTERN = re.compile(
    r"^(?:\d{1,2}[/.-]\d{1,2}(?:\s+\d{1,2}:\d{2})?|\d{1,2}:\d{2})$"
)
STORE_VALID_SERVICE_LABELS = {"입출금알림수수료"}
STORE_INVALID_KEYWORDS = {
    "출금",
    "입금",
    "승인",
    "누적",
    "잔액",
    "일시불",
    "할부",
    "통지수수료",
    "민생회복",
    "소비쿠폰",
    "승인거절",
}
CASHBACK_PREFIX_PATTERN = re.compile(r"^\*?\d+원(?:캐쉬백|캐시백)\s*")
STORE_SUFFIX_TRIM_PATTERN = re.compile(r"\s*(?:누적.*|잔액.*)$", re.S)
DEBIT_LINE_PATTERN = re.compile(r"(?:^|\n)출금(?:\n|$)")
INCOME_ROUTE_TYPES = {"cancel", "income"}
INCOME_FINANCIAL_PATTERN = re.compile(
    r"kb|국민|노리|신한|sol|쏠|삼성|현대|스마일|smile|롯데|하나|우리|nh|농협|"
    r"bc|비씨|씨티|시티|citi|카카오|카뱅|토스|케이뱅크|k뱅크|ibk|기업|sc제일|"
    r"제일은행|수협|광주은행|kjb|전북은행|경남은행|bnk|부산은행|대구은행|dgb|"
    r"새마을|mg|신협|kfcc|우체국|우정|post|저축은행|체크카드|신용카드|선불|후불",
    re.I,
)
INCOME_CANCELLATION_PATTERN = re.compile(
    r"(?:출금|승인|결제|사용|이용)\s*취소|취소\s*(?:승인|완료|처리|환불)?|환불"
)
INCOME_HINT_PATTERN = re.compile(
    r"입금|이체입금|급여|월급|보너스|상여|환급|정산|송금|받으셨습니다|입금되었습니다|"
    r"자동이체입금|무통장입금|계좌입금|출금취소"
)
INCOME_AMOUNT_WITH_WON_PATTERN = re.compile(r"([\d,]+)원(?![가-힣])")
INCOME_NUMBER_ONLY_LINE_PATTERN = re.compile(r"(?m)^([\d,]{3,})$")


@dataclass(frozen=True)
class AssetRule:
    sender: str
    rule_type: str
    rule_key: str
    body_regex: str
    priority: int
    amount_group: str
    store_group: str
    card_group: str
    date_group: str


@dataclass(frozen=True)
class RuleEvaluation:
    rule_key: str = ""
    failure_reason: str = ""


@dataclass(frozen=True)
class OriginSample:
    sender: str
    sample_type: str
    sample_key: str
    outcome: str
    count: int
    fail_stage: str
    fail_reason: str
    failure_template: str
    origin_body: str
    masked_body: str
    raw: dict[str, Any]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compare RTDB sms_origin fail samples with local Fast Path regex rules."
    )
    parser.add_argument("--origin", required=True, help="Path to RTDB sms_origin export JSON.")
    parser.add_argument(
        "--asset",
        default="app/src/main/assets/sms_rules_v1.json",
        help="Path to local sms_rules_v1.json.",
    )
    parser.add_argument("--limit", type=int, default=20, help="Max clusters to print.")
    parser.add_argument(
        "--all-outcomes",
        action="store_true",
        help="Audit success and fail samples together. Default scope is fail samples only.",
    )
    parser.add_argument(
        "--min-pipeline-success-rate",
        type=float,
        help="Exit with failure when the correctly handled row rate is below this percentage.",
    )
    parser.add_argument(
        "--show-body",
        action="store_true",
        help="Print raw originBody when available. Use only with masked/safe exports.",
    )
    args = parser.parse_args()
    if (
        args.min_pipeline_success_rate is not None
        and not 0.0 <= args.min_pipeline_success_rate <= 100.0
    ):
        parser.error("--min-pipeline-success-rate must be between 0 and 100")
    return args


def load_json(path: str) -> Any:
    with Path(path).expanduser().open(encoding="utf-8") as file:
        return json.load(file)


def load_asset_rules(path: str) -> dict[str, list[AssetRule]]:
    data = load_json(path)
    rules_by_sender_and_type: dict[tuple[str, str], list[AssetRule]] = defaultdict(list)
    for sender, type_node in data.get("sms_rules", {}).items():
        for rule_type, rule_node in type_node.items():
            if rule_type not in ALLOWED_FAST_PATH_TYPES:
                continue
            for rule_key, payload in rule_node.items():
                if payload.get("status") != "ACTIVE":
                    continue
                body_regex = str(payload.get("bodyRegex", ""))
                if not body_regex:
                    continue
                rules_by_sender_and_type[(sender, rule_type)].append(
                    AssetRule(
                        sender=sender,
                        rule_type=rule_type,
                        rule_key=rule_key,
                        body_regex=body_regex,
                        priority=int(payload.get("priority") or 0),
                        amount_group=str(payload.get("amountGroup") or ""),
                        store_group=str(payload.get("storeGroup") or ""),
                        card_group=str(payload.get("cardGroup") or ""),
                        date_group=str(payload.get("dateGroup") or ""),
                    )
                )

    rules_by_sender: dict[str, list[AssetRule]] = defaultdict(list)
    for (sender, _), rules in rules_by_sender_and_type.items():
        rules.sort(key=lambda rule: rule.priority, reverse=True)
        rules_by_sender[sender].extend(rules[:5])
    for rules in rules_by_sender.values():
        rules.sort(key=lambda rule: rule.priority, reverse=True)
    return dict(rules_by_sender)


def flatten_origin_samples(data: Any) -> list[OriginSample]:
    root = data.get("sms_origin", data) if isinstance(data, dict) else {}
    samples: list[OriginSample] = []
    if not isinstance(root, dict):
        return samples

    for sender, sender_node in root.items():
        if not isinstance(sender_node, dict):
            continue
        for sample_type, type_node in sender_node.items():
            if not isinstance(type_node, dict):
                continue
            for sample_key, payload in type_node.items():
                if not isinstance(payload, dict) or "outcome" not in payload:
                    continue
                samples.append(
                    OriginSample(
                        sender=str(payload.get("normalizedSenderAddress") or sender),
                        sample_type=str(payload.get("type") or sample_type).lower(),
                        sample_key=str(sample_key),
                        outcome=str(payload.get("outcome") or ""),
                        count=safe_int(payload.get("count"), default=1),
                        fail_stage=str(payload.get("failStage") or ""),
                        fail_reason=str(payload.get("failReason") or ""),
                        failure_template=str(payload.get("failureTemplate") or ""),
                        origin_body=str(payload.get("originBody") or ""),
                        masked_body=str(payload.get("maskedBody") or ""),
                        raw=payload,
                    )
                )
    return samples


def safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def compile_java_regex(pattern: str) -> re.Pattern[str] | None:
    python_pattern = re.sub(r"\(\?<([A-Za-z][A-Za-z0-9_]*)>", r"(?P<\1>", pattern)
    try:
        return re.compile(python_pattern)
    except re.error:
        return None


def evaluate_asset_match(
    sample: OriginSample,
    rules_by_sender: dict[str, list[AssetRule]],
) -> RuleEvaluation:
    if not sample.origin_body:
        return RuleEvaluation(failure_reason="body_unavailable")
    rules = rules_by_sender.get(sample.sender, [])
    if not rules:
        return RuleEvaluation(failure_reason="no_active_rule")

    normalized_body = normalize_body(sample.origin_body)
    last_failure = "no_regex_match"
    for rule in rules:
        compiled = compile_java_regex(rule.body_regex)
        if compiled is None:
            last_failure = "invalid_regex"
            continue
        match = compiled.search(normalized_body)
        if match is None:
            continue

        amount = extract_amount(match, rule.amount_group)
        if amount is None:
            last_failure = "amount_extract_failed"
            continue
        if amount <= 0:
            last_failure = "invalid_amount"
            continue

        store = extract_group_value(match, rule.store_group)
        normalized_store = normalize_store_candidate(store, normalized_body)
        if not normalized_store:
            normalized_store = extract_store_fallback(normalized_body)
        if not normalized_store:
            last_failure = "store_extract_failed"
            continue
        return RuleEvaluation(rule_key=rule.rule_key)
    return RuleEvaluation(failure_reason=last_failure)


def normalize_body(body: str) -> str:
    return body.replace("\r\n", "\n").replace("\r", "\n")


def extract_group_value(match: re.Match[str], group_key: str) -> str:
    key = group_key.strip()
    if not key:
        return ""
    try:
        value = match.group(int(key)) if key.isdigit() else match.group(key)
    except (IndexError, KeyError):
        return ""
    return (value or "").strip()


def extract_amount(match: re.Match[str], group_key: str) -> int | None:
    raw = extract_group_value(match, group_key)
    digits = re.sub(r"\D", "", raw)
    if not digits:
        return None
    amount = safe_int(digits, default=-1)
    return amount if amount <= 2_147_483_647 else None


def sanitize_store_candidate(raw: str) -> str:
    value = CASHBACK_PREFIX_PATTERN.sub("", raw)
    value = STORE_SUFFIX_TRIM_PATTERN.sub("", value)
    return re.sub(r"\s+", " ", value).strip()


def is_valid_store_candidate(value: str) -> bool:
    if not value or len(value) > 30 or "{" in value:
        return False
    if STORE_NUMBER_ONLY_PATTERN.fullmatch(value) or STORE_DATE_OR_TIME_PATTERN.fullmatch(value):
        return False
    if value in STORE_VALID_SERVICE_LABELS:
        return True
    return not any(keyword in value for keyword in STORE_INVALID_KEYWORDS)


def normalize_store_candidate(raw: str, body: str) -> str:
    value = sanitize_store_candidate(raw)
    if is_valid_store_candidate(value):
        return value
    if not value or not DEBIT_LINE_PATTERN.search(body) or "{" in value:
        return ""
    if STORE_DATE_OR_TIME_PATTERN.fullmatch(value):
        return ""
    if STORE_NUMBER_ONLY_PATTERN.fullmatch(value) or "입출통지" in value:
        return "계좌출금"
    if any(keyword in value for keyword in STORE_INVALID_KEYWORDS):
        return "계좌출금"
    return value


def extract_store_fallback(body: str) -> str:
    after_account = re.search(
        r"\n\d+\*+\d+\n(.+?)\n(?:출금|입금|체크카드출금|스마트폰출금|공동CMS출|지로출금|FBS출금)",
        body,
    )
    if after_account:
        candidate = sanitize_store_candidate(after_account.group(1))
        if is_valid_store_candidate(candidate):
            return candidate

    after_time = re.search(
        r"\d{1,2}[/.-]\d{1,2}\s+\d{1,2}:\d{2}\s+(.+?)(?:\s+누적|\s*$)",
        body,
    )
    if after_time:
        candidate = sanitize_store_candidate(after_time.group(1))
        if is_valid_store_candidate(candidate):
            return candidate

    lines = [line.strip() for line in body.split("\n") if line.strip()]
    for line in lines:
        candidate = sanitize_store_candidate(line)
        if is_valid_store_candidate(candidate):
            return candidate
    return ""


def is_non_transaction(sample: OriginSample) -> bool:
    text = "\n".join(
        value for value in (sample.failure_template, sample.masked_body, sample.origin_body) if value
    )
    if is_completed_card_bill_debit(text):
        return False
    return any(pattern.search(text) for pattern in NON_TRANSACTION_PATTERNS)


def is_completed_card_bill_debit(text: str) -> bool:
    return (
        bool(CARD_BILL_PATTERN.search(text))
        and bool(CARD_BILL_SETTLEMENT_PATTERN.search(text))
        and bool(WON_AMOUNT_PATTERN.search(text))
        and not CARD_USAGE_PATTERN.search(text)
        and not CARD_BILL_NOTICE_PATTERN.search(text)
    )


def sample_text(sample: OriginSample) -> str:
    return "\n".join(
        value for value in (sample.failure_template, sample.masked_body, sample.origin_body) if value
    )


def is_unsupported_foreign_currency(sample: OriginSample) -> bool:
    return bool(FOREIGN_CURRENCY_PATTERN.search(sample_text(sample)))


def has_insufficient_completion_evidence(sample: OriginSample) -> bool:
    text = sample_text(sample)
    return bool(INCOMPLETE_PAYMENT_INSTRUCTION_PATTERN.search(text)) and not bool(
        COMPLETED_TRANSACTION_PATTERN.search(text)
    )


def extract_income_amount(text: str) -> int | None:
    match = INCOME_AMOUNT_WITH_WON_PATTERN.search(text)
    if match is None:
        match = INCOME_NUMBER_ONLY_LINE_PATTERN.search(text)
    if match is None:
        return None
    amount = safe_int(match.group(1).replace(",", ""), default=-1)
    return amount if 100 <= amount <= 2_147_483_647 else None


def is_routed_income(sample: OriginSample) -> bool:
    if sample.sample_type not in INCOME_ROUTE_TYPES:
        return False
    text = normalize_body(sample.origin_body)
    if not text or len(text) > 130 or not INCOME_FINANCIAL_PATTERN.search(text):
        return False
    if extract_income_amount(text) is None:
        return False
    if sample.sample_type == "cancel":
        return bool(INCOME_CANCELLATION_PATTERN.search(text.lower()))
    return bool(INCOME_HINT_PATTERN.search(text.lower()))


def classify_sample(sample: OriginSample, evaluation: RuleEvaluation) -> str:
    policy_category = ""
    if is_non_transaction(sample):
        policy_category = "non_transaction"
    elif sample.sample_type not in ALLOWED_FAST_PATH_TYPES:
        policy_category = "routed_income" if is_routed_income(sample) else "unsupported_type"
    elif is_unsupported_foreign_currency(sample):
        policy_category = "unsupported_currency"
    elif has_insufficient_completion_evidence(sample):
        policy_category = "insufficient_evidence"

    if policy_category:
        return "policy_false_positive" if evaluation.rule_key else policy_category
    if evaluation.rule_key:
        return "matched"
    if evaluation.failure_reason == "body_unavailable":
        return "body_unavailable"
    if evaluation.failure_reason == "invalid_amount":
        return "invalid_amount"
    return "actionable_unmatched"


def observation_count(samples: list[OriginSample]) -> int:
    return sum(sample.count for sample in samples)


def candidate_rule_key(sample: OriginSample) -> str:
    body_regex = str(sample.raw.get("bodyRegex") or "")
    amount_group = str(sample.raw.get("amountGroup") or "")
    store_group = str(sample.raw.get("storeGroup") or "")
    card_group = str(sample.raw.get("cardGroup") or "")
    date_group = str(sample.raw.get("dateGroup") or "")
    version = safe_int(sample.raw.get("version"), default=1)
    if not body_regex or not amount_group or not store_group:
        return ""
    key_input = "|".join(
        [
            sample.sender,
            sample.sample_type,
            body_regex,
            amount_group,
            store_group,
            card_group,
            date_group,
            str(version),
        ]
    )
    return hashlib.sha256(key_input.encode("utf-8")).hexdigest()[:24]


def sanitize_for_output(value: str) -> str:
    sanitized = re.sub(r"[가-힣]{2,4}님", "고객님", value)
    sanitized = re.sub(r"(?<![가-힣])[가-힣]\*[가-힣](?![가-힣])", "고객", sanitized)
    return sanitized


def shorten(value: str, limit: int = 140, *, sanitize: bool = True) -> str:
    if sanitize:
        value = sanitize_for_output(value)
    normalized = value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " | ")
    if len(normalized) <= limit:
        return normalized
    return normalized[: limit - 3] + "..."


def main() -> None:
    args = parse_args()
    samples = flatten_origin_samples(load_json(args.origin))
    scoped_samples = (
        samples
        if args.all_outcomes
        else [sample for sample in samples if sample.outcome == "fail"]
    )
    rules_by_sender = load_asset_rules(args.asset)
    evaluations = {
        (sample.sender, sample.sample_type, sample.sample_key): evaluate_asset_match(
            sample, rules_by_sender
        )
        for sample in scoped_samples
    }
    categories: dict[str, list[OriginSample]] = defaultdict(list)
    for sample in scoped_samples:
        identity = (sample.sender, sample.sample_type, sample.sample_key)
        categories[classify_sample(sample, evaluations[identity])].append(sample)

    matched_samples = categories["matched"]
    actionable = categories["actionable_unmatched"]
    valid_candidate_samples = matched_samples + actionable
    valid_candidate_observations = observation_count(valid_candidate_samples)
    matched_observations = observation_count(matched_samples)
    row_success_rate = (
        len(matched_samples) * 100.0 / len(valid_candidate_samples)
        if valid_candidate_samples
        else 100.0
    )
    observation_success_rate = (
        matched_observations * 100.0 / valid_candidate_observations
        if valid_candidate_observations
        else 100.0
    )
    handled_categories = ("matched", "non_transaction", "routed_income")
    pipeline_handled = [sample for category in handled_categories for sample in categories[category]]
    pipeline_handled_observations = observation_count(pipeline_handled)
    pipeline_unhandled = [
        sample
        for category, category_samples in categories.items()
        if category not in handled_categories
        for sample in category_samples
    ]
    pipeline_success_rate = len(pipeline_handled) * 100.0 / len(scoped_samples) if scoped_samples else 100.0
    pipeline_observation_success_rate = (
        pipeline_handled_observations * 100.0 / observation_count(scoped_samples)
        if scoped_samples
        else 100.0
    )

    clusters: dict[tuple[str, str, str, str, str], list[OriginSample]] = defaultdict(list)
    for sample in scoped_samples:
        clusters[
            (
                sample.sender,
                sample.sample_type,
                sample.fail_stage,
                sample.fail_reason,
                sample.failure_template,
            )
        ].append(sample)

    print("# sms_origin regex audit")
    print(f"origin: {Path(args.origin).expanduser()}")
    print(f"asset: {Path(args.asset).expanduser()}")
    print(f"sample_scope: {'all' if args.all_outcomes else 'fail'}")
    print(f"samples: {len(scoped_samples)}")
    print(f"observations: {observation_count(scoped_samples)}")
    print(f"current_asset_matched: {len(matched_samples)}")
    print(f"current_asset_matched_observations: {matched_observations}")
    for category in (
        "non_transaction",
        "routed_income",
        "unsupported_type",
        "unsupported_currency",
        "insufficient_evidence",
        "invalid_amount",
        "body_unavailable",
        "policy_false_positive",
    ):
        category_samples = categories[category]
        print(f"{category}: {len(category_samples)}")
        print(f"{category}_observations: {observation_count(category_samples)}")
    print(f"actionable_unmatched: {len(actionable)}")
    print(f"actionable_unmatched_observations: {observation_count(actionable)}")
    print(f"valid_candidate_success_rate: {row_success_rate:.1f}%")
    print(f"valid_candidate_observation_success_rate: {observation_success_rate:.1f}%")
    print(f"pipeline_handled: {len(pipeline_handled)}")
    print(f"pipeline_handled_observations: {pipeline_handled_observations}")
    print(f"pipeline_unhandled: {len(pipeline_unhandled)}")
    print(f"pipeline_unhandled_observations: {observation_count(pipeline_unhandled)}")
    print(f"pipeline_success_rate: {pipeline_success_rate:.1f}%")
    print(f"pipeline_observation_success_rate: {pipeline_observation_success_rate:.1f}%")
    print()
    print("## clusters")

    sorted_clusters = sorted(
        clusters.values(),
        key=lambda group: (sum(sample.count for sample in group), len(group)),
        reverse=True,
    )
    for group in sorted_clusters[: args.limit]:
        first = group[0]
        total_count = sum(sample.count for sample in group)
        group_evaluations = [
            evaluations[(sample.sender, sample.sample_type, sample.sample_key)] for sample in group
        ]
        matched = [evaluation.rule_key for evaluation in group_evaluations if evaluation.rule_key]
        group_categories = {
            classify_sample(
                sample,
                evaluations[(sample.sender, sample.sample_type, sample.sample_key)],
            )
            for sample in group
        }
        candidate_keys = sorted({candidate_rule_key(sample) for sample in group} - {""})
        if "policy_false_positive" in group_categories:
            status = "error_policy_false_positive"
        elif matched:
            status = f"covered_by_asset:{matched[0]}"
        elif "non_transaction" in group_categories:
            status = "skip_non_transaction"
        elif "routed_income" in group_categories:
            status = "handled_by_income_route"
        elif "unsupported_type" in group_categories:
            status = "skip_unsupported_type"
        elif "unsupported_currency" in group_categories:
            status = "skip_unsupported_currency"
        elif "insufficient_evidence" in group_categories:
            status = "skip_insufficient_evidence"
        elif "invalid_amount" in group_categories:
            status = "skip_invalid_amount"
        elif "body_unavailable" in group_categories:
            status = "needs_body_or_masked_csv"
        else:
            status = "needs_rule_review"

        print(
            f"- sender={first.sender} type={first.sample_type} "
            f"count={total_count} samples={len(group)}"
        )
        print(f"  status={status}")
        print(f"  fail={first.fail_stage}:{first.fail_reason}")
        failure_reasons = sorted(
            {evaluation.failure_reason for evaluation in group_evaluations if evaluation.failure_reason}
        )
        if failure_reasons and status == "needs_rule_review":
            print(f"  runtimeFailure={failure_reasons[0]}")
        if candidate_keys and status == "needs_rule_review":
            print(f"  candidateRuleKey={candidate_keys[0]}")
        if first.failure_template:
            print(f"  template={shorten(first.failure_template)}")
        elif first.masked_body:
            print(f"  masked={shorten(first.masked_body)}")
        if args.show_body and first.origin_body:
            print(f"  body={shorten(first.origin_body, limit=220, sanitize=False)}")

    if (
        args.min_pipeline_success_rate is not None
        and pipeline_success_rate < args.min_pipeline_success_rate
    ):
        raise SystemExit(
            f"pipeline success rate {pipeline_success_rate:.1f}% is below "
            f"required {args.min_pipeline_success_rate:.1f}%"
        )


if __name__ == "__main__":
    main()
