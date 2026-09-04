"""离线规则抽取测试。"""

from extract.rules import extract_rules


def test_person_and_topic():
    ents, _ = extract_rules("张伟 参加晨会")
    got = {(e["type"], e["name"]) for e in ents}
    assert ("Person", "张伟") in got
    assert ("Topic", "晨会") in got


def test_time_word_not_person():
    ents, _ = extract_rules("韩梅梅 周末去健身")
    persons = [e["name"] for e in ents if e["type"] == "Person"]
    assert persons == ["韩梅梅"]


def test_meeting_word_prefix_blocked():
    ents, _ = extract_rules("李雷 周一上午开会")
    persons = [e["name"] for e in ents if e["type"] == "Person"]
    assert persons == ["李雷"]


def test_verb_prefix_surname_not_person():
    # 「完成发版」中的「成」被「完」紧邻，不应误抽成 Person「成发版」
    ents, _ = extract_rules("完成发版")
    persons = [e["name"] for e in ents if e["type"] == "Person"]
    assert persons == []
    topics = [e["name"] for e in ents if e["type"] == "Topic"]
    assert "发版" in topics


def test_no_entity_when_empty():
    assert extract_rules("嗯好的") == ([], [])


def test_topic_keyword():
    ents, _ = extract_rules("下午做代码评审")
    topics = [e["name"] for e in ents if e["type"] == "Topic"]
    assert "代码评审" in topics
