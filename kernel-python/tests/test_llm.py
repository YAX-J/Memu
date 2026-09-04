"""LLM 调度：云端响应解析兼容性测试。"""

from extract.llm import _extract_cloud_content, parse_extraction


def test_parse_dashscope_native_format():
    data = {"output": {"choices": [{"message": {"content": "RESULT"}}]}}
    assert _extract_cloud_content(data) == "RESULT"


def test_parse_openai_compatible_format():
    data = {"choices": [{"message": {"content": "RESULT"}}]}
    assert _extract_cloud_content(data) == "RESULT"


def test_parse_empty_response():
    assert _extract_cloud_content({}) == ""


def test_parse_extraction_tolerates_fence():
    raw = '```json\n{"entities":[{"type":"Topic","name":"晨会"}]}\n```'
    entities, _ = parse_extraction(raw)
    assert entities == [{"type": "Topic", "name": "晨会"}]
