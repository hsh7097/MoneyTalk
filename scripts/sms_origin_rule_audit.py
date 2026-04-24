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


ALLOWED_FAST_PATH_TYPES = {"expense", "cancel", "overseas", "payment", "debit"}
NUM_TOKEN = r"(?:\d+(?:\.\d+)?|\{N\}(?:\.\{N\})?)"
NON_TRANSACTION_PATTERNS = (
    re.compile(r"(?=.*(?:요율|단가))(?=.*(?:안내|부가세|VAT|MMS|데이터|로밍|국제)).*", re.I | re.S),
    re.compile(
        rf"(?:MMS|SMS|데이터).{{0,80}}{NUM_TOKEN}원\s*/\s*{NUM_TOKEN}\s*(?:KB|MB|GB)",
        re.I | re.S,
    ),
    re.compile(rf"(?:걸\s*때|받을\s*때).{{0,80}}{NUM_TOKEN}원\s*/\s*초", re.I | re.S),
)


@dataclass(frozen=True)
class AssetRule:
    sender: str
    rule_type: str
    rule_key: str
    body_regex: str
    priority: int


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
        "--show-body",
        action="store_true",
        help="Print raw originBody when available. Use only with masked/safe exports.",
    )
    return parser.parse_args()


def load_json(path: str) -> Any:
    with Path(path).expanduser().open(encoding="utf-8") as file:
        return json.load(file)


def load_asset_rules(path: str) -> dict[tuple[str, str], list[AssetRule]]:
    data = load_json(path)
    rules_by_bucket: dict[tuple[str, str], list[AssetRule]] = defaultdict(list)
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
                rules_by_bucket[(sender, rule_type)].append(
                    AssetRule(
                        sender=sender,
                        rule_type=rule_type,
                        rule_key=rule_key,
                        body_regex=body_regex,
                        priority=int(payload.get("priority") or 0),
                    )
                )
    for rules in rules_by_bucket.values():
        rules.sort(key=lambda rule: rule.priority, reverse=True)
    return dict(rules_by_bucket)


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


def find_asset_match(
    sample: OriginSample,
    rules_by_bucket: dict[tuple[str, str], list[AssetRule]],
) -> str:
    if not sample.origin_body:
        return ""
    for rule in rules_by_bucket.get((sample.sender, sample.sample_type), []):
        compiled = compile_java_regex(rule.body_regex)
        if compiled and compiled.search(sample.origin_body):
            return rule.rule_key
    return ""


def is_non_transaction(sample: OriginSample) -> bool:
    text = "\n".join(
        value for value in (sample.failure_template, sample.masked_body, sample.origin_body) if value
    )
    return any(pattern.search(text) for pattern in NON_TRANSACTION_PATTERNS)


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
    normalized = value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", " ↵ ")
    if len(normalized) <= limit:
        return normalized
    return normalized[: limit - 1] + "…"


def main() -> None:
    args = parse_args()
    samples = flatten_origin_samples(load_json(args.origin))
    fail_samples = [sample for sample in samples if sample.outcome == "fail"]
    rules_by_bucket = load_asset_rules(args.asset)

    matched_keys = {sample.sample_key: find_asset_match(sample, rules_by_bucket) for sample in fail_samples}
    non_transaction_keys = {
        sample.sample_key for sample in fail_samples if is_non_transaction(sample)
    }
    body_unavailable = [sample for sample in fail_samples if not sample.origin_body]
    actionable = [
        sample
        for sample in fail_samples
        if not matched_keys[sample.sample_key]
        and sample.sample_key not in non_transaction_keys
        and sample.sample_type in ALLOWED_FAST_PATH_TYPES
    ]

    clusters: dict[tuple[str, str, str, str, str], list[OriginSample]] = defaultdict(list)
    for sample in fail_samples:
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
    print(f"fail_samples: {len(fail_samples)}")
    print(f"current_asset_matched: {sum(1 for key in matched_keys.values() if key)}")
    print(f"non_transaction_suspect: {len(non_transaction_keys)}")
    print(f"body_unavailable: {len(body_unavailable)}")
    print(f"actionable_unmatched: {len(actionable)}")
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
        matched = [matched_keys[sample.sample_key] for sample in group if matched_keys[sample.sample_key]]
        non_transaction = any(sample.sample_key in non_transaction_keys for sample in group)
        candidate_keys = sorted({candidate_rule_key(sample) for sample in group} - {""})
        if matched:
            status = f"covered_by_asset:{matched[0]}"
        elif non_transaction:
            status = "skip_non_transaction"
        elif first.sample_type not in ALLOWED_FAST_PATH_TYPES:
            status = "skip_unsupported_type"
        elif not first.origin_body:
            status = "needs_body_or_masked_csv"
        else:
            status = "needs_rule_review"

        print(f"- sender={first.sender} type={first.sample_type} count={total_count} samples={len(group)}")
        print(f"  status={status}")
        print(f"  fail={first.fail_stage}:{first.fail_reason}")
        if candidate_keys and status == "needs_rule_review":
            print(f"  candidateRuleKey={candidate_keys[0]}")
        if first.failure_template:
            print(f"  template={shorten(first.failure_template)}")
        elif first.masked_body:
            print(f"  masked={shorten(first.masked_body)}")
        if args.show_body and first.origin_body:
            print(f"  body={shorten(first.origin_body, limit=220, sanitize=False)}")


if __name__ == "__main__":
    main()
