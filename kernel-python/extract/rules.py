"""
离线规则抽取兜底。

当本地 Ollama 与云端都不可用时，用纯规则（零成本）抽取常见实体，
让事件仍能落到 Person / Topic，从而支撑规律学习。属粗粒度启发式，允许误报，
只作 LLM 不可用时的降级，不替代 LLM。

可抽取：
  - Person：常见姓氏 + 1~2 个汉字的人名
  - Topic ：关键词 → 主题（供事件挂 RELATES_TO，按主题聚合检测周期）
"""

from __future__ import annotations

import re

# 常见姓氏（Top ~120），用于识别「姓氏 + 1~2 字名」
SURNAMES = (
    "赵钱孙李周吴郑王冯陈褚卫蒋沈韩杨朱秦尤许何吕施张孔曹严华金魏陶姜"
    "戚谢邹喻柏水窦章云苏潘葛奚范彭郎鲁韦昌马苗凤花方俞任袁柳酆鲍史唐"
    "费廉岑薛雷贺倪汤滕殷罗毕郝邬安常乐于时傅皮卞齐康伍余元卜顾孟平黄"
    "和穆萧尹姚邵湛汪祁毛禹狄米贝明臧计伏成戴谈宋茅庞熊纪舒屈项祝董梁"
    "杜阮蓝闵席季麻强贾路娄危江童颜郭梅盛林刁钟徐邱骆高夏蔡田樊胡凌霍"
)
_SURNAME_CLASS = "".join(sorted(set(SURNAMES)))

# 姓氏 + 1~2 个汉字；要求姓氏前不能紧邻汉字（负向后行断言），
# 避免「完成发版」里的「成」被误认作姓氏（成 + 发 + 版 → 成发版）。
_NAME_RE = re.compile(rf"(?<![\u4e00-\u9fa5])([{_SURNAME_CLASS}])([\u4e00-\u9fa5]{{1,2}})")

# 误报黑名单：时间词 / 会议词等会被姓名正则误中的常见词
_NAME_BLOCKLIST = {
    "周一", "周二", "周三", "周四", "周五", "周六", "周日", "周天",
    "下周", "上周", "本周", "周末", "今天", "明天", "昨天", "前天", "后天",
    "上午", "下午", "中午", "晚上", "早上", "周会", "例会", "晨会", "站会",
}


def _is_blocked(name: str) -> bool:
    """名字本身是时间/会议词，或以这类词开头。

    贪婪正则 `[姓氏][汉字]{1,2}` 会多吞 1~2 字（如「周末去」），
    因此用 startswith 而非全等比较，否则块名单基本失效。
    """
    return any(name.startswith(w) for w in _NAME_BLOCKLIST)

# 关键词 → 主题（事件据此挂到 Topic，供规律学习按主题聚合）
TOPIC_KEYWORDS: dict[str, str] = {
    "版本评审": "版本评审",
    "代码评审": "代码评审",
    "code review": "代码评审",
    "晨会": "晨会",
    "站会": "晨会",
    "周会": "周会",
    "例会": "周会",
    "面试": "面试",
    "复盘": "复盘",
    "健身": "健身",
    "跑步": "健身",
    "读书": "读书",
    "出差": "出差",
    "报销": "报销",
    "发版": "发版",
    "上线": "发版",
}


def extract_rules(text: str) -> tuple[list[dict], list[dict]]:
    """返回 (entities, relations)，与 LLM 抽取结果同构。"""
    entities: list[dict] = []
    seen: set[tuple[str, str]] = set()

    for m in _NAME_RE.finditer(text):
        name = m.group(0)
        if _is_blocked(name):
            continue
        key = ("Person", name)
        if key in seen:
            continue
        seen.add(key)
        entities.append({"type": "Person", "name": name})

    for kw, topic in TOPIC_KEYWORDS.items():
        if kw in text:
            key = ("Topic", topic)
            if key in seen:
                continue
            seen.add(key)
            entities.append({"type": "Topic", "name": topic})

    return entities, []
